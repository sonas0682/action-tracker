package com.sonas.actiontracker.sync

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Turns a photo or screenshot (a school flyer, a permission slip, a chat screenshot) into a
 * draft action, all on the phone:
 * 1. ML Kit text recognition reads the text in the image.
 * 2. ML Kit entity extraction finds the dates and times ("Fri, Oct 24 by 3 PM"); simple date
 *    rules take over when its small model isn't downloaded yet.
 * 3. Gemini Nano summarization writes the title and a three-bullet summary when the text is
 *    long enough; otherwise the title comes from the most task-like line.
 * Person and context use the same rules as voice capture. The image is never stored or sent.
 */
object PhotoDraft {
    data class Draft(val item: Item, val text: String, val summarizedByNano: Boolean, val datesByModel: Boolean)

    /** A date found in the text, with where it starts (to look at the words around it). */
    data class Found(val start: Int, val date: LocalDate, val time: LocalTime? = null)

    private const val MAX_TITLE_CHARS = 120
    private const val MAX_NOTES_CHARS = 1_000
    private const val MAX_PHOTO_TEXT_CHARS = 600
    /** Long enough for a one-time download of the small date model on a normal connection. */
    private const val DATE_MODEL_TIMEOUT_MS = 15_000L
    /** Images are scaled down to this long side: plenty for text, and safe for 200 MP photos. */
    private const val MAX_IMAGE_SIDE = 2_048

    /** Reads [uri] and drafts an action, or returns null when the image holds no readable text. */
    suspend fun build(
        context: Context,
        uri: Uri,
        people: List<String>,
        contexts: List<String>,
        usual: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.now()
    ): Draft? {
        val text = readText(context, uri).trim()
        if (text.length < 3) return null
        val modelDates = modelDates(text)
        val rules = fromText(text, modelDates ?: ruleDates(text, today), people, contexts, today, usual)
        val nano = SmartTitles.summarize(context, text)
        val item = if (nano == null) rules else rules.copy(
            title = nano.first.ifEmpty { rules.title }.take(MAX_TITLE_CHARS),
            notes = buildString {
                if (nano.second.isNotEmpty()) append("Summary:\n").append(nano.second).append("\n\n")
                append(rules.notes)
            }.take(MAX_NOTES_CHARS)
        )
        return Draft(item, text, nano != null, modelDates != null)
    }

    // ------------------------------------------------------------------ on-device models

