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
 * suggestions into a clean one-line title. The rules still decide what counts as
 * an action; Nano only improves the wording. Nothing leaves the phone.
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

    private val lock = Mutex()

    /** Retitles every suggestion still holding email text. Returns how many were retitled. */
    suspend fun run(context: Context): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val dao = Db.get(context).dao()
            val pending = dao.awaitingSmartTitle()
            if (pending.isEmpty()) return@withContext 0

            val summarizer = Summarization.getClient(
                SummarizerOptions.builder(context)
                    .setInputType(SummarizerOptions.InputType.ARTICLE)
                    .setOutputType(SummarizerOptions.OutputType.ONE_BULLET)
                    .setLanguage(SummarizerOptions.Language.ENGLISH)
                    .build()
            )
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
                    dao.update(
                        if (title.isEmpty()) item.copy(body = "")
                        else item.copy(title = title, notes = "Subject: ${item.title}\n${item.notes}".take(400), body = "")
                    )
                    done++
                }
                done
            } catch (e: Exception) {
                0
            } finally {
                summarizer.close()
            }
        }
    }

    private fun clean(summary: String): String =
        summary.lines().firstOrNull { it.isNotBlank() }.orEmpty()
            .trim().removePrefix("*").removePrefix("-").removePrefix("•").trim()
            .take(MAX_TITLE_CHARS)
}
