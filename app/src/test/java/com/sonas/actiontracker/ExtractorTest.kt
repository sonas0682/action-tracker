package com.sonas.actiontracker

import com.sonas.actiontracker.sync.Extractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExtractorTest {
    // A Saturday.
    private val today = LocalDate.of(2026, 10, 17)

    @Test fun skipsMailWithoutAnActionWord() {
        assertNull(Extractor.fromMail("Weekly newsletter", "news@example.com", "Top stories this week", today))
    }

    @Test fun cleansReplyPrefixAndFindsPersonContextAndDate() {
        val s = Extractor.fromMail("Re: Fwd: Vibu field trip form", "teacher@example.com", "Please sign and return by Oct 24", today)!!
        assertEquals("Vibu field trip form", s.title)
        assertEquals("Vibu", s.person)
        assertEquals("Kids/School", s.context)
        assertEquals(LocalDate.of(2026, 10, 24), s.due)
    }

    @Test fun defaultsToTomorrowAndWork() {
        val s = Extractor.fromMail("Budget review", "boss@example.com", "Can you send the numbers", today)!!
        assertEquals("Me", s.person)
        assertEquals("Work", s.context)
        assertEquals(today.plusDays(1), s.due)
    }

    @Test fun parsesDueWords() {
        assertEquals(LocalDate.of(2026, 11, 3), Extractor.parseDue("due 2026-11-03", today))
        assertEquals(LocalDate.of(2027, 1, 5), Extractor.parseDue("pay by Jan 5", today))
        assertEquals(today, Extractor.parseDue("needed by EOD", today))
        assertEquals(today.plusDays(1), Extractor.parseDue("bring it tomorrow", today))
        assertEquals(LocalDate.of(2026, 10, 23), Extractor.parseDue("submit by Friday", today))
        assertNull(Extractor.parseDue("no date here", today))
    }

    @Test fun calendarEventUsesEventDay() {
        val day = LocalDate.of(2026, 10, 30)
        assertEquals(day, Extractor.fromEvent("Dentist reminder", "", day, today)!!.due)
        assertEquals("Family", Extractor.fromEvent("Dentist reminder", "", day, today)!!.context)
    }
}
