package com.sonas.actiontracker

import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.sync.VoiceDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private val fri = LocalDate.of(2026, 10, 16)
    private val sat = LocalDate.of(2026, 10, 17)

    @Test fun noneHasNoNextDate() = assertNull(Recurrence.NONE.nextDue(fri, fri))

    @Test fun dailyOnTimeAndOverdue() {
        assertEquals(sat, Recurrence.DAILY.nextDue(fri, fri))
        // Ten days late: next is tomorrow, not a backlog of past days.
        assertEquals(sat.plusDays(1), Recurrence.DAILY.nextDue(fri.minusDays(10), sat))
        // Done early: the day after its own due date.
        assertEquals(fri.plusDays(4), Recurrence.DAILY.nextDue(fri.plusDays(3), fri))
    }

    @Test fun weekdaysSkipTheWeekend() {
        assertEquals(LocalDate.of(2026, 10, 19), Recurrence.WEEKDAYS.nextDue(fri, fri))
        assertEquals(LocalDate.of(2026, 10, 19), Recurrence.WEEKDAYS.nextDue(fri, sat))
    }

    @Test fun weeklyKeepsTheWeekday() {
        assertEquals(fri.plusWeeks(1), Recurrence.WEEKLY.nextDue(fri, fri))
        assertEquals(fri.plusWeeks(1), Recurrence.WEEKLY.nextDue(fri, sat))
        assertEquals(fri.plusWeeks(2), Recurrence.WEEKLY.nextDue(fri.minusWeeks(2), fri.plusDays(10)))
    }

    @Test fun monthlyKeepsTheDayWithoutDrift() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.MONTHLY.nextDue(jan31, jan31))
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.MONTHLY.nextDue(jan31, LocalDate.of(2026, 3, 1)))
    }

    @Test fun yearly() {
        val leap = LocalDate.of(2028, 2, 29)
        assertEquals(LocalDate.of(2029, 2, 28), Recurrence.YEARLY.nextDue(leap, leap))
    }

    @Test fun voicePhrases() {
        assertEquals(Recurrence.WEEKLY, VoiceDraft.recurrence("take out the trash every Monday"))
        assertEquals(Recurrence.DAILY, VoiceDraft.recurrence("water the plants every day"))
        assertEquals(Recurrence.DAILY, VoiceDraft.recurrence("daily standup notes"))
        assertEquals(Recurrence.WEEKDAYS, VoiceDraft.recurrence("pack Vibu's lunch every weekday"))
        assertEquals(Recurrence.MONTHLY, VoiceDraft.recurrence("pay rent every month"))
        assertEquals(Recurrence.YEARLY, VoiceDraft.recurrence("renew car registration every year"))
        assertEquals(Recurrence.NONE, VoiceDraft.recurrence("call mom on Monday"))
        assertEquals("Take out the trash", VoiceDraft.title("remind me to take out the trash every Monday"))
    }
}
