package com.sonas.actiontracker

import com.sonas.actiontracker.ui.DayPage
import com.sonas.actiontracker.ui.dayPageOf
import com.sonas.actiontracker.ui.donePageOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayPagesTest {
    private val today = 20_000L

    @Test fun overdueAndTodayAreOnToday() {
        assertEquals(DayPage.TODAY, dayPageOf(today - 3, today))
        assertEquals(DayPage.TODAY, dayPageOf(today, today))
    }

    @Test fun tomorrowAndLater() {
        assertEquals(DayPage.TOMORROW, dayPageOf(today + 1, today))
        assertEquals(DayPage.LATER, dayPageOf(today + 2, today))
        assertEquals(DayPage.LATER, dayPageOf(today + 60, today))
    }

    @Test fun doneTodayStaysOnTodayWhateverItsDueDate() {
        assertEquals(DayPage.TODAY, donePageOf(today - 5, today, today))
        assertEquals(DayPage.TODAY, donePageOf(today + 1, today, today))
    }

    @Test fun doneEarlierFollowsDueDateOrDropsOff() {
        assertEquals(DayPage.TOMORROW, donePageOf(today + 1, today - 1, today))
        assertEquals(DayPage.LATER, donePageOf(today + 4, null, today))
        assertNull(donePageOf(today - 2, today - 2, today))
        assertEquals(DayPage.TODAY, donePageOf(today, today - 1, today))
    }
}
