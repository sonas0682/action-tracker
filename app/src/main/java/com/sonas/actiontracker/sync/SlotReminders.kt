package com.sonas.actiontracker.sync

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.TimeOfDay
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
    internal const val CHANNEL = "time_reminders"
    private const val NOTIFICATION_BASE = 20
    /** A missed slot is still posted this long after its time, unless a later slot has started. */
    private const val CATCH_UP_HOURS = 2L

    internal const val ACTION_FIRE = "com.sonas.actiontracker.SLOT_FIRE"
    internal const val EXTRA_SLOT = "slot"

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
            ?.let { fire(context, it, now.toLocalDate()) }
    }

    /**
     * Posts [slot]'s reminder once per day, reading the open actions now so ones done earlier
     * are left out. One notification lists them all ([TaskNotifications]).
     */
    suspend fun fire(context: Context, slot: TimeOfDay, today: LocalDate = LocalDate.now()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${slot.name}_last_day"
        if (settings(context).none { it.slot == slot && it.enabled }) return
        if (prefs.getLong(key, Long.MIN_VALUE) == today.toEpochDay()) return
        prefs.edit().putLong(key, today.toEpochDay()).apply()
        val open = Db.get(context).dao().openDueBy(today.toEpochDay()).filter { TimeOfDay.of(it.timeOfDay) == slot }
        ensureChannel(context)
        TaskNotifications.post(context, TaskNotifications.slotTag(slot.name), CHANNEL, heading(slot, open.size), open, today, urgent = true)
    }

    internal fun heading(slot: TimeOfDay, count: Int): String =
        "${slot.emoji} $count ${slot.label.lowercase()} ${if (count == 1) "action" else "actions"} still open"

    private fun fireIntent(context: Context, slot: TimeOfDay?): PendingIntent {
        // One request code for every slot, so arming the next slot replaces the previous alarm.
        val intent = Intent(context, SlotReminderReceiver::class.java).setAction(ACTION_FIRE)
        if (slot != null) intent.putExtra(EXTRA_SLOT, slot.name)
        return PendingIntent.getBroadcast(context, NOTIFICATION_BASE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    internal fun setAlarm(context: Context, at: LocalDateTime, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
    }

    internal fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Time-of-day reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pop-up reminders for open Morning, Afternoon, Evening and Night actions"
            }
        )
    }
}

/** Slot alarms; also re-arms alarms after a reboot or app update and clears notifications older builds left. */
class SlotReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val slot = intent.getStringExtra(SlotReminders.EXTRA_SLOT)?.let(TimeOfDay::of)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    SlotReminders.ACTION_FIRE -> {
                        if (slot != null) runCatching { SlotReminders.fire(app, slot) }
                        SlotReminders.schedule(app)
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                        SlotReminders.schedule(app)
                        Reminders.schedule(app)
                        TaskNotifications.removeLegacy(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
