package com.sonas.actiontracker.sync

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.STATUS_SUGGESTED
import com.sonas.actiontracker.data.TimeOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Turns a spoken sentence ("remind me to sign Vibu's permission slip by Friday")
 * into a draft action. Title, due date, time of day, person and context come from
 * rules, matched against the people and contexts already used in the app. If on-device Gemini
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

    /**
     * The context each person's actions usually have ("Raj" -> "Work"), from confirmed actions
     * only: Gmail suggestions carry guessed contexts. "Me" is left out since it spans everything.
     */
    fun usualContexts(items: List<Item>): Map<String, String> =
        items.filter { it.status != STATUS_SUGGESTED && it.person.isNotBlank() && !it.person.equals("Me", ignoreCase = true) }
            .groupBy { it.person.trim().lowercase() }
            .mapValues { (_, list) -> list.groupingBy { it.context }.eachCount().maxBy { it.value }.key }

    suspend fun build(
        context: Context,
        transcript: String,
        people: List<String>,
        contexts: List<String>,
        usual: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.now()
    ): Draft {
        val rules = fromRules(transcript, people, contexts, today, usual)
        val nanoTitle = tidy(context, rules.title)
        val item = if (nanoTitle != null) rules.copy(title = nanoTitle.take(MAX_TITLE_CHARS)) else rules
        return Draft(item, transcript.trim(), nanoTitle != null)
    }

    /**
     * The draft from the rules alone, without Gemini Nano. Used as-is where Nano can't run,
     * such as a reply typed or dictated into a notification (for example on a watch).
     */
    fun fromRules(
        transcript: String,
        people: List<String>,
        contexts: List<String>,
        today: LocalDate,
        usual: Map<String, String> = emptyMap()
    ): Item {
        val text = transcript.trim()
        val mention = findPerson(text, people)
        val person = mention?.person ?: Extractor.person(text)
        val ctx = context(text, contexts, person, usual)
        return Item(
            title = title(text, mention?.spoken, mention?.person).take(MAX_TITLE_CHARS),
            notes = "Voice: $text",
            person = person,
            context = ctx,
            dueDate = (due(text, today) ?: today.plusDays(1)).toEpochDay(), // default: tomorrow
            status = STATUS_OPEN,
            source = "VOICE",
            timeOfDay = (timeOfDay(text) ?: TimeOfDay.defaultFor(ctx)).name,
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
        if (Regex("""\b(this (morning|afternoon|evening)|later today|in (\d+|a|an|a few|one|two|three) (hours?|minutes?|mins?))\b""").containsMatchIn(lower)) return today
        Regex("""\bin (\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten) (days?|weeks?)\b""").find(lower)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: numbers.getValue(it.groupValues[1])
            return if (it.groupValues[2].startsWith("week")) today.plusWeeks(n.toLong()) else today.plusDays(n.toLong())
        }
        if (Regex("""\bnext week\b""").containsMatchIn(lower)) return today.plusWeeks(1)
        if (Regex("""\bnext month\b""").containsMatchIn(lower)) return today.plusMonths(1)
        if (Regex("""\bend of (the )?month\b""").containsMatchIn(lower)) return today.with(TemporalAdjusters.lastDayOfMonth())
        if (Regex("""\bend of (the )?week\b""").containsMatchIn(lower)) return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        if (Regex("""\b(this )?weekend\b""").containsMatchIn(lower)) return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        Regex("""\bthe (\d{1,2})(st|nd|rd|th)\b""").find(lower)?.let {
            val day = it.groupValues[1].toInt()
            var d = runCatching { today.withDayOfMonth(day) }.getOrNull()
            if (d == null || d.isBefore(today)) d = runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
            if (d != null) return d
        }
        // "December 10th": the Gmail parser only knows "Dec 10".
        Regex("""\b($MONTH)[a-z]*\.?\s+(\d{1,2})(st|nd|rd|th)\b""").find(lower)?.let {
            val month = MONTH.split('|').indexOf(it.groupValues[1]) + 1
            var d = runCatching { LocalDate.of(today.year, month, it.groupValues[2].toInt()) }.getOrNull()
            if (d != null && d.isBefore(today)) d = d.plusYears(1)
            if (d != null) return d
        }
        return Extractor.parseDue(text, today)
    }

    // ------------------------------------------------------------------ time of day

    private val clock = Regex("""(?i)\b(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.|o'?clock)""")
    private val bareClock = Regex("""(?i)\b(?:at|around|by)\s+(\d{1,2})(?::(\d{2}))?\b(?!\s*(?:st|nd|rd|th|days?|weeks?|hours?|minutes?)\b)""")

    /** "at 3 pm", "tomorrow morning", "tonight", "after school"; null when the sentence names no time. */
    fun timeOfDay(text: String): TimeOfDay? {
        val lower = text.lowercase()
        (clock.find(lower) ?: bareClock.find(lower))?.let { m ->
            val h = m.groupValues[1].toInt()
            val suffix = m.groupValues.getOrNull(3).orEmpty()
            val hour = when {
                suffix.startsWith("p") && h < 12 -> h + 12
                suffix.startsWith("a") && h == 12 -> 0
                suffix.startsWith("a") || suffix.startsWith("p") -> h
                h in 1..6 -> h + 12 // a bare "at 5" for a personal task means 5 PM
                h in 7..8 -> h + 12
                else -> h
            }
            if (h in 0..23) return when (hour) {
                in 4..11 -> TimeOfDay.MORNING
                in 12..16 -> TimeOfDay.AFTERNOON
                in 17..20 -> TimeOfDay.EVENING
                else -> TimeOfDay.NIGHT
            }
        }
        return when {
            Regex("""\b(morning|before work|first thing)\b""").containsMatchIn(lower) -> TimeOfDay.MORNING
            Regex("""\b(afternoon|noon|midday|after school|lunch ?time)\b""").containsMatchIn(lower) -> TimeOfDay.AFTERNOON
            Regex("""\b(evening|tonight|after work)\b""").containsMatchIn(lower) -> TimeOfDay.EVENING
            Regex("""\b(at night|night|bedtime|before bed|midnight)\b""").containsMatchIn(lower) -> TimeOfDay.NIGHT
            else -> null
        }
    }

    // ------------------------------------------------------------------ repeat

    private val repeatPhrase = Regex(
        """(?i)[\s,]*\b((every|each)\s+(single\s+)?(week\s?days?|day|week|month|year|($WEEKDAY))|daily|weekly|monthly|yearly|annually)\b""" +
            """(\s+(morning|afternoon|evening|night)s?)?"""
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

    // ------------------------------------------------------------------ person

    /**
     * How the speech recognizer tends to spell the family names, plus how Sona refers to them.
     * A saved person spelled exactly as spoken still wins over these.
     */
    private val aliases = mapOf(
        "nathiya" to listOf("nathia", "natia", "nithya", "nadia", "my wife"),
        "vibu" to listOf("vibhu", "viboo", "vibo"),
        "dharshu" to listOf("darshu", "dharsu", "darsu", "dharsini", "dharshini", "darshini", "darsini")
    )

    /** A person found in the sentence, with the words used for them (to drop "for <them>" from the title). */
    data class Mention(val person: String, val spoken: String, val start: Int, val score: Int)

    // Words just before a name that say the task is about that person.
    private val forThem = Regex("""(?i)\b(for|ask|tell|remind|get|have|let|help)\s+$""")
    private val actOnThem = Regex(
        """(?i)\b(call|text|email|e-mail|message|ping|phone|pick up|pick|drop off|drop|take|bring|meet|pay|send|book|invite|visit|thank|wish|check on)\s+$"""
    )
    private val inPassing = Regex("""(?i)\b(from|to|about|than|like|of|at|and|with)\s+$""")
    private val word = Regex("""[A-Za-z]+('s)?""")
    private val honorific = setOf("mr", "mrs", "ms", "dr", "miss", "uncle", "aunty", "auntie")

    /**
     * The person the sentence is about. Every saved person ("Me" aside) is looked for by name,
     * by a common mis-hearing ("Vibhu", "Darshu") and by a near spelling; when several are named,
     * the one the task is for wins ("ask Nathiya to call Vibu's teacher" is Nathiya's), then the
     * first named. Falls back to the Gmail rules, then "Me".
     */
    fun person(text: String, people: List<String>): String = findPerson(text, people)?.person ?: Extractor.person(text)

    fun findPerson(text: String, people: List<String>): Mention? {
        val found = mutableListOf<Mention>()
        fun record(person: String, start: Int, end: Int, possessive: Boolean) {
            val before = text.substring(0, start)
            val role = when {
                forThem.containsMatchIn(before) -> 4
                actOnThem.containsMatchIn(before) || possessive -> 3
                inPassing.containsMatchIn(before) -> 1
                else -> 2
            }
            found += Mention(person, text.substring(start, end), start, role)
        }
        for (p in people.map { it.trim() }.filter { it.isNotEmpty() && !it.equals("Me", ignoreCase = true) }) {
            val names = buildList {
                add(p)
                addAll(aliases[p.lowercase()].orEmpty())
                p.split(' ').firstOrNull { it.length >= 3 && it.lowercase().trimEnd('.') !in honorific }
                    ?.takeIf { p.contains(' ') }?.let { add(it) }
            }
            for (n in names) {
                Regex("""(?i)\b${Regex.escape(n)}('s|s)?\b""").findAll(text).forEach {
                    record(p, it.range.first, it.range.first + n.length, it.groupValues[1].isNotEmpty())
                }
            }
            if (!p.contains(' ') && sound(p).length >= 5) {
                word.findAll(text).forEach { w ->
                    val possessive = w.value.endsWith("'s")
                    val bare = w.value.removeSuffix("'s")
                    if (closeTo(bare, p)) record(p, w.range.first, w.range.first + bare.length, possessive)
                }
            }
        }
        return found.sortedWith(compareByDescending<Mention> { it.score }.thenBy { it.start }).firstOrNull()
    }

    /** Spelling folded the way names get mis-heard: no h's, no doubled letters, ee/oo as i/u. */
    private fun sound(s: String): String = s.lowercase().filter { it.isLetter() }
        .replace("ee", "i").replace("oo", "u").replace("h", "")
        .replace(Regex("""(.)\1+"""), "$1")

    /** One letter off a name of five or more sounds, starting with the same letter. */
    private fun closeTo(spoken: String, name: String): Boolean {
        val a = sound(spoken)
        val b = sound(name)
        return a.isNotEmpty() && a[0] == b[0] && editDistance(a, b) <= 1
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    // ------------------------------------------------------------------ context

    private val schoolWords = Regex(
        """(?i)\b(school|teacher|class|classroom|homework|pta|isd|field trip|permission slip|principal|""" +
            """soccer|practice|piano|swim|swimming|tutor|tutoring|recital|science project|report card|school bus)\b"""
    )
    private val familyWords = Regex(
        """(?i)\b(family|birthday|anniversary|doctor|dentist|pediatrician|dinner|vacation|trip|home|house|""" +
            """groceries|grocery|costco|walmart|milk|car|oil change|registration|bills?|rent|mortgage|insurance|""" +
            """gift|cake|party|temple|haircut|passport|mom|dad|amma|appa|plumber|laundry|trash|tickets?)\b"""
    )
    private val workWords = Regex(
        """(?i)\b(work|office|meeting|client|customer|deck|slides|report|manager|standup|stand-up|ticket|jira|""" +
            """deploy|release|invoice|timesheet|expense report|team|one on one|1:1|review|interview)\b"""
    )

    /**
     * The context: a saved context named in the sentence ("Kids/School" matches "kids" or
     * "school"), else keywords (school, then family, then work), else the person's usual
     * context, else Family for the built-in family members, else Work.
     */
    fun context(text: String, contexts: List<String>, person: String = "Me", usual: Map<String, String> = emptyMap()): String {
        // "work on his project" and "work out" are about doing, not the Work context.
        val t = text.replace(Regex("""(?i)\bwork(ing)?\s+(on|out)\b"""), " ")
        contexts.firstOrNull { c ->
            (listOf(c) + c.split('/', ' ', '&'))
                .map { it.trim() }
                .filter { it.length >= 3 }
                .any { Regex("""(?i)\b${Regex.escape(it)}\b""").containsMatchIn(t) }
        }?.let { return it }
        fun saved(like: String, default: String) =
            contexts.firstOrNull { Regex("""(?i)\b($like)\b""").containsMatchIn(it) } ?: default
        return when {
            schoolWords.containsMatchIn(t) -> saved("school|kids", "Kids/School")
            familyWords.containsMatchIn(t) -> saved("family|home", "Family")
            workWords.containsMatchIn(t) -> saved("work|office", "Work")
            else -> usual[person.trim().lowercase()]
                ?: if (DEFAULT_PEOPLE.drop(1).any { it.equals(person, ignoreCase = true) }) saved("family|home", "Family")
                else saved("work|office", "Work")
        }
    }

    // ------------------------------------------------------------------ title

    private val filler = Regex("""(?i)\b(um+|uh+|uhm|erm|hmm+)\b,?""")

    private val leadIn = Regex(
        """(?i)^\s*((hey|hi|ok(ay)?|alright|so|and|also|please|oh)\b[\s,]*)*""" +
            """((can|could|would|will) you\s+(please\s+)?|""" +
            """remind me (to\s+|about\s+|that\s+)?|""" +
            """set (up )?(an?\s+)?reminder\s*(to\s+|for\s+|about\s+|:\s*)?|""" +
            """(add|create|make|put|new)\s+(in\s+)?(an?\s+)?(new\s+)?(task|action|to-?do|reminder|item)\s*(to\s+|about\s+|:\s*)?|""" +
            """(i|we)\s+(really\s+)?(want|need|have|got|ought|would like|'d like|wanna|gotta|should|must|'ve got|have got|'ll need|will need|'ll have)(\s+to)?\s+|""" +
            """i'?m (going|gonna|supposed) to\s+|""" +
            """remember to\s+|don'?t (let me )?forget (to\s+)?|make sure (to|that i|i)\s+|""" +
            """note to self\s*:?\s*|to-?do\s*:?\s*|need to\s+|have to\s+|got to\s+|""" +
            """to\s+)"""
    )

    private const val WEEKDAY = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val MONTH = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"

    private val datePhrase = Regex(
        """(?i)[\s,]*\b((by|on|before|due|until|this|next|coming)\s+)*(the\s+)?""" +
            """(day after tomorrow|today|tonight|tomorrow|eod|end of (the\s+)?(day|week|month)|next (week|month)|weekend|later today|""" +
            """in (\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten) (days?|weeks?)|""" +
            """in (\d+|a|an|a few|one|two|three) (hours?|minutes?|mins?)|""" +
            """($WEEKDAY)|($MONTH)[a-z]*\.?\s+\d{1,2}(st|nd|rd|th)?|\d{1,2}(st|nd|rd|th)|\d{4}-\d{2}-\d{2})\b""" +
            """(\s+(morning|afternoon|evening|night))?"""
    )

    private val clockTime = Regex(
        """(?i)[\s,]*\b((at|around|by|before|after)\s+)?\d{1,2}(:\d{2})?\s*(am\b|pm\b|a\.m\.|p\.m\.|o'?clock\b)|""" +
            """[\s,]*\b(at|around)\s+\d{1,2}(:\d{2})?\b(?!\s*(?:st|nd|rd|th|days?|weeks?|hours?|minutes?)\b)|""" +
            """[\s,]*\b(at|around|by)\s+(noon|midnight)\b"""
    )
    private val dayPart = Regex("""(?i)[\s,]*\b(in the|this|at|by|before|tomorrow)\s+(morning|afternoon|evening|night)\b|[\s,]*\b(first thing)\b""")
    private val politeEnd = Regex("""(?i)[\s,]*\b(please|thanks|thank you|okay|ok)[\s.!]*$""")

    /**
     * The task in a few words: the sentence minus "remind me to"-style lead-ins, filler, date,
     * time and repeat words, and a trailing "for <person>" when [spokenPerson] is who it's for
     * (the person shows as a tag). "Add a task for Vibu to clean his room" becomes "Clean his room".
     */
    fun title(text: String, spokenPerson: String? = null, savedPerson: String? = null): String {
        // Lead-ins stack ("okay so can you remind me to ..."), so peel them until none is left.
        fun peel(s: String): String {
            var cur = s.trim()
            while (true) {
                val next = cur.replace(leadIn, "").trim()
                if (next == cur || next.isBlank()) return cur
                cur = next
            }
        }
        var t = peel(text.replace(filler, " ").replace(Regex("""\s{2,}"""), " "))
        // Again after the date words go, for "remind me in 2 hours to check the oven".
        t = peel(
            t.replace(repeatPhrase, "")
                .replace(datePhrase, "")
                .replace(clockTime, "")
                .replace(dayPart, "")
                .replace(politeEnd, "")
        )
        if (spokenPerson != null) {
            val who = Regex.escape(spokenPerson)
            t = t.replace(Regex("""(?i)^\s*(for\s+)?$who\s+(to|needs to|has to|should|must)\s+"""), "")
                .replace(Regex("""(?i)\s+for\s+$who\s*[.,!]*$"""), "")
            // A mis-heard name ("Vibhu's") is written the way it is saved ("Vibu's").
            if (savedPerson != null && ' ' !in spokenPerson && spokenPerson != savedPerson) {
                t = t.replace(Regex("""(?i)\b$who\b"""), Regex.escapeReplacement(savedPerson))
            }
        }
        val stripped = t
            .replace(Regex("""(?i)\s+(for|at)\s+(work|the office|school)\s*$"""), "")
            .replace(Regex("""(?i)\s+(and|to|by|on|for|due|at|in)\s*$"""), "")
            .replace(Regex("""\s+([,.;:!?])"""), "$1")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', ',', '.', ';', ':', '!', '?')
        val result = stripped.ifEmpty { text.trim() }
        return result.replaceFirstChar { it.uppercaseChar() }
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
