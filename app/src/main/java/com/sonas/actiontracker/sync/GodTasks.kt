package com.sonas.actiontracker.sync

import android.content.Context
import androidx.room.withTransaction
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.GodTheme
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

const val SOURCE_GOD = "GOD"

/** The oil lamp that marks God tasks in the list, widget and notifications. */
const val GOD_LAMP = "\uD83E\uDE94"

/**
 * God tasks: short activities added to today's Evening every morning from [START_TIME], one each
 * weekday and three on Saturday and Sunday unless ⋮ → God task sets other counts (0 to [MAX_COUNT]).
 * The theme comes from the same dialog (Random by default, weekends included). An unfinished God
 * task isn't left overdue: it moves to today and counts toward today's number, so new ones only
 * fill the gap. On a day whose count is 0, unfinished God tasks are removed instead, so none show.
 *
 * Tasks are picked from [GodTheme.tasks] (Gemini Nano can't write them: the Prompt API doesn't
 * support the S25 Ultra and Nano can't run in the background). A task isn't picked again for
 * [NO_REPEAT_DAYS] days unless its theme has nothing fresher; Family tasks rotate through the family.
 * Long-pressing an open God task offers "Another God task": a new one from the same theme ([replace]).
 */
object GodTasks {
    private const val PREFS = "god_tasks"
    const val RANDOM = "RANDOM"
    const val OFF = "OFF"
    val START_TIME: LocalTime = LocalTime.of(6, 0)
    const val NO_REPEAT_DAYS = 60L
    /** History older than this is dropped; it no longer changes any pick. */
    private const val KEEP_DAYS = 365L
    val FAMILY = listOf("Nathiya", "Vibu", "Dharshu")
    const val WEEKDAY_DEFAULT = 1
    const val WEEKEND_DEFAULT = 3
    /** Random gives each of a day's tasks its own theme, so there are at most as many as themes. */
    val MAX_COUNT = GodTheme.entries.size

    private val lock = Mutex()

    /** [RANDOM], [OFF] or a [GodTheme] name. */
    fun choice(context: Context): String =
        prefs(context).getString("theme", RANDOM)?.takeIf { it == RANDOM || it == OFF || GodTheme.of(it) != null } ?: RANDOM

    fun setChoice(context: Context, choice: String) {
        prefs(context).edit().putString("theme", choice).apply()
        // The 15-minute reminder check is what adds them in the morning.
        Reminders.schedule(context)
    }

    fun enabled(context: Context): Boolean = choice(context) != OFF

    fun weekdayCount(context: Context): Int =
        prefs(context).getInt("weekday_count", WEEKDAY_DEFAULT).coerceIn(0, MAX_COUNT)

    fun weekendCount(context: Context): Int =
        prefs(context).getInt("weekend_count", WEEKEND_DEFAULT).coerceIn(0, MAX_COUNT)

    /** Saves the ⋮ → God task counts; the caller runs [ensureToday] with force so today follows at once. */
    fun setCounts(context: Context, weekday: Int, weekend: Int) {
        prefs(context).edit()
            .putInt("weekday_count", weekday.coerceIn(0, MAX_COUNT))
            .putInt("weekend_count", weekend.coerceIn(0, MAX_COUNT))
            .apply()
    }

    fun choiceLabel(choice: String): String = when (choice) {
        RANDOM -> "Random"
        OFF -> "Off"
        else -> GodTheme.of(choice)?.label ?: "Random"
    }

    fun isGod(item: Item): Boolean = item.source == SOURCE_GOD

    /**
     * Once a day, at or after [START_TIME]: moves unfinished God tasks to today and adds new ones
     * up to today's count; when today's count is 0 it removes unfinished ones instead. Returns true
     * when it changed anything. Safe to call often; [force] runs it again today after a count change.
     */
    suspend fun ensureToday(
        context: Context, now: LocalDateTime = LocalDateTime.now(), force: Boolean = false
    ): Boolean = lock.withLock {
        if (!enabled(context) || now.toLocalTime() < START_TIME) return@withLock false
        val today = now.toLocalDate()
        val t = today.toEpochDay()
        val prefs = prefs(context)
        if (!force && prefs.getLong("last_day", Long.MIN_VALUE) == t) return@withLock false
        val count = countFor(today, weekdayCount(context), weekendCount(context))

        val history = parseHistory(prefs.getStringSet("history", emptySet()).orEmpty())
        val familyNext = prefs.getInt("family_next", 0)
        val db = Db.get(context)
        val (carried, picks) = db.withTransaction {
            val dao = db.dao()
            if (count == 0) {
                // A day set to 0 shows no God tasks: unfinished ones (carried or added earlier today) go.
                val unfinished = dao.openGodBefore(t + 1)
                unfinished.forEach { dao.delete(it) }
                return@withTransaction unfinished.size to emptyList<Pick>()
            }
            val late = dao.openGodBefore(t)
            late.forEach { dao.update(it.copy(dueDate = t)) }
            val picks = plan(today, choice(context), dao.godCountOn(t), history, familyNext, Random.Default, count)
            picks.forEach { dao.insert(it.toItem(t)) }
            late.size to picks
        }
        val newHistory = history.toMutableMap()
        picks.forEach { newHistory[it.key] = t }
        prefs.edit()
            .putLong("last_day", t)
            .putStringSet("history", formatHistory(newHistory.filterValues { t - it < KEEP_DAYS }))
            .putInt("family_next", familyNext + picks.count { it.theme == GodTheme.FAMILY })
            .apply()
        carried > 0 || picks.isNotEmpty()
    }

