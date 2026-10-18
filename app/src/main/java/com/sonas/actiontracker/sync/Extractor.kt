package com.sonas.actiontracker.sync

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class Suggestion(
    val title: String,
    val notes: String,
    val person: String,
    val context: String,
    val due: LocalDate
)

/**
 * Rule-based extraction (v1). An on-device Gemini Nano pass for ambiguous
 * messages can plug in later behind [fromMail] / [fromEvent].
 */
object Extractor {
    private val trigger = Regex(
        """(?i)\b(please|kindly|action required|action needed|need(s)? you|can you|could you|""" +
            """reminder|due|deadline|follow[- ]?up|rsvp|sign|submit|pay|payment|register|renew|""" +
            """respond|reply|confirm|complete|permission slip|forms?|prepare|bring|send)\b"""
    )

    fun fromMail(subject: String, from: String, snippet: String, today: LocalDate): Suggestion? {
        val text = "$subject. $snippet"
        if (!trigger.containsMatchIn(text)) return null
        return build(subject, "From: $from\n$snippet", text, today)
    }

    fun fromEvent(summary: String, description: String, date: LocalDate, today: LocalDate): Suggestion? {
        val text = "$summary. $description"
        if (!trigger.containsMatchIn(text)) return null
        // The event day is the natural due date for a calendar-sourced item.
        return build(summary, description, text, today).copy(due = date)
    }

    private fun build(title: String, notes: String, text: String, today: LocalDate) = Suggestion(
        title = title.replace(replyPrefix, "").trim().take(120).ifEmpty { "(no subject)" },
        notes = notes.take(300),
        person = person(text),
        context = context(text),
        due = parseDue(text, today) ?: today.plusDays(1) // default: one day
    )

    // Compiled once: sync runs these over every new email and event.
    private val replyPrefix = Regex("""(?i)^\s*((re|fwd?):\s*)+""")
    private val nathiya = Regex("(?i)nathiya")
    private val vibu = Regex("(?i)vibu")
    private val dharshu = Regex("(?i)dharshu|dharsini|dharsu")
    private val schoolWords = Regex("(?i)school|teacher|class|homework|pta|isd|field trip|permission slip|principal")
    private val familyWords = Regex("(?i)family|birthday|doctor|dentist|dinner|vacation|home")
    private val isoDate = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val monthDay = Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{1,2})\b""")
    private val todayWords = Regex("""\b(today|tonight|eod)\b""")
    private val tomorrowWord = Regex("""\btomorrow\b""")
    private val weekday = Regex("""\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""")

    internal fun person(text: String): String = when {
        nathiya.containsMatchIn(text) -> "Nathiya"
        vibu.containsMatchIn(text) -> "Vibu"
        dharshu.containsMatchIn(text) -> "Dharshu"
        else -> "Me"
    }

    internal fun context(text: String): String = when {
        schoolWords.containsMatchIn(text) -> "Kids/School"
        familyWords.containsMatchIn(text) -> "Family"
        else -> "Work"
    }

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Finds a due date in free text, or null when the source gives none. */
    fun parseDue(text: String, today: LocalDate): LocalDate? = runCatching {
        isoDate.find(text)?.let {
            val d = LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
            if (!d.isBefore(today)) return@runCatching d
        }
        monthDay.find(text)?.let {
            val month = months.indexOf(it.groupValues[1].lowercase()) + 1
            var d = LocalDate.of(today.year, month, it.groupValues[2].toInt())
            if (d.isBefore(today)) d = d.plusYears(1)
            return@runCatching d
        }
        val lower = text.lowercase()
        if (todayWords.containsMatchIn(lower)) return@runCatching today
        if (tomorrowWord.containsMatchIn(lower)) return@runCatching today.plusDays(1)
        weekday.find(lower)?.let {
            val dow = DayOfWeek.valueOf(it.groupValues[1].uppercase())
            return@runCatching today.with(TemporalAdjusters.next(dow))
        }
        null
    }.getOrNull()
}
