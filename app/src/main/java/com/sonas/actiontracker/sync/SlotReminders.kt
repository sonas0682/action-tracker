package com.sonas.actiontracker.sync

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.widget.DueWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Time-of-day reminders: a heads-up notification per slot (12 PM Morning, 4 PM Afternoon,
 * 7 PM Evening, 10 PM Night by default) listing that slot's open actions due today or overdue.
 *
 * An alarm is set for the next enabled slot and re-armed after each one fires. The 15-minute
 * [ReminderWorker] also calls [catchUp], which re-arms the alarm and posts a slot the alarm
 * missed (phone off, alarm dropped), so nothing depends on the alarm alone. The task list is
 * read when the reminder fires, so actions ticked off earlier never show.
 */
object SlotReminders {
    private const val PREFS = "slot_reminders"
    private const val CHANNEL = "time_reminders"
    private const val NOTIFICATION_BASE = 20
    private const val SNOOZE_BASE = 40
    private const val MAX_LINES = 5
    private const val SNOOZE_MINUTES = 30L
    /** A missed slot is still posted this long after its time, unless a later slot has started. */
    private const val CATCH_UP_HOURS = 2L

    internal const val ACTION_FIRE = "com.sonas.actiontracker.SLOT_FIRE"
    internal const val ACTION_SNOOZE = "com.sonas.actiontracker.SLOT_SNOOZE"
    internal const val ACTION_DONE = "com.sonas.actiontracker.SLOT_DONE"
    internal const val EXTRA_SLOT = "slot"
    internal const val EXTRA_ITEM = "item"

    data class Slot(val slot: TimeOfDay, val enabled: Boolean, val time: LocalTime)

    fun defaultTime(slot: TimeOfDay): LocalTime = when (slot) {
        TimeOfDay.MORNING -> LocalTime.of(12, 0)
        TimeOfDay.AFTERNOON -> LocalTime.of(16, 0)
        TimeOfDay.EVENING -> LocalTime.of(19, 0)
        TimeOfDay.NIGHT -> LocalTime.of(22, 0)
    }

