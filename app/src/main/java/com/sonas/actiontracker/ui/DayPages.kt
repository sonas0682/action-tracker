package com.sonas.actiontracker.ui

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import java.time.Instant
import java.time.ZoneId

/** The three day pages before Suggested in the dock. Overdue work sits on Today so nothing late is hidden. */
enum class DayPage(val label: String) { TODAY("Today"), TOMORROW("Tomorrow"), LATER("Later") }

/** Which page an action due on [dueDay] belongs to, given [today] (both epoch days). */
fun dayPageOf(dueDay: Long, today: Long): DayPage = when {
    dueDay <= today -> DayPage.TODAY
    dueDay == today + 1 -> DayPage.TOMORROW
    else -> DayPage.LATER
}

/**
 * The page a done action shows on: one finished today stays on Today; otherwise it follows its
 * due date, and older finished work (due and completed before today) drops off the pages.
 */
fun donePageOf(dueDay: Long, completedDay: Long?, today: Long): DayPage? = when {
    completedDay == today -> DayPage.TODAY
    dueDay < today -> null
    else -> dayPageOf(dueDay, today)
}

/** The actions on [page]: open ones by due date, done ones by [donePageOf]. */
fun List<Item>.onPage(page: DayPage, today: Long, zone: ZoneId = ZoneId.systemDefault()): List<Item> = filter {
    if (it.status == STATUS_DONE) {
        val completedDay = it.completedAt?.let { ms -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay() }
        donePageOf(it.dueDate, completedDay, today) == page
    } else {
        dayPageOf(it.dueDate, today) == page
    }
}
