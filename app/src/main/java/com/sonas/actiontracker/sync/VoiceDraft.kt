package com.sonas.actiontracker.sync

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.data.STATUS_OPEN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Turns a spoken sentence ("remind me to sign Vibu's permission slip by Friday")
 * into a draft action. Due date, person and context come from rules, matched
 * against the people and contexts already used in the app. If on-device Gemini
 * Nano proofreading is available it then tidies the title (punctuation, misheard
 * words). Nothing leaves the phone.
 */
object VoiceDraft {
    data class Draft(val item: Item, val transcript: String, val tidiedByNano: Boolean)

    private const val NANO_TIMEOUT_SECONDS = 8L
    private const val MAX_TITLE_CHARS = 120

    /** People and contexts the app offers before any have been typed. */
    val DEFAULT_PEOPLE = listOf("Me", "Nathiya", "Vibu", "Dharshu")
    val DEFAULT_CONTEXTS = listOf("Work", "Family", "Kids/School")

    /** Every person/context used so far, most recent first, then the built-in defaults; deduped ignoring case. */
    fun recentValues(items: List<Item>, defaults: List<String>, pick: (Item) -> String): List<String> =
        (items.sortedByDescending { it.createdAt }.map { pick(it).trim() } + defaults)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    suspend fun build(
        context: Context,
        transcript: String,
        people: List<String>,
        contexts: List<String>,
        today: LocalDate = LocalDate.now()
    ): Draft {
        val rules = fromRules(transcript, people, contexts, today)
        val nanoTitle = tidy(context, rules.title)
        val item = if (nanoTitle != null) rules.copy(title = nanoTitle.take(MAX_TITLE_CHARS)) else rules
        return Draft(item, transcript.trim(), nanoTitle != null)
    }

    /**
     * The draft from the rules alone, without Gemini Nano. Used as-is where Nano can't run,
     * such as a reply typed or dictated into a notification (for example on a watch).
     */
    fun fromRules(transcript: String, people: List<String>, contexts: List<String>, today: LocalDate): Item {
        val text = transcript.trim()
        return Item(
            title = title(text).take(MAX_TITLE_CHARS),
            notes = "Voice: $text",
            person = person(text, people),
            context = context(text, contexts),
            dueDate = (due(text, today) ?: today.plusDays(1)).toEpochDay(), // default: tomorrow
            status = STATUS_OPEN,
            source = "VOICE",
            recurrence = recurrence(text).name
        )
    }

    // ------------------------------------------------------------------ due date

