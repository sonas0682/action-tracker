package com.sonas.actiontracker.sync

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.sonas.actiontracker.data.Db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device Gemini Nano (ML Kit GenAI Summarization) pass that rewrites Gmail
 * suggestions into a clean one-line title and puts a short bullet summary of the
 * email in the notes. The rules still decide what counts as an action and set the
 * due date, person and context; Nano only improves the wording. Nothing leaves the phone.
 *
 * Android only allows Nano while the app is in the foreground, so this runs from
 * the app, never from background sync. Any failure leaves the rule-based title.
 */
object SmartTitles {
    /** Well under Nano's ~4,000-token input limit. */
    const val MAX_INPUT_CHARS = 6_000

    /** The summarizer rejects article input shorter than this. */
    private const val MIN_INPUT_CHARS = 400
    private const val MAX_TITLE_CHARS = 120
    private const val MAX_NOTES_CHARS = 800

    private val lock = Mutex()

    /** Retitles and summarizes every suggestion still holding email text. Returns how many were done. */
    suspend fun run(context: Context): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val dao = Db.get(context).dao()
            val pending = dao.awaitingSmartTitle()
            if (pending.isEmpty()) return@withContext 0

            val summarizer = client(context, SummarizerOptions.OutputType.ONE_BULLET)
            val bullets = client(context, SummarizerOptions.OutputType.THREE_BULLETS)
            try {
                val status = summarizer.checkFeatureStatus().get()
                // Not supported, or the model is still downloading: keep the text and try next time.
                if (status == FeatureStatus.UNAVAILABLE || status == FeatureStatus.DOWNLOADING) return@withContext 0

                var done = 0
                for (item in pending) {
                    if (item.body.length < MIN_INPUT_CHARS) {
                        dao.update(item.copy(body = ""))
                        continue
                    }
                    // The first request also starts the model download when status is DOWNLOADABLE.
                    val summary = runCatching {
                        summarizer.runInference(SummarizationRequest.builder(item.body).build()).get().summary
                    }.getOrNull() ?: return@withContext done // e.g. app went to background; retry later
                    val title = clean(summary)
                    // A missing summary is fine: the title still improves and the notes keep the subject.
                    val points = runCatching {
                        bullets.runInference(SummarizationRequest.builder(item.body).build()).get().summary
                    }.getOrNull()?.let(::bulletList).orEmpty()
                    val notes = buildString {
                        if (points.isNotEmpty()) append("Summary:\n").append(points).append("\n\n")
                        if (title.isNotEmpty()) append("Subject: ${item.title}\n")
                        append(item.notes)
                    }.trim().take(MAX_NOTES_CHARS)
                    dao.update(
                        if (title.isEmpty()) item.copy(notes = notes, body = "")
                        else item.copy(title = title, notes = notes, body = "")
                    )
                    done++
                }
                done
            } catch (e: Exception) {
                0
            } finally {
                summarizer.close()
                bullets.close()
            }
        }
    }

    private fun client(context: Context, output: Int) = Summarization.getClient(
        SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(output)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()
    )

    private fun stripBullet(line: String) =
        line.trim().removePrefix("*").removePrefix("-").removePrefix("•").trim()

    private fun clean(summary: String): String =
        stripBullet(summary.lines().firstOrNull { it.isNotBlank() }.orEmpty()).take(MAX_TITLE_CHARS)

    /** Normalizes Nano's bullets to "• point" lines. */
    private fun bulletList(summary: String): String =
        summary.lines().map(::stripBullet).filter { it.isNotEmpty() }.joinToString("\n") { "• $it" }
}
