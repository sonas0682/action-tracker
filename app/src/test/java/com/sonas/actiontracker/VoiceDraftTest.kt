package com.sonas.actiontracker

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.STATUS_SUGGESTED
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.VoiceDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Spoken sentences to draft actions, rules only (no Gemini Nano). Today is Sunday 2026-10-18. */
class VoiceDraftTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val people = listOf("Raj", "Me", "Nathiya", "Vibu", "Dharshu")
    private val contexts = VoiceDraft.DEFAULT_CONTEXTS
    private val usual = mapOf("raj" to "Work")

    private fun draft(text: String) = VoiceDraft.fromRules(text, people, contexts, today, usual)

    private fun check(
        text: String, title: String, person: String, context: String,
        due: LocalDate = today.plusDays(1), time: TimeOfDay? = null, repeat: Recurrence = Recurrence.NONE
    ) {
        val d = draft(text)
        assertEquals("title of \"$text\"", title, d.title)
        assertEquals("person of \"$text\"", person, d.person)
        assertEquals("context of \"$text\"", context, d.context)
        assertEquals("due of \"$text\"", due, LocalDate.ofEpochDay(d.dueDate))
        if (time != null) assertEquals("time of \"$text\"", time.name, d.timeOfDay)
        assertEquals("repeat of \"$text\"", repeat.name, d.recurrence)
    }

    // ------------------------------------------------------------------ titles

    @Test fun forPersonMovesToTheTag() =
        check("Remind me to buy a birthday gift for Nathiya by Friday", "Buy a birthday gift", "Nathiya", "Family", LocalDate.of(2026, 10, 23))

    @Test fun stackedLeadInsAndFillerAreDropped() =
        check("okay so I need to renew my passport, um, before December 10th", "Renew my passport", "Me", "Family", LocalDate.of(2026, 12, 10))

    @Test fun canYouRemindMe() =
        check("Can you remind me to pay the electricity bill tomorrow morning", "Pay the electricity bill", "Me", "Family", time = TimeOfDay.MORNING)

    @Test fun setAReminder() =
        check("Set a reminder to send the slides to Raj on Monday at 3 pm", "Send the slides to Raj", "Raj", "Work", LocalDate.of(2026, 10, 19), TimeOfDay.AFTERNOON)

    @Test fun rememberToAndThisEvening() =
        check("Remember to call Dharshu's piano teacher this evening", "Call Dharshu's piano teacher", "Dharshu", "Kids/School", today, TimeOfDay.EVENING)

    @Test fun addATaskForSomeone() =
        check("Add a task for Vibu to clean his room tomorrow please", "Clean his room", "Vibu", "Family")

    @Test fun leadInAfterTheDate() =
        check("Remind me in 2 hours to check the oven", "Check the oven", "Me", "Work", today)

    @Test fun repeatWithTimeOfDay() =
        check("Take Dharshu to soccer practice every Saturday morning", "Take Dharshu to soccer practice", "Dharshu", "Kids/School",
            LocalDate.of(2026, 10, 24), TimeOfDay.MORNING, Recurrence.WEEKLY)

    @Test fun plainSentenceIsKept() = check("Buy milk", "Buy milk", "Me", "Family")

    // ------------------------------------------------------------------ person

    @Test fun whoTheTaskIsForBeatsTheLongestName() {
        assertEquals("Nathiya", draft("Ask Nathiya to call Vibu's teacher tomorrow").person)
        assertEquals("Vibu", draft("Pick up Vibu from Nathiya's mom's house tomorrow evening").person)
    }

    @Test fun misheardNamesMatchAndAreSpelledAsSaved() {
        check("Um sign Vibhu's field trip form tonight", "Sign Vibu's field trip form", "Vibu", "Kids/School", today, TimeOfDay.EVENING)
        assertEquals("Dharshu", draft("Book a dentist appointment for Dharsini next week").person)
        assertEquals("Book a dentist appointment", draft("I want to book a dentist appointment for Dharsini next week").title)
        assertEquals("Nathiya", draft("Text Nathia when I land").person)
    }

    @Test fun nearSpellingOfASavedName() =
        assertEquals("Priyanka", VoiceDraft.person("Call Priyanca about the venue", listOf("Me", "Priyanka")))

    @Test fun lowercaseNameIsCapitalized() =
        assertEquals("Tell Vibu to finish homework", draft("tell vibu to finish homework by 8 pm").title)

    @Test fun nobodyNamedIsMe() = assertEquals("Me", draft("Renew car registration on the 25th").person)

    // ------------------------------------------------------------------ context

    @Test fun usualContextOfThePerson() = assertEquals("Work", draft("Call Raj").context)

    @Test fun familyMemberWithoutKeywordsIsFamily() = assertEquals("Family", draft("Dharshu needs new shoes").context)

    @Test fun workOnIsNotTheWorkContext() =
        assertEquals("Kids/School", draft("Help Vibu work on his science project this weekend").context)

    @Test fun usualContextsSkipSuggestionsAndMe() {
        fun item(person: String, context: String, status: String = STATUS_OPEN) =
            Item(title = "t", person = person, context = context, dueDate = 0, status = status, source = "CUSTOM")
        val usual = VoiceDraft.usualContexts(
            listOf(item("Raj", "Work"), item("Raj", "Work"), item("Raj", "Family"), item("Me", "Family"), item("Vibu", "Work", STATUS_SUGGESTED))
        )
        assertEquals(mapOf("raj" to "Work"), usual)
    }

    // ------------------------------------------------------------------ time of day

    @Test fun clockTimesMapToTimeOfDay() {
        assertEquals(TimeOfDay.MORNING, VoiceDraft.timeOfDay("standup at 9:30 am"))
        assertEquals(TimeOfDay.AFTERNOON, VoiceDraft.timeOfDay("dentist on Tuesday at 4"))
        assertEquals(TimeOfDay.EVENING, VoiceDraft.timeOfDay("dance class at 6"))
        assertEquals(TimeOfDay.NIGHT, VoiceDraft.timeOfDay("take the trash out at 10 pm"))
        assertEquals(TimeOfDay.AFTERNOON, VoiceDraft.timeOfDay("pick up the order after school"))
        assertEquals(null, VoiceDraft.timeOfDay("pay rent on the 1st"))
    }

    @Test fun noTimeSpokenKeepsTheContextDefault() =
        assertEquals(TimeOfDay.AFTERNOON.name, draft("Prepare slides for the client meeting").timeOfDay)
}
