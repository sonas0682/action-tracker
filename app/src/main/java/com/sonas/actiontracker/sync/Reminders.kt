package com.sonas.actiontracker.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.widget.DueWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Morning digest: once a day, at or after 8 AM, posts a summary of open actions that are due
 * today or overdue, as one notification listing them ([TaskNotifications]). The time-of-day reminders
 * ([SlotReminders]) cover the rest of the day, so the digest is just on or off.
 *
 * A short periodic check (rather than one 24 h job) keeps the time right across
 * daylight-saving changes and still fires if the phone was off at reminder time.
 * The check only reads preferences until it is time, so it is cheap.
 */
object Reminders {
    private const val PREFS = "reminders"
    private const val WORK = "due_reminders"
    private const val CHANNEL = "due_reminders"

    val DIGEST_TIME: LocalTime = LocalTime.of(8, 0)

    /** Keeps the saved on/off from the old "Daily due-date reminder" switch; its custom time is dropped. */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled)
            .apply()
        schedule(context)
    }

    /**
     * Starts or stops the periodic check to match the saved settings. Safe to call on every launch.
     * The check also backs up the time-of-day reminders and adds the morning's God tasks, so it
     * runs while any of them is on.
     */
    fun schedule(context: Context) {
        // Builds before the fixed 8 AM digest saved a custom reminder time here.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("hour").remove("minute").apply()
        val wm = WorkManager.getInstance(context)
        if (!enabled(context) && !SlotReminders.anyEnabled(context) && !GodTasks.enabled(context)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Posts today's digest if it is on, it is past 8 AM, and none was posted today. */
    suspend fun checkAndNotify(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val today = now.toLocalDate()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!enabled(context) || now.toLocalTime() < DIGEST_TIME) return
        if (prefs.getLong("last_day", Long.MIN_VALUE) == today.toEpochDay()) return

        val due = Db.get(context).dao().openDueBy(today.toEpochDay())
        // Record the day even when nothing is due, so the list isn't re-checked all day.
        prefs.edit().putLong("last_day", today.toEpochDay()).apply()
        if (due.isNotEmpty()) post(context, due, today)
    }

    private fun post(context: Context, due: List<Item>, today: LocalDate) {
        ensureChannel(context)
        val overdue = due.count { it.dueDate < today.toEpochDay() }
        val title = summary(due.size - overdue, overdue)
        TaskNotifications.post(context, TaskNotifications.digestTag(), CHANNEL, title, due, today, urgent = false)
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
            NotificationChannel(CHANNEL, "Morning digest", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // God tasks first, so the 8 AM digest lists them.
        if (runCatching { GodTasks.ensureToday(applicationContext) }.getOrDefault(false)) {
            runCatching { DueWidget.refresh(applicationContext) }
            runCatching { Backup.backUpSoon(applicationContext) }
        }
        runCatching { Reminders.checkAndNotify(applicationContext) }
        runCatching { SlotReminders.catchUp(applicationContext) }
        return Result.success()
    }
}
