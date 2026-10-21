package com.sonas.actiontracker.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.data.Actions
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.widget.DueWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reminder notifications: one notification per reminder [tag] (the morning digest, each time of
 * day) listing its tasks, never one notification per task. Android has no per-line checkboxes,
 * so a reminder with exactly one task offers Done; with more, Snooze 30 min and a tap to open the
 * app. Phone notifications mirror to a paired watch with the same buttons.
 */
object TaskNotifications {
    /** Lines shown before the "+N more" line; InboxStyle shows at most about seven. */
    const val MAX_LINES = 6
    private const val SNOOZE_MINUTES = 30L
    private const val ID = 0
    private const val TAG_PREFIX = "task:"

    internal const val ACTION_DONE = "com.sonas.actiontracker.TASK_DONE"
    internal const val ACTION_SNOOZE = "com.sonas.actiontracker.TASK_SNOOZE"
    internal const val ACTION_SNOOZE_FIRE = "com.sonas.actiontracker.TASK_SNOOZE_FIRE"
    internal const val EXTRA_ITEM = "item"
    internal const val EXTRA_ITEMS = "items"
    internal const val EXTRA_TAG = "tag"
    internal const val EXTRA_CHANNEL = "channel"

    fun digestTag() = "${TAG_PREFIX}digest"
    fun slotTag(slot: String) = "${TAG_PREFIX}slot:$slot"

    // ------------------------------------------------------------------ text (pure, unit-tested)

    data class Plan(val shown: List<Item>, val more: Int)

    /** The tasks listed as lines, and how many are left for the "+N more" line. */
    fun plan(items: List<Item>, max: Int = MAX_LINES) = Plan(items.take(max), maxOf(0, items.size - max))

    /** One line of the list: "Overdue: Pay fees · Vibu". */
    fun line(item: Item, todayEpoch: Long): String {
        val who = if (item.person != "Me") " · ${item.person}" else ""
        val late = if (item.dueDate < todayEpoch) "Overdue: " else ""
        val lamp = if (GodTasks.isGod(item)) "$GOD_LAMP " else ""
        return "$late$lamp${item.title}$who"
    }

    /** Done is offered only when the reminder lists a single task, so a tap can't tick the wrong one. */
    fun offersDone(items: List<Item>) = items.size == 1

    // ------------------------------------------------------------------ reminders

    /**
     * Replaces [tag]'s notification with one titled [title] listing [items] (up to [MAX_LINES],
     * then "+N more"). The channel must exist. Posts nothing when [items] is empty.
     */
    fun post(context: Context, tag: String, channel: String, title: String, items: List<Item>, today: LocalDate, urgent: Boolean) {
        if (!canPost(context)) return
        cancelTag(context, tag)
        if (items.isEmpty()) return
        val todayEpoch = today.toEpochDay()
        val plan = plan(items)
        val ids = items.map { it.id }.toLongArray()

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        plan.shown.forEach { style.addLine(line(it, todayEpoch)) }
        if (plan.more > 0) style.addLine("+${plan.more} more")
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(plan.shown.joinToString(", ") { it.title })
            .setStyle(style)
            .setNumber(items.size)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(locked(context, channel, title))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
        if (offersDone(items)) n.addAction(0, "Done", action(context, ACTION_DONE, tag, ids, channel))
        n.addAction(0, "Snooze 30 min", action(context, ACTION_SNOOZE, tag, ids, channel))
        NotificationManagerCompat.from(context).notify(tag, ID, n.build())
    }

    fun cancelTag(context: Context, tag: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching { nm.activeNotifications }.getOrDefault(emptyArray())
            .filter { it.tag == tag }
            .forEach { nm.cancel(it.tag, it.id) }
    }

    /**
     * Clears what older builds left behind: the per-task notifications and the ongoing
     * "Add a task" notification, its channel and its saved on/off. Safe to call on every launch.
     */
    fun removeLegacy(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(LEGACY_QUICK_ADD_ID)
        runCatching { nm.deleteNotificationChannel(LEGACY_QUICK_ADD_CHANNEL) }
        runCatching { nm.activeNotifications }.getOrDefault(emptyArray())
            .filter { it.tag?.startsWith(TAG_PREFIX) == true && it.id != ID }
            .forEach { nm.cancel(it.tag, it.id) }
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().remove("quick_add").apply()
    }

