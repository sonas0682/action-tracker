package com.sonas.actiontracker.widget

import android.content.Context
import android.content.SharedPreferences
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The two-to-three-line summary of today's actions at the bottom of the widget.
 *
 * Gemini Nano only runs while the app is in the foreground, so the widget can't call
 * it itself. Instead the summary is written whenever the app is open (or the widget's
 * refresh button briefly opens an invisible screen) and cached together with a
 * fingerprint of today's open actions. The widget shows the cached text only while
 * the fingerprint still matches; otherwise it shows a rule-based summary.
 */
object WidgetSummary {
    private const val PREFS = "widget_summary"
    private const val KEY_TEXT = "text"
    private const val KEY_FINGERPRINT = "fingerprint"

    /** The summarizer rejects article input shorter than this. */
    private const val MIN_INPUT_CHARS = 400
    private const val MAX_INPUT_CHARS = 6_000
    private const val MAX_NOTE_CHARS = 300

    private val lock = Mutex()

    data class Cached(val text: String, val fingerprint: String)

    /** Identifies today's open list; any add, edit, completion or new day changes it. */
    fun fingerprint(today: List<Item>, epochDay: Long): String =
        (listOf(epochDay.toString()) + today.sortedBy { it.id }.map { "${it.id}|${it.title}|${it.dueDate}|${it.person}" })
            .joinToString("\n").hashCode().toString()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(p: SharedPreferences): Cached? {
        val text = p.getString(KEY_TEXT, null) ?: return null
        return Cached(text, p.getString(KEY_FINGERPRINT, "").orEmpty())
    }

    /** The cached Nano summary, re-emitted whenever it is rewritten. */
    fun flow(context: Context): Flow<Cached?> = callbackFlow {
        val p = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, _ -> trySend(read(sp)) }
        trySend(read(p))
        p.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun cached(context: Context): Cached? = read(prefs(context))

    /** The rule-based summary shown until Nano has summarized the current list. */
    fun fallback(today: List<Item>, tomorrow: Int, epochDay: Long): String {
        if (today.isEmpty()) {
            return if (tomorrow > 0) "All clear for today.\n$tomorrow lined up for tomorrow." else "All clear for today and tomorrow."
        }
        val overdue = today.count { it.dueDate < epochDay }
        val head = buildString {
            append("${today.size} due today")
            if (overdue > 0) append(", $overdue overdue")
            append(".")
        }
        val first = today.take(3).joinToString(" · ") { it.title }
        return "$head\nUp first: $first"
    }

    /**
     * Summarizes today's open actions with Gemini Nano and caches the result. Only call
     * while an activity of the app is in the foreground. Returns true when the cache was
     * updated. Fewer actions than Nano's minimum input size get no Nano summary; the
     * rule-based one already says everything for a short list.
     */
    suspend fun summarize(context: Context): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val epochDay = LocalDate.now().toEpochDay()
            val today = Db.get(context).dao().openDueBy(epochDay)
            val fp = fingerprint(today, epochDay)
            if (cached(context)?.fingerprint == fp) return@withContext false
            val input = describe(today, epochDay)
            if (input.length < MIN_INPUT_CHARS) return@withContext false

            val client = Summarization.getClient(
                SummarizerOptions.builder(context)
                    .setInputType(SummarizerOptions.InputType.ARTICLE)
                    .setOutputType(
                        if (today.size >= 3) SummarizerOptions.OutputType.THREE_BULLETS
                        else SummarizerOptions.OutputType.TWO_BULLETS
                    )
                    .setLanguage(SummarizerOptions.Language.ENGLISH)
                    .build()
            )
            try {
                val status = client.checkFeatureStatus().get()
                if (status == FeatureStatus.UNAVAILABLE || status == FeatureStatus.DOWNLOADING) return@withContext false
                val summary = client.runInference(SummarizationRequest.builder(input).build()).get().summary
                val lines = summary.lines()
                    .map { it.trim().removePrefix("*").removePrefix("-").removePrefix("•").trim() }
                    .filter { it.isNotEmpty() }
                    .take(3)
                if (lines.isEmpty()) return@withContext false
                prefs(context).edit()
                    .putString(KEY_TEXT, lines.joinToString("\n"))
                    .putString(KEY_FINGERPRINT, fp)
                    .apply()
                true
            } catch (e: Exception) {
                false // e.g. the screen closed before Nano finished; the rule-based summary stays
            } finally {
                client.close()
            }
        }
    }

    /** Plain-prose description of today's list for the summarizer. */
    private fun describe(today: List<Item>, epochDay: Long): String = buildString {
        append("This is my to-do list for today. I have ${today.size} open action")
        append(if (today.size == 1) ". " else "s. ")
        today.forEachIndexed { i, item ->
            append("Action ${i + 1}: ${item.title}. ")
            if (item.person != "Me") append("It involves ${item.person}. ")
            append("It is ${item.context.lowercase()} related. ")
            if (item.dueDate < epochDay) append("It is overdue by ${epochDay - item.dueDate} day(s). ") else append("It is due today. ")
            val note = item.notes.lines()
                .filterNot { it.startsWith("Subject: ") || it.startsWith("From: ") || it.startsWith("Summary:") }
                .joinToString(" ") { it.removePrefix("•").trim() }
                .trim()
                .take(MAX_NOTE_CHARS)
            if (note.isNotEmpty()) append("Details: $note ")
            append("\n")
        }
    }.take(MAX_INPUT_CHARS)
}