    fun settings(context: Context): List<Slot> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return TimeOfDay.entries.map { slot ->
            val minutes = p.getInt("${slot.name}_minutes", defaultTime(slot).toSecondOfDay() / 60)
            Slot(slot, p.getBoolean("${slot.name}_on", true), LocalTime.of(minutes / 60, minutes % 60))
        }
    }

    fun anyEnabled(context: Context) = settings(context).any { it.enabled }

    fun save(context: Context, slot: Slot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("${slot.slot.name}_on", slot.enabled)
            .putInt("${slot.slot.name}_minutes", slot.time.toSecondOfDay() / 60)
            .apply()
        Reminders.schedule(context)
        schedule(context)
    }

    /** Arms one alarm for the next enabled slot, or clears it when every slot is off. Safe to call often. */
    fun schedule(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val next = nextFire(settings(context), now)
        if (next == null) {
            am.cancel(fireIntent(context, null))
            return
        }
        val (slot, at) = next
        setAlarm(context, at, fireIntent(context, slot))
    }

    /** The next enabled slot strictly after [now], looking up to a day ahead. */
    internal fun nextFire(slots: List<Slot>, now: LocalDateTime): Pair<TimeOfDay, LocalDateTime>? =
        slots.filter { it.enabled }
            .map { s ->
                val today = now.toLocalDate().atTime(s.time)
                s.slot to if (today.isAfter(now)) today else today.plusDays(1)
            }
            .minByOrNull { it.second }

    /**
     * The slot that should have fired most recently today but was missed, if its time was
     * within [CATCH_UP_HOURS] and no later enabled slot has started since.
     */
    internal fun missedSlot(slots: List<Slot>, now: LocalDateTime, firedToday: (TimeOfDay) -> Boolean): TimeOfDay? {
        val latest = slots.filter { it.enabled && !it.time.isAfter(now.toLocalTime()) }
            .maxByOrNull { it.time } ?: return null
        val due = now.toLocalDate().atTime(latest.time)
        if (now.isAfter(due.plusHours(CATCH_UP_HOURS))) return null
        return latest.slot.takeUnless(firedToday)
    }

    /** Called by the periodic worker: re-arms the alarm and posts a slot the alarm missed. */
    suspend fun catchUp(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val slots = settings(context)
        schedule(context, now)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = now.toLocalDate().toEpochDay()
        missedSlot(slots, now) { prefs.getLong("${it.name}_last_day", Long.MIN_VALUE) == today }
            ?.let { fire(context, it, now.toLocalDate(), force = false) }
    }

    /**
     * Posts [slot]'s reminder once per day (any number of times when [force]d, as for a snooze),
     * reading the open actions now so ones done earlier are left out.
     */
    suspend fun fire(context: Context, slot: TimeOfDay, today: LocalDate = LocalDate.now(), force: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${slot.name}_last_day"
        if (!force) {
            if (settings(context).none { it.slot == slot && it.enabled }) return
            if (prefs.getLong(key, Long.MIN_VALUE) == today.toEpochDay()) return
            prefs.edit().putLong(key, today.toEpochDay()).apply()
        }
        val open = Db.get(context).dao().openDueBy(today.toEpochDay()).filter { TimeOfDay.of(it.timeOfDay) == slot }
        if (open.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE + slot.ordinal)
        } else {
            post(context, slot, open, today)
        }
    }

    fun snooze(context: Context, slot: TimeOfDay) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE + slot.ordinal)
        val at = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
        val intent = Intent(context, SlotReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE).putExtra(EXTRA_SLOT, slot.name)
        setAlarm(
            context, at,
            PendingIntent.getBroadcast(context, SNOOZE_BASE + slot.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    suspend fun markDone(context: Context, slot: TimeOfDay, itemId: Long) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE + slot.ordinal)
        Db.get(context).dao().markDone(itemId)
        runCatching { DueWidget.refresh(context) }
    }

    internal fun heading(slot: TimeOfDay, count: Int): String =
        "${slot.emoji} $count ${slot.label.lowercase()} ${if (count == 1) "action" else "actions"} still open"

    private fun post(context: Context, slot: TimeOfDay, open: List<Item>, today: LocalDate) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val id = NOTIFICATION_BASE + slot.ordinal
        val todayEpoch = today.toEpochDay()
        val title = heading(slot, open.size)
        val openApp = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        open.take(MAX_LINES).forEach { item ->
            val who = if (item.person != "Me") " · ${item.person}" else ""
            val late = if (item.dueDate < todayEpoch) "Overdue: " else ""
            style.addLine("$late${item.title}$who")
        }
        if (open.size > MAX_LINES) style.setSummaryText("+${open.size - MAX_LINES} more")

        // The lock screen shows only the count; titles stay hidden until the phone is unlocked.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Action Tracker")
            .setContentText(title)
            .build()

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(open.first().title)
            .setStyle(style)
            .setNumber(open.size)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openApp)
            .setAutoCancel(true)
        // "Done" only when there is exactly one action, so it is clear what it ticks off.
        open.singleOrNull()?.let { item ->
            val done = Intent(context, SlotReminderReceiver::class.java)
                .setAction(ACTION_DONE).putExtra(EXTRA_SLOT, slot.name).putExtra(EXTRA_ITEM, item.id)
            n.addAction(0, "Done", PendingIntent.getBroadcast(context, id, done, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        val snooze = Intent(context, SlotReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE).putExtra(EXTRA_SLOT, slot.name).putExtra("tap", true)
        n.addAction(0, "Snooze 30 min", PendingIntent.getBroadcast(context, id + 100, snooze, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        NotificationManagerCompat.from(context).notify(id, n.build())
    }

    private fun fireIntent(context: Context, slot: TimeOfDay?): PendingIntent {
        // One request code for every slot, so arming the next slot replaces the previous alarm.
        val intent = Intent(context, SlotReminderReceiver::class.java).setAction(ACTION_FIRE)
        if (slot != null) intent.putExtra(EXTRA_SLOT, slot.name)
        return PendingIntent.getBroadcast(context, NOTIFICATION_BASE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setAlarm(context: Context, at: LocalDateTime, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Time-of-day reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pop-up reminders for open Morning, Afternoon, Evening and Night actions"
            }
        )
    }
}

/** Slot alarms, snoozes and the notification's Done button; also re-arms alarms after a reboot or app update. */
class SlotReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val slot = intent.getStringExtra(SlotReminders.EXTRA_SLOT)?.let(TimeOfDay::of)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    SlotReminders.ACTION_FIRE -> {
                        if (slot != null) runCatching { SlotReminders.fire(app, slot, force = false) }
                        SlotReminders.schedule(app)
                    }
                    SlotReminders.ACTION_SNOOZE -> if (slot != null) {
                        // A tap on "Snooze" sets the alarm; the alarm itself re-posts the reminder.
                        if (intent.getBooleanExtra("tap", false)) SlotReminders.snooze(app, slot)
                        else runCatching { SlotReminders.fire(app, slot, force = true) }
                    }
                    SlotReminders.ACTION_DONE -> if (slot != null) {
                        val id = intent.getLongExtra(SlotReminders.EXTRA_ITEM, -1)
                        if (id >= 0) runCatching { SlotReminders.markDone(app, slot, id) }
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                        SlotReminders.schedule(app)
                        Reminders.schedule(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
