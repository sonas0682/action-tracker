package com.sonas.actiontracker.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Processed
import com.sonas.actiontracker.data.STATUS_SUGGESTED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

object SyncEngine {
    /**
     * Pulls new Gmail + Calendar items and stores action candidates as SUGGESTED.
     * Every message/event is marked processed the first time it is seen, so it is
     * never evaluated again (no duplicate actions). Returns the number of new suggestions.
     */
    suspend fun run(context: Context, token: String): Int = withContext(Dispatchers.IO) {
        val dao = Db.get(context).dao()
        val today = LocalDate.now()
        var added = 0

        for (mail in Gmail.recent(token)) {
            val key = "gmail:${mail.id}"
            if (dao.seen(key) > 0) continue
            dao.markProcessed(Processed(key, System.currentTimeMillis()))
            val s = Extractor.fromMail(mail.subject, mail.from, mail.snippet, today) ?: continue
            dao.insert(s.toItem("GMAIL"))
            added++
        }
        for (event in Calendar.upcoming(token)) {
            val key = "cal:${event.id}"
            if (dao.seen(key) > 0) continue
            dao.markProcessed(Processed(key, System.currentTimeMillis()))
            val s = Extractor.fromEvent(event.summary, event.description, event.date, today) ?: continue
            dao.insert(s.toItem("CALENDAR"))
            added++
        }
        added
    }

    private fun Suggestion.toItem(source: String) = Item(
        title = title,
        notes = notes,
        person = person,
        context = context,
        dueDate = due.toEpochDay(),
        status = STATUS_SUGGESTED,
        source = source
    )
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val auth = GoogleAuth.authorize(applicationContext)
            val token = auth.accessToken
            if (auth.hasResolution() || token == null) {
                Notify.signInNeeded(applicationContext)
                return Result.success()
            }
            val added = SyncEngine.run(applicationContext, token)
            if (added > 0) Notify.newSuggestions(applicationContext, added)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

object Notify {
    private const val CHANNEL = "suggestions"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "New suggestions", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun newSuggestions(context: Context, count: Int) =
        post(context, 1, "Action Tracker", "$count new suggested action item(s) to review")

    fun signInNeeded(context: Context) =
        post(context, 2, "Action Tracker", "Sign in to Google again to keep syncing")

    private fun post(context: Context, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
