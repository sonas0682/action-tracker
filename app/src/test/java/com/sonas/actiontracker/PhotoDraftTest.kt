package com.sonas.actiontracker

import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.PhotoDraft
import com.sonas.actiontracker.sync.PhotoDraft.Found
import com.sonas.actiontracker.sync.VoiceDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Photo text to draft actions, rules only (no ML Kit models, no Gemini Nano). Today is Sunday 2026-10-18. */
class PhotoDraftTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val people = listOf("Me", "Nathiya", "Vibu", "Dharshu")

    private fun dates(text: String) = PhotoDraft.ruleDates(text, today).map { it.date }

    // ------------------------------------------------------------------ dates

    @Test fun monthNamesAndOrdinals() {
        assertEquals(listOf(LocalDate.of(2026, 10, 24)), dates("Permission slip due Oct 24"))
        assertEquals(listOf(LocalDate.of(2026, 10, 24)), dates("Return by October 24th"))
        assertEquals(listOf(LocalDate.of(2026, 10, 24)), dates("on the 24th of October"))
        assertEquals(listOf(LocalDate.of(2026, 11, 2)), dates("Picture day: Nov. 2, 2026"))
    }

    @Test fun numericAndIsoDates() {
        assertEquals(listOf(LocalDate.of(2026, 10, 30)), dates("Field trip 10/30/2026"))
        assertEquals(listOf(LocalDate.of(2026, 10, 30)), dates("Field trip 10/30"))
        assertEquals(listOf(LocalDate.of(2026, 12, 1)), dates("Closes 2026-12-01"))
    }

    @Test fun pastDateWithoutYearMeansNextYear() =
        assertEquals(listOf(LocalDate.of(2027, 9, 5)), dates("Registration opens Sept 5"))

    @Test fun wordsThatStartLikeMonthsAreNotDates() {
        assertTrue(dates("Read Mark 3 and Junior 5 notes").isEmpty())
        assertTrue(dates("Score 13/40 on the quiz").isEmpty())
    }

    @Test fun deadlineDateWinsOverEarlierEventDate() {
        val text = "Field trip Oct 30\nPlease return this slip by Oct 24"
        assertEquals(LocalDate.of(2026, 10, 24), PhotoDraft.pickDue(text, PhotoDraft.ruleDates(text, today), today)?.date)
    }

    @Test fun earliestUpcomingDateWithoutDeadlineWords() {
        val text = "Picture day Oct 28\nClass party Oct 22"
        assertEquals(LocalDate.of(2026, 10, 22), PhotoDraft.pickDue(text, PhotoDraft.ruleDates(text, today), today)?.date)
    }

    @Test fun pastDatesAreIgnored() {
        val found = listOf(Found(0, LocalDate.of(2026, 10, 1)))
        assertNull(PhotoDraft.pickDue("Due Oct 1", found, today))
    }

    // ------------------------------------------------------------------ titles

    @Test fun capsHeadingBecomesSentenceCase() = assertEquals(
        "Field trip permission slip",
        PhotoDraft.title("LINCOLN ELEMENTARY\nFIELD TRIP PERMISSION SLIP\nPlease sign below")
    )

    @Test fun firstLineOfWordsWhenNoTaskWord() =
        assertEquals("Grandma visiting next week", PhotoDraft.title("  \n12\nGrandma visiting next week.\nYay"))

    // ------------------------------------------------------------------ whole draft

    @Test fun schoolFlyerBecomesADraft() {
        val text = "Lincoln Elementary\nVibu's class field trip to the Perot Museum\nFriday, October 30\n" +
            "Please sign and return this permission slip by Oct 24 at 3 PM"
        val item = PhotoDraft.fromText(text, PhotoDraft.ruleDates(text, today), people, VoiceDraft.DEFAULT_CONTEXTS, today)
        assertEquals("Vibu's class field trip to the Perot Museum", item.title)
        assertEquals("Vibu", item.person)
        assertEquals("Kids/School", item.context)
        assertEquals(LocalDate.of(2026, 10, 24), LocalDate.ofEpochDay(item.dueDate))
        assertEquals(TimeOfDay.AFTERNOON.name, item.timeOfDay)
        assertEquals("PHOTO", item.source)
        assertTrue(item.notes.startsWith("From photo:\n"))
    }

    @Test fun modelTimeSetsTimeOfDay() {
        val text = "Dentist appointment Oct 21"
        val found = listOf(Found(text.indexOf("Oct"), LocalDate.of(2026, 10, 21), LocalTime.of(9, 30)))
        val item = PhotoDraft.fromText(text, found, people, VoiceDraft.DEFAULT_CONTEXTS, today)
        assertEquals(TimeOfDay.MORNING.name, item.timeOfDay)
        assertEquals("Family", item.context)
    }

    @Test fun noDateDefaultsToTomorrow() {
        val item = PhotoDraft.fromText("Bring snacks for the team", emptyList(), people, VoiceDraft.DEFAULT_CONTEXTS, today)
        assertEquals(today.plusDays(1), LocalDate.ofEpochDay(item.dueDate))
    }
}
