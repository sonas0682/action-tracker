package com.sonas.actiontracker

import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.sync.VoiceDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private val fri = LocalDate.of(2026, 10, 16)

    private fun Recurrence.first(start: LocalDate, n: Int) = dates(start).take(n).toList()

    @Test fun noneIsJustTheStart() = assertEquals(listOf(fri), Recurrence.NONE.first(fri, 5))

    @Test fun daily() = assertEquals(listOf(fri, fri.plusDays(1), fri.plusDays(2)), Recurrence.DAILY.first(fri, 3))

    @Test fun weekdaysSkipTheWeekend() =
        assertEquals(listOf(fri, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20)), Recurrence.WEEKDAYS.first(fri, 3))

    @Test fun weeklyKeepsTheWeekday() =
        assertEquals(listOf(fri, fri.plusWeeks(1), fri.plusWeeks(2)), Recurrence.WEEKLY.first(fri, 3))

    @Test fun monthlyKeepsTheDayWithoutDrift() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(
            listOf(jan31, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            Recurrence.MONTHLY.first(jan31, 4)
        )
    }

    @Test fun yearlyFromALeapDay() {
        val leap = LocalDate.of(2028, 2, 29)
        assertEquals(listOf(leap, LocalDate.of(2029, 2, 28), LocalDate.of(2032, 2, 29)), Recurrence.YEARLY.dates(leap).filterIndexed { i, _ -> i in setOf(0, 1, 4) }.take(3).toList())
    }

    @Test fun datesBetweenIsAfterAndUpToInclusive() =
        assertEquals(
            listOf(fri.plusWeeks(1), fri.plusWeeks(2)),
            Recurrence.WEEKLY.datesBetween(fri, after = fri, until = fri.plusWeeks(2))
        )

    @Test fun nthDateCountsTheFirst() {
        assertEquals(fri, Recurrence.WEEKLY.nthDate(fri, 1))
        assertEquals(fri.plusWeeks(9), Recurrence.WEEKLY.nthDate(fri, 10))
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