    private val numbers = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    /** Spoken date phrases first, then the same parser the Gmail rules use. */
    fun due(text: String, today: LocalDate): LocalDate? {
        val lower = text.lowercase()
        if ("day after tomorrow" in lower) return today.plusDays(2)
        Regex("""\bin (\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten) (days?|weeks?)\b""").find(lower)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: numbers.getValue(it.groupValues[1])
            return if (it.groupValues[2].startsWith("week")) today.plusWeeks(n.toLong()) else today.plusDays(n.toLong())
        }
        if (Regex("""\bnext week\b""").containsMatchIn(lower)) return today.plusWeeks(1)
        if (Regex("""\bend of (the )?month\b""").containsMatchIn(lower)) return today.with(TemporalAdjusters.lastDayOfMonth())
        if (Regex("""\bend of (the )?week\b""").containsMatchIn(lower)) return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        if (Regex("""\b(this )?weekend\b""").containsMatchIn(lower)) return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        Regex("""\bthe (\d{1,2})(st|nd|rd|th)\b""").find(lower)?.let {
            val day = it.groupValues[1].toInt()
            var d = runCatching { today.withDayOfMonth(day) }.getOrNull()
            if (d == null || d.isBefore(today)) d = runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
            if (d != null) return d
        }
        return Extractor.parseDue(text, today)
    }

    // ------------------------------------------------------------------ repeat

    private val repeatPhrase = Regex(
        """(?i)[\s,]*\b((every|each)\s+(single\s+)?(week\s?days?|day|week|month|year|($WEEKDAY))|daily|weekly|monthly|yearly|annually)\b"""
    )

    /** "every Monday", "each week", "daily", "every weekday"; the due date still comes from [due]. */
    fun recurrence(text: String): Recurrence {
        val phrase = repeatPhrase.find(text)?.groupValues?.get(1)?.lowercase() ?: return Recurrence.NONE
        return when {
            "weekday" in phrase || "week day" in phrase -> Recurrence.WEEKDAYS
            phrase.endsWith("day") && !Regex(WEEKDAY).containsMatchIn(phrase) || phrase == "daily" -> Recurrence.DAILY
            "week" in phrase || Regex(WEEKDAY).containsMatchIn(phrase) -> Recurrence.WEEKLY
            "month" in phrase -> Recurrence.MONTHLY
            else -> Recurrence.YEARLY
        }
    }

    // ------------------------------------------------------------------ person / context

    /** A saved person named in the sentence (longest name wins), else the Gmail rules, else "Me". */
    fun person(text: String, people: List<String>): String {
        people.filter { it.isNotBlank() && !it.equals("Me", ignoreCase = true) }
            .sortedByDescending { it.length }
            .firstOrNull { Regex("""(?i)\b${Regex.escape(it)}\b""").containsMatchIn(text) }
            ?.let { return it }
        return Extractor.person(text)
    }

    /** A saved context named in the sentence ("Kids/School" matches "kids" or "school"), else keyword rules. */
    fun context(text: String, contexts: List<String>): String {
        contexts.firstOrNull { c ->
            (listOf(c) + c.split('/', ' ', '&'))
                .map { it.trim() }
                .filter { it.length >= 3 }
                .any { Regex("""(?i)\b${Regex.escape(it)}\b""").containsMatchIn(text) }
        }?.let { return it }
        return Extractor.context(text)
    }

    // ------------------------------------------------------------------ title

    private val leadIn = Regex(
        """(?i)^\s*(hey\s+|ok(ay)?\s+|so\s+|please\s+)*""" +
            """(remind me (to\s+|about\s+)?|i need to\s+|i have to\s+|i('ve)? got to\s+|i should\s+|i must\s+|""" +
            """(please\s+)?(add|create|make)\s+(an?\s+)?(new\s+)?(task|action|to-?do|reminder)\s*(to\s+|for\s+|:\s*)?|""" +
            """don'?t (let me )?forget to\s+|make sure (to|i)\s+|need to\s+|have to\s+)"""
    )

    private const val WEEKDAY = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val MONTH = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"

    private val datePhrase = Regex(
        """(?i)[\s,]*\b((by|on|before|due|until|this|next|coming)\s+)*(the\s+)?""" +
            """(day after tomorrow|today|tonight|tomorrow|eod|end of (the\s+)?(day|week|month)|next week|weekend|""" +
            """in (\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten) (days?|weeks?)|""" +
            """($WEEKDAY)|($MONTH)[a-z]*\.?\s+\d{1,2}(st|nd|rd|th)?|\d{1,2}(st|nd|rd|th)|\d{4}-\d{2}-\d{2})\b""" +
            """(\s+(morning|afternoon|evening|night))?"""
    )

    private val clockTime = Regex("""(?i)[\s,]*\bat\s+\d{1,2}(:\d{2})?\s*(am|pm|a\.m\.|p\.m\.)""")

    /** The sentence minus "remind me to"-style lead-ins and the date words, capitalized. */
    fun title(text: String): String {
        val stripped = text
            .replace(leadIn, "")
            .replace(repeatPhrase, "")
            .replace(datePhrase, "")
            .replace(clockTime, "")
            .replace(Regex("""(?i)\s+(and|to|by|on|for|due)\s*$"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', ',', '.', ';', ':')
        val t = stripped.ifEmpty { text.trim() }
        return t.replaceFirstChar { it.uppercaseChar() }
    }

    // ------------------------------------------------------------------ Gemini Nano

    /**
     * Gemini Nano proofreading tuned for speech input. Returns null when Nano is not
     * ready on this phone or takes too long, so the rule-based title is kept.
     */
    private suspend fun tidy(context: Context, title: String): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val proofreader = Proofreading.getClient(
            ProofreaderOptions.builder(context)
                .setInputType(ProofreaderOptions.InputType.VOICE)
                .setLanguage(ProofreaderOptions.Language.ENGLISH)
                .build()
        )
        try {
            val status = proofreader.checkFeatureStatus().get(NANO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // DOWNLOADABLE: the first request starts the download; if it isn't done in time the rules' title stays.
            if (status == FeatureStatus.UNAVAILABLE || status == FeatureStatus.DOWNLOADING) return@withContext null
            val result = proofreader.runInference(ProofreadingRequest.builder(title).build())
                .get(NANO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            result.results.firstOrNull()?.text?.trim()?.trimEnd('.')
                ?.takeIf { it.isNotEmpty() }
                ?.replaceFirstChar { it.uppercaseChar() }
        } catch (e: Exception) {
            null
        } finally {
            proofreader.close()
        }
    }
}
