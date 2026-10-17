package com.sonas.actiontracker.sync

import android.Manifest
import android.app.Notification
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Daily due-date reminder: once a day, at or after the chosen time, posts one
 * notification listing open actions that are due today or overdue.
 *
 * A short periodic check (rather than one 24 h job) keeps the time right across
 * daylight-saving changes and still fires if the phone was off at reminder time.
 * The check only reads preferences until it is time, so it is cheap.
 */
object Reminders {
    private const val PREFS = "reminders"
    private const val WORK = "due_reminders"
    private const val CHANNEL = "due_reminders"
    private const val NOTIFICATION_ID = 10
    private const val MAX_LINES = 5

    data class Settings(val enabled: Boolean, val time: LocalTime)

    fun settings(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = p.getBoolean("enabled", true),
            time = LocalTime.of(p.getInt("hour", 8), p.getInt("minute", 0))
        )
    }

    fun save(context: Context, settings: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("hour", settings.time.hour)
            .putInt("minute", settings.time.minute)
            .apply()
        schedule(context)
    }

    /**
     * Starts or stops the periodic check to match the saved settings. Safe to call on every launch.
     * The check also backs up the time-of-day reminders, so it runs while either kind is on.
     */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!settings(context).enabled && !SlotReminders.anyEnabled(context)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Posts today's digest if reminders are on, it is past the reminder time, and none was posted today. */
    suspend fun checkAndNotify(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val s = settings(context)
        val today = now.toLocalDate()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!s.enabled || now.toLocalTime() < s.time) return
        if (prefs.getLong("last_day", Long.MIN_VALUE) == today.toEpochDay()) return

        val due = Db.get(context).dao().openDueBy(today.toEpochDay())
        // Record the day even when nothing is due, so the list isn't re-checked all day.
        prefs.edit().putLong("last_day", today.toEpochDay()).apply()
        if (due.isNotEmpty()) post(context, due, today)
    }

    private fun post(context: Context, due: List<Item>, today: LocalDate) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val todayEpoch = today.toEpochDay()
        val overdue = due.count { it.dueDate < todayEpoch }
        val dueToday = due.size - overdue
        val title = summary(dueToday, overdue)

        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        due.take(MAX_LINES).forEach { item ->
            val who = if (item.person != "Me") " · ${item.person}" else ""
            val late = if (item.dueDate < todayEpoch) "Overdue: " else ""
            style.addLine("$late${item.title}$who")
        }
        if (due.size > MAX_LINES) style.setSummaryText("+${due.size - MAX_LINES} more")

        // The lock screen shows only the count; titles stay hidden until the phone is unlocked.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Action Tracker")
            .setContentText(title)
            .build()

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(due.first().title)
            .setStyle(style)
            .setNumber(due.size)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    internal fun summary(dueToday: Int, overdue: Int): String {
        fun plural(n: Int) = if (n == 1) "action" else "actions"
        return when {
            overdue == 0 -> "$dueToday ${plural(dueToday)} due today"
            dueToday == 0 -> "$overdue overdue ${plural(overdue)}"
            else -> "$dueToday due today, $overdue overdue"
        }
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Due-date reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Reminders.checkAndNotify(applicationContext) }
        runCatching { SlotReminders.catchUp(applicationContext) }
        return Result.success()
    }
}