    /** ML Kit text recognition (Latin script), bundled in the app so it works offline. */
    private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder applies the photo's rotation; software bitmaps are what ML Kit reads.
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_IMAGE_SIDE) {
                val scale = MAX_IMAGE_SIDE.toFloat() / side
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    /**
     * Dates and times from ML Kit entity extraction, relative to now. Null when its model
     * can't be downloaded or read in time, so the date rules are used instead.
     */
    private suspend fun modelDates(text: String): List<Found>? {
        val extractor = EntityExtraction.getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build())
        return try {
            withTimeoutOrNull(DATE_MODEL_TIMEOUT_MS) {
                extractor.downloadModelIfNeeded().await()
                val params = EntityExtractionParams.Builder(text)
                    .setReferenceTime(System.currentTimeMillis())
                    .setReferenceTimeZone(TimeZone.getDefault())
                    .setEntityTypesFilter(setOf(Entity.TYPE_DATE_TIME))
                    .build()
                val zone = ZoneId.systemDefault()
                extractor.annotate(params).await().flatMap { annotation ->
                    annotation.entities.mapNotNull { entity ->
                        val dt = entity.asDateTimeEntity() ?: return@mapNotNull null
                        val at = Instant.ofEpochMilli(dt.timestampMillis).atZone(zone)
                        val timed = dt.dateTimeGranularity in setOf(
                            DateTimeEntity.GRANULARITY_HOUR, DateTimeEntity.GRANULARITY_MINUTE, DateTimeEntity.GRANULARITY_SECOND
                        )
                        Found(annotation.start, at.toLocalDate(), if (timed) at.toLocalTime() else null)
                    }
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            extractor.close()
        }
    }

    // ------------------------------------------------------------------ rules (no models)

    /** The draft from the photo's text and the dates found in it, without Gemini Nano. */
    fun fromText(
        text: String,
        dates: List<Found>,
        people: List<String>,
        contexts: List<String>,
        today: LocalDate,
        usual: Map<String, String> = emptyMap()
    ): Item {
        val flat = text.replace(Regex("""\s+"""), " ").trim()
        val person = VoiceDraft.person(flat, people)
        val ctx = VoiceDraft.context(flat, contexts, person, usual)
        val due = pickDue(text, dates, today)
        val time = due?.time?.let { timeOfDay(it.hour) }
            ?: due?.let { VoiceDraft.timeOfDay(text.substring(it.start, minOf(text.length, it.start + 40))) }
            ?: TimeOfDay.defaultFor(ctx)
        return Item(
            title = title(text).take(MAX_TITLE_CHARS),
            notes = "From photo:\n" + text.trim().take(MAX_PHOTO_TEXT_CHARS),
            person = person,
            context = ctx,
            dueDate = (due?.date ?: today.plusDays(1)).toEpochDay(), // default: tomorrow
            status = STATUS_OPEN,
            source = "PHOTO",
            timeOfDay = time.name
        )
    }

    private val deadline = Regex(
        """(?i)\b(due|by|before|deadline|return(ed)?|rsvp|submit(ted)?|turn(ed)? in|sign ?up|register|""" +
            """registration|no later than|last day|pay(ment)?|closes?|ends?|expires?|until)\b[^\n]{0,30}$"""
    )

    /**
     * The due date: the first upcoming date that follows a deadline word ("Return by Oct 24"),
     * else the earliest upcoming date. Dates already past are ignored.
     */
    fun pickDue(text: String, dates: List<Found>, today: LocalDate): Found? {
        val upcoming = dates.filter { !it.date.isBefore(today) }
        val flagged = upcoming.filter { deadline.containsMatchIn(text.substring(maxOf(0, it.start - 40), it.start)) }
        return (flagged.ifEmpty { upcoming }).minWithOrNull(compareBy<Found> { it.date }.thenBy { it.start })
    }

    // Full names or abbreviations only, so "Mark 3" or "Junior 5" never read as dates.
    private const val MONTH = "january|february|march|april|may|june|july|august|september|october|november|december|" +
        "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"
    private val monthIndex = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthDay = Regex(
        """(?i)\b($MONTH)\b\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4}))?"""
    )
    private val dayMonth = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTH)\b(?:,?\s+(\d{4}))?""")
    private val numeric = Regex("""(?<![\d/])(\d{1,2})/(\d{1,2})(?:/(\d{2}|\d{4}))?(?![\d/])""")
    private val iso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

    /** Dates written out in the text ("Oct 24", "24th October", "10/24/2026", "2026-10-24"); US month/day order. */
    fun ruleDates(text: String, today: LocalDate): List<Found> {
        fun date(year: String?, month: Int, day: Int): LocalDate? {
            val y = year?.takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            val d = runCatching { LocalDate.of(y ?: today.year, month, day) }.getOrNull() ?: return null
            // A date without a year that has already passed this year means next year.
            return if (y == null && d.isBefore(today)) d.plusYears(1) else d
        }
        fun month(name: String) = monthIndex.indexOf(name.lowercase().take(3)) + 1
        return buildList {
            monthDay.findAll(text).forEach { m ->
                date(m.groupValues[3], month(m.groupValues[1]), m.groupValues[2].toInt())
                    ?.let { add(Found(m.range.first, it)) }
            }
            dayMonth.findAll(text).forEach { m ->
                date(m.groupValues[3], month(m.groupValues[2]), m.groupValues[1].toInt())
                    ?.let { add(Found(m.range.first, it)) }
            }
            numeric.findAll(text).forEach { m ->
                val month = m.groupValues[1].toInt()
                if (month in 1..12) date(m.groupValues[3], month, m.groupValues[2].toInt())?.let { add(Found(m.range.first, it)) }
            }
            iso.findAll(text).forEach { m ->
                runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                    .getOrNull()?.let { add(Found(m.range.first, it)) }
            }
        }.sortedBy { it.start }
    }

    private val taskWords = Regex(
        """(?i)\b(permission|slip|form|due|rsvp|field trip|picture day|fees?|payment|pay|register|registration|""" +
            """sign|bring|return|submit|meeting|conference|party|invit\w*|appointment|deadline|reminder|""" +
            """practice|recital|game|tryouts?|fundraiser|book fair|open house|volunteer|order|renew|bill)\b"""
    )

    /**
     * The most task-like line: the first one with a task word ("Field Trip Permission Slip"),
     * else the first line of a few words. ALL-CAPS headings become sentence case.
     */
    fun title(text: String): String {
        val lines = text.lines()
            .map { it.replace(Regex("""\s+"""), " ").trim(' ', '-', '•', '*', ':', '|') }
            .filter { line -> line.count { it.isLetter() } >= 3 }
        val pick = lines.firstOrNull { taskWords.containsMatchIn(it) && it.split(' ').size in 2..14 }
            ?: lines.firstOrNull { it.split(' ').size >= 2 }
            ?: lines.firstOrNull()
            ?: return "Photo task"
        val letters = pick.filter { it.isLetter() }
        val cased = if (letters.length >= 4 && letters.all { it.isUpperCase() }) pick.lowercase() else pick
        return cased.trimEnd('.', ',', ';', ':', '!').take(MAX_TITLE_CHARS).replaceFirstChar { it.uppercaseChar() }
    }

    fun timeOfDay(hour: Int): TimeOfDay = when (hour) {
        in 4..11 -> TimeOfDay.MORNING
        in 12..16 -> TimeOfDay.AFTERNOON
        in 17..20 -> TimeOfDay.EVENING
        else -> TimeOfDay.NIGHT
    }
}