    private const val LEGACY_QUICK_ADD_ID = 30
    private const val LEGACY_QUICK_ADD_CHANNEL = "quick_add"

    /** The lock screen shows only this; titles stay hidden until the phone is unlocked. */
    private fun locked(context: Context, channel: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Action Tracker")
            .setContentText(text)
            .build()

    private fun action(context: Context, action: String, tag: String, ids: LongArray, channel: String): PendingIntent {
        // The data URI keeps one PendingIntent per reminder and button.
        val intent = Intent(context, TaskActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("actiontracker://reminder/$action/$tag"))
            .putExtra(EXTRA_TAG, tag)
            .putExtra(EXTRA_ITEMS, ids)
            .putExtra(EXTRA_CHANNEL, channel)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // ------------------------------------------------------------------ Done / Snooze

    /** Ticks the reminder's single task done (a repeating occurrence just completes) and clears it. */
    suspend fun done(context: Context, tag: String, id: Long) {
        Actions.complete(Db.get(context), id)
        cancelTag(context, tag)
        afterWrite(context)
    }

    /** Hides the reminder now and shows it again in [SNOOZE_MINUTES] with whichever of its tasks are still open. */
    fun snooze(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(EXTRA_TAG) ?: return
        cancelTag(context, tag)
        val fire = Intent(intent).setClass(context, TaskActionReceiver::class.java)
            .setAction(ACTION_SNOOZE_FIRE)
            .setData(Uri.parse("actiontracker://snooze/$tag"))
        SlotReminders.setAlarm(
            context, LocalDateTime.now().plusMinutes(SNOOZE_MINUTES),
            PendingIntent.getBroadcast(context, 0, fire, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    suspend fun snoozeOver(context: Context, intent: Intent) {
        // A snooze armed by an older build carries one task id and no tag.
        val ids = intent.getLongArrayExtra(EXTRA_ITEMS)
            ?: intent.getLongExtra(EXTRA_ITEM, -1).takeIf { it > 0 }?.let { longArrayOf(it) }
            ?: return
        val dao = Db.get(context).dao()
        val open = ids.toList().mapNotNull { dao.byId(it)?.takeIf { item -> item.status == STATUS_OPEN } }
        SlotReminders.ensureChannel(context)
        val tag = intent.getStringExtra(EXTRA_TAG) ?: "${TAG_PREFIX}snoozed"
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: SlotReminders.CHANNEL
        post(context, tag, channel, snoozedTitle(open.size), open, LocalDate.now(), urgent = true)
    }

    /** The title of a reminder back from snooze, counting only the tasks still open: "Snoozed: 2 actions still open". */
    internal fun snoozedTitle(stillOpen: Int): String =
        "Snoozed: $stillOpen ${if (stillOpen == 1) "action" else "actions"} still open"

    // ------------------------------------------------------------------ helpers

    private suspend fun afterWrite(context: Context) {
        runCatching { DueWidget.refresh(context) }
        runCatching { Backup.backUpSoon(context) }
    }

    private fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Done and Snooze from a reminder notification, on the phone or a watch. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    when (intent.action) {
                        TaskNotifications.ACTION_DONE -> {
                            val tag = intent.getStringExtra(TaskNotifications.EXTRA_TAG)
                            val ids = intent.getLongArrayExtra(TaskNotifications.EXTRA_ITEMS)
                            if (tag != null && ids?.size == 1) TaskNotifications.done(app, tag, ids[0])
                        }
                        TaskNotifications.ACTION_SNOOZE -> TaskNotifications.snooze(app, intent)
                        TaskNotifications.ACTION_SNOOZE_FIRE -> TaskNotifications.snoozeOver(app, intent)
                    }
                    Unit
                }
            } finally {
                pending.finish()
            }
        }
    }
}
