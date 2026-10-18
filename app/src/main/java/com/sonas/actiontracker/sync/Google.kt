package com.sonas.actiontracker.sync

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Base64

/**
 * Gmail and Calendar access. Each source can be switched off on its own (⋮ → Permissions &
 * features), and a source the user unticks on Google's consent screen is switched off too, so
 * the app only ever asks for what is on and the other source keeps working.
 */
object GoogleAuth {
    const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
    private const val PREFS = "google_sources"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun gmailOn(context: Context) = prefs(context).getBoolean("gmail", true)
    fun calendarOn(context: Context) = prefs(context).getBoolean("calendar", true)
    fun setGmail(context: Context, on: Boolean) = prefs(context).edit().putBoolean("gmail", on).apply()
    fun setCalendar(context: Context, on: Boolean) = prefs(context).edit().putBoolean("calendar", on).apply()

    /** True once a sync has been granted at least one source, for the Permissions & features screen. */
    fun everGranted(context: Context) = prefs(context).getBoolean("granted_once", false)

    /** The scopes to ask for: only the sources that are switched on. */
    fun wanted(context: Context): List<String> = buildList {
        if (gmailOn(context)) add(GMAIL_SCOPE)
        if (calendarOn(context)) add(CALENDAR_SCOPE)
    }

    /** Null when both Gmail and Calendar are switched off, so there is nothing to ask for. */
    suspend fun authorize(context: Context): AuthorizationResult? {
        val scopes = wanted(context).ifEmpty { return null }.map(::Scope)
        return Identity.getAuthorizationClient(context)
            .authorize(AuthorizationRequest.builder().setRequestedScopes(scopes).build())
            .await()
    }

    /**
     * After the consent screen: switches off any source the user didn't allow, so the next
     * sync doesn't ask again, and returns their names ("Gmail", "Calendar") for a message.
     */
    fun recordGrant(context: Context, granted: Collection<String>): List<String> {
        val requested = wanted(context)
        val declined = declined(requested, granted)
        if (GMAIL_SCOPE in declined) setGmail(context, false)
        if (CALENDAR_SCOPE in declined) setCalendar(context, false)
        if (requested.any { it in granted }) prefs(context).edit().putBoolean("granted_once", true).apply()
        return declined.map { if (it == GMAIL_SCOPE) "Gmail" else "Calendar" }
    }

    /** The requested scopes missing from the grant. */
    fun declined(requested: Collection<String>, granted: Collection<String>): List<String> =
        requested.filter { it !in granted }
}

private fun httpGet(url: String, token: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.setRequestProperty("Authorization", "Bearer $token")
    conn.connectTimeout = 15_000
    conn.readTimeout = 20_000
    try {
        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for ${url.substringBefore('?')}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

data class Mail(val id: String, val subject: String, val from: String, val snippet: String)
data class CalEvent(val id: String, val summary: String, val description: String, val date: LocalDate)

object Gmail {
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me/messages"

    /**
     * Recent inbox mail, skipping promotions and social. Only metadata + snippet are fetched,
     * and only for ids [isNew] accepts, so mail seen on an earlier sync costs no extra request.
     */
    suspend fun recent(token: String, isNew: suspend (String) -> Boolean = { true }): List<Mail> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("in:inbox -category:promotions -category:social newer_than:2d", "UTF-8")
        val list = JSONObject(httpGet("$BASE?q=$q&maxResults=30", token))
        val ids = list.optJSONArray("messages") ?: return@withContext emptyList()
        (0 until ids.length()).map { ids.getJSONObject(it).getString("id") }.filter { isNew(it) }.map { id ->
            val msg = JSONObject(
                httpGet("$BASE/$id?format=metadata&metadataHeaders=Subject&metadataHeaders=From", token)
            )
            val headers = msg.getJSONObject("payload").optJSONArray("headers")
            var subject = ""
            var from = ""
            if (headers != null) {
                for (h in 0 until headers.length()) {
                    val header = headers.getJSONObject(h)
                    when (header.getString("name").lowercase()) {
                        "subject" -> subject = header.getString("value")
                        "from" -> from = header.getString("value")
                    }
                }
            }
            Mail(id, subject, from, msg.optString("snippet"))
        }
    }
}

/**
 * Finds the id of a message already turned into an item, by its subject and sender, within a
 * few days of when the item was created. Items don't store the Gmail id, so this is a best match.
 */
suspend fun Gmail.find(subject: String, from: String, createdAt: Long, token: String): String? = withContext(Dispatchers.IO) {
    val words = subject.replace(Regex("[\"(){}:]"), " ").replace(Regex("\\s+"), " ").trim()
    if (words.isEmpty()) return@withContext null
    val sender = Regex("<([^>]+)>").find(from)?.groupValues?.get(1) ?: from.trim()
    val created = createdAt / 1000
    val q = buildString {
        append("subject:\"$words\" after:${created - 5 * 86_400L} before:${created + 86_400L}")
        if (sender.contains('@')) append(" from:$sender")
    }
    val list = JSONObject(
        httpGet("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${URLEncoder.encode(q, "UTF-8")}&maxResults=1", token)
    )
    list.optJSONArray("messages")?.optJSONObject(0)?.optString("id")?.ifEmpty { null }
}

/** Plain text of one message (text/plain part, or tag-stripped HTML), for the on-device title pass. */
suspend fun Gmail.body(id: String, token: String): String = withContext(Dispatchers.IO) {
    val payload = JSONObject(httpGet("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=full", token))
        .optJSONObject("payload") ?: return@withContext ""
    val plain = findPart(payload, "text/plain")
    val text = plain ?: findPart(payload, "text/html")
        ?.replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), " ")
        ?.replace(Regex("<[^>]+>"), " ")
        ?.replace(Regex("&nbsp;|&#160;"), " ")
    (text ?: "").replace(Regex("\\s+"), " ").trim()
}

private fun findPart(part: JSONObject, mime: String): String? {
    if (part.optString("mimeType") == mime) {
        val data = part.optJSONObject("body")?.optString("data").orEmpty()
        if (data.isNotEmpty()) return String(Base64.getUrlDecoder().decode(data), Charsets.UTF_8)
    }
    val parts = part.optJSONArray("parts") ?: return null
    for (i in 0 until parts.length()) findPart(parts.getJSONObject(i), mime)?.let { return it }
    return null
}

object Calendar {
    /** Events starting in the next 7 days on the primary calendar. */
    suspend fun upcoming(token: String): List<CalEvent> = withContext(Dispatchers.IO) {
        val min = URLEncoder.encode(Instant.now().toString(), "UTF-8")
        val max = URLEncoder.encode(Instant.now().plusSeconds(7 * 86_400L).toString(), "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
            "?timeMin=$min&timeMax=$max&singleEvents=true&orderBy=startTime&maxResults=50"
        val items = JSONObject(httpGet(url, token)).optJSONArray("items") ?: return@withContext emptyList()
        // One odd event (no id, an unexpected date format) is skipped instead of failing the whole sync.
        (0 until items.length()).mapNotNull { i ->
            runCatching {
                val e = items.getJSONObject(i)
                val start = e.optJSONObject("start") ?: return@mapNotNull null
                val date = if (start.has("dateTime")) {
                    OffsetDateTime.parse(start.getString("dateTime")).toLocalDate()
                } else {
                    LocalDate.parse(start.getString("date"))
                }
                CalEvent(e.getString("id"), e.optString("summary"), e.optString("description"), date)
            }.getOrNull()
        }
    }
}
