package com.sonas.actiontracker.ui

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.SOURCE_GOD
import java.time.Instant
import java.time.ZoneId

/**
 * How the Now / Near / Next pages are drawn (⋮ → Settings → Look → Layout). Only the page body
 * changes: the header, dock, edit sheet, Suggested, Calendar, Stats and the widget are shared.
 */
enum class AppLayout(val label: String, val blurb: String) {
    CLASSIC("Classic", "Cards, grouped the way you choose"),
    LEDGER("Ledger", "A dense table with the day's numbers on top"),
    QUEST("Quest Path", "Steps on a path; finishing earns XP, unticking returns it"),
    DIAL("Day Dial", "A ring that fills from Morning to Night"),
    JOURNAL("Journal Page", "A ruled page that opens with the day's thought"),
    METRO("Metro Line", "The day as a train line; tasks are stops");

    companion object {
        fun of(name: String?): AppLayout = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

/** One block of a layout page: a time of day (Now, Near), a date (Next) or the late ones. */
data class LaySection(val key: String, val label: String, val slot: TimeOfDay?, val late: Boolean, val items: List<Item>)

/** How many days ahead repeats show on a page; later ones fold under "Upcoming" (same as Classic). */
const val LAYOUT_UPCOMING_AFTER_DAYS = 7

/** Splits a page's actions into far-off repeats (first) and everything a layout shows (second). */
fun splitUpcoming(items: List<Item>, today: Long): Pair<List<Item>, List<Item>> = items.partition {
    it.status == STATUS_OPEN && it.seriesId != null && it.dueDate > today + LAYOUT_UPCOMING_AFTER_DAYS
}

fun Item.isLate(today: Long): Boolean = status == STATUS_OPEN && dueDate < today

/**
 * A page's actions in reading order: Late first, then Morning → Night on Now and Near, or one
 * block per date on Next. Inside a block open actions come before done ones.
 */
fun layoutSections(items: List<Item>, page: DayPage, today: Long, dateLabel: (Long) -> String): List<LaySection> {
    val (late, rest) = items.partition { it.isLate(today) }
    val byTime = compareBy<Item>({ it.status == STATUS_DONE }, { TimeOfDay.of(it.timeOfDay).ordinal })
    val out = mutableListOf<LaySection>()
    if (late.isNotEmpty()) out += LaySection("late", "Late", null, true, late.sortedBy { it.dueDate })
    if (page == DayPage.LATER) {
        rest.groupBy { it.dueDate }.toSortedMap().forEach { (day, list) ->
            out += LaySection("d-$day", dateLabel(day), null, false, list.sortedWith(byTime))
        }
    } else {
        rest.groupBy { TimeOfDay.of(it.timeOfDay) }.toSortedMap().forEach { (slot, list) ->
            out += LaySection("t-${slot.name}", slot.label, slot, false, list.sortedBy { it.status == STATUS_DONE })
        }
    }
    return out
}

/**
 * Ledger's blocks, regrouped by the dock's group-by chip: Time and Date as on the other layouts,
 * Person and Context alphabetically. Late gets its own block only when grouped by time.
 */
fun ledgerSections(items: List<Item>, group: Group, page: DayPage, today: Long, dateLabel: (Long) -> String): List<LaySection> {
    val byTime = compareBy<Item>({ it.status == STATUS_DONE }, { it.dueDate }, { TimeOfDay.of(it.timeOfDay).ordinal })
    return when (group) {
        Group.TIME -> layoutSections(items, if (page == DayPage.LATER) DayPage.TOMORROW else page, today, dateLabel)
        Group.DATE -> items.groupBy { it.dueDate }.toSortedMap().map { (day, list) ->
            LaySection("d-$day", dateLabel(day), null, day < today && list.any { it.isLate(today) }, list.sortedWith(byTime))
        }
        Group.PERSON -> items.groupBy { it.person }.toSortedMap().map { (person, list) ->
            LaySection("p-$person", person, null, false, list.sortedWith(byTime))
        }
        Group.CONTEXT -> items.groupBy { it.context }.toSortedMap().map { (context, list) ->
            LaySection("c-$context", context, null, false, list.sortedWith(byTime))
        }
    }
}

/** What Ledger's top strip can narrow the table to. */
enum class LedgerFilter { OPEN, LATE, DONE }

fun List<Item>.ledgerFilter(filter: LedgerFilter?, today: Long): List<Item> = when (filter) {
    null -> this
    LedgerFilter.OPEN -> filter { it.status == STATUS_OPEN }
    LedgerFilter.LATE -> filter { it.isLate(today) }
    LedgerFilter.DONE -> filter { it.status == STATUS_DONE }
}

/**
 * Where the clock sits on the Day Dial, in degrees clockwise from the top. Each time of day is a
 * quarter turn: Morning 6-12, Afternoon 12-5, Evening 5-9, Night 9-midnight; before 6 AM is the start.
 */
fun dialAngle(hour: Int, minute: Int): Float {
    val t = hour + minute / 60f
    return when {
        t < 6f -> 0f
        t < 12f -> (t - 6f) / 6f * 90f
        t < 17f -> 90f + (t - 12f) / 5f * 90f
        t < 21f -> 180f + (t - 17f) / 4f * 90f
        else -> 270f + (t - 21f) / 3f * 90f
    }
}

/** A page with something on it and nothing left open. */
fun allDone(items: List<Item>): Boolean = items.isNotEmpty() && items.all { it.status == STATUS_DONE }

private val FINALE_LINES = listOf(
    "A finished day is a quiet kind of wealth.",
    "What you kept today, tomorrow can build on.",
    "Small promises kept are how trust in yourself grows.",
    "Rest is part of the work. You have earned it.",
    "The list is empty; let the mind be too.",
    "Done with care is done for good.",
    "Today asked, and you answered."
)

/** The closing thought under a finished page; changes with the day, the same all day. */
fun finaleLine(today: Long): String = FINALE_LINES[Math.floorMod(today, FINALE_LINES.size.toLong()).toInt()]

/** What each layout calls a finished page. */
fun finaleTitle(layout: AppLayout): String = when (layout) {
    AppLayout.CLASSIC -> "All done"
    AppLayout.LEDGER -> "Books closed"
    AppLayout.QUEST -> "Quest complete"
    AppLayout.DIAL -> "Ring closed"
    AppLayout.JOURNAL -> "Page finished"
    AppLayout.METRO -> "End of the line"
}

/** The Layout row's summary in Settings: one name when every page matches, else page by page. */
fun layoutSummary(now: AppLayout, near: AppLayout, next: AppLayout): String =
    if (now == near && near == next) "${now.label} on every page"
    else "Now ${now.label} · Near ${near.label} · Next ${next.label}"

/** The time of day a clock hour (0-23) falls in, matching the 12 / 4 / 7 / 10 PM reminders. */
fun slotAt(hour: Int): TimeOfDay = when {
    hour < 12 -> TimeOfDay.MORNING
    hour < 17 -> TimeOfDay.AFTERNOON
    hour < 21 -> TimeOfDay.EVENING
    else -> TimeOfDay.NIGHT
}

/** "2 days late" for an open action due before [today]. */
fun lateText(dueDay: Long, today: Long): String {
    val days = today - dueDay
    return if (days == 1L) "1 day late" else "$days days late"
}

/** Ledger column codes: "Me" → "ME", "Nathiya" → "NAT"; "Kids/School" → "KIDS/SCH". */
fun personCode(person: String): String = person.trim().take(3).uppercase().ifEmpty { "-" }
fun contextCode(context: String): String = context.trim().take(8).uppercase().ifEmpty { "-" }

/**
 * Quest Path scoring, worked out from the actions already stored (nothing new is saved): every
 * finished action is 20 XP, a God task 50. Levels get longer as they go; the streak counts days
 * in a row with at least one action finished, and today only breaks it once the day is over.
 */
object Quest {
    const val TASK_XP = 20
    const val GOD_XP = 50
    private val titles = listOf(
        "First Step", "Early Riser", "Steady Hand", "Path Finder", "Promise Keeper",
        "Calm Finisher", "Trail Maker", "Quiet Master", "Mountain Mover"
    )

    fun xpFor(item: Item): Int = if (item.source == SOURCE_GOD) GOD_XP else TASK_XP

    fun totalXp(items: List<Item>): Int = items.filter { it.status == STATUS_DONE }.sumOf { xpFor(it) }

    /** XP earned by actions finished on [day]. */
    fun xpOn(items: List<Item>, day: Long, zone: ZoneId = ZoneId.systemDefault()): Int = items.filter { item ->
        item.status == STATUS_DONE && item.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() } == day
    }.sumOf { xpFor(it) }

    /** XP needed to stand at [level]: 0, 100, 300, 600, 1000 … */
    fun xpToReach(level: Int): Int = 50 * level * (level - 1)

    fun levelOf(xp: Int): Int {
        var level = 1
        while (xpToReach(level + 1) <= xp) level++
        return level
    }

    fun title(level: Int): String = titles[(level - 1).coerceIn(0, titles.lastIndex)]

    /** Epoch days on which something was finished. */
    fun doneDays(items: List<Item>, zone: ZoneId = ZoneId.systemDefault()): Set<Long> = items.mapNotNullTo(HashSet()) { item ->
        item.completedAt?.takeIf { item.status == STATUS_DONE }
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
    }

    fun streak(doneDays: Set<Long>, today: Long): Int {
        var day = if (today in doneDays) today else today - 1
        var count = 0
        while (day in doneDays) { count++; day-- }
        return count
    }
}
