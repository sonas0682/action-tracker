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
    QUEST("Quest Path", "Tasks are steps on a path; finishing earns XP"),
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