    /**
     * Swaps open God task [item] for another from the same theme (same person for Family), in
     * place, and records it in the history. Returns the new row, or null if [item] can't be swapped.
     */
    suspend fun replace(context: Context, item: Item, today: LocalDate = LocalDate.now()): Item? = lock.withLock {
        val prefs = prefs(context)
        val history = parseHistory(prefs.getStringSet("history", emptySet()).orEmpty())
        val pick = replacement(item, history, today.toEpochDay(), Random.Default) ?: return@withLock null
        val db = Db.get(context)
        val updated = db.withTransaction {
            val dao = db.dao()
            val current = dao.byId(item.id)?.takeIf { isGod(it) && it.status == STATUS_OPEN } ?: return@withTransaction null
            current.copy(title = pick.title).also { dao.update(it) }
        } ?: return@withLock null
        val t = today.toEpochDay()
        prefs.edit()
            .putStringSet("history", formatHistory((history + (pick.key to t)).filterValues { t - it < KEEP_DAYS }))
            .apply()
        updated
    }

    // ------------------------------------------------------------------ rules (pure, unit-tested)

    /** A different task from [item]'s theme, keeping its person; the current one counts as just used. */
    fun replacement(item: Item, history: Map<String, Long>, today: Long, random: Random): Pick? {
        val theme = GodTheme.entries.firstOrNull { it.label == item.context } ?: return null
        val person = if (theme == GodTheme.FAMILY) item.person else "Me"
        val used = history.toMutableMap()
        theme.tasks.indices.filter { theme.tasks[it].replace("{name}", person) == item.title }
            .forEach { used[key(theme, it)] = today }
        val index = pickIndex(theme, used, today, random)
        val title = theme.tasks[index].replace("{name}", person)
        return if (title == item.title) null else Pick(theme, index, title, person)
    }

    data class Pick(val theme: GodTheme, val index: Int, val title: String, val person: String) {
        val key: String get() = key(theme, index)

        fun toItem(epochDay: Long) = Item(
            title = title, person = person, context = theme.label, dueDate = epochDay,
            status = STATUS_OPEN, source = SOURCE_GOD, timeOfDay = TimeOfDay.EVENING.name
        )
    }

    fun key(theme: GodTheme, index: Int) = "${theme.name}:$index"

    /** [weekday] on Monday to Friday, [weekend] on Saturday and Sunday (1 and 3 unless changed). */
    fun countFor(day: LocalDate, weekday: Int = WEEKDAY_DEFAULT, weekend: Int = WEEKEND_DEFAULT): Int =
        if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) weekend else weekday

    /**
     * The new God tasks for [day] when [already] are on it (carried over or added earlier), up to
     * [count]. [history] maps [key] to the epoch day it was last picked; [familyNext] is the rotation position.
     */
    fun plan(
        day: LocalDate, choice: String, already: Int, history: Map<String, Long>, familyNext: Int, random: Random,
        count: Int = countFor(day)
    ): List<Pick> {
        val n = count.coerceAtMost(MAX_COUNT) - already
        if (n <= 0 || choice == OFF) return emptyList()
        val fixed = GodTheme.of(choice)
        // Random spreads a weekend's three over different themes.
        val themes = if (fixed != null) List(n) { fixed } else GodTheme.entries.shuffled(random).take(n)
        val used = history.toMutableMap()
        var family = familyNext
        return themes.map { theme ->
            val index = pickIndex(theme, used, day.toEpochDay(), random)
            used[key(theme, index)] = day.toEpochDay()
            val person = if (theme == GodTheme.FAMILY) FAMILY[Math.floorMod(family++, FAMILY.size)] else "Me"
            Pick(theme, index, theme.tasks[index].replace("{name}", person), person)
        }
    }

    /** A random task not picked in the last [NO_REPEAT_DAYS] days, or else the one picked longest ago. */
    fun pickIndex(theme: GodTheme, used: Map<String, Long>, today: Long, random: Random): Int {
        val all = theme.tasks.indices
        val fresh = all.filter { i -> used[key(theme, i)]?.let { today - it >= NO_REPEAT_DAYS } ?: true }
        return if (fresh.isNotEmpty()) fresh.random(random) else all.minBy { used[key(theme, it)] ?: Long.MIN_VALUE }
    }

    fun parseHistory(entries: Set<String>): Map<String, Long> = entries.mapNotNull { e ->
        val (k, d) = e.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        d.toLongOrNull()?.let { k to it }
    }.toMap()

    fun formatHistory(history: Map<String, Long>): Set<String> = history.map { (k, d) -> "$k=$d" }.toSet()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
