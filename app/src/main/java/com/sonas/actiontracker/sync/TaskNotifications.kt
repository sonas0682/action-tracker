package com.sonas.actiontracker.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.data.Actions
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.Series
import com.sonas.actiontracker.widget.DueWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Reminder notifications built so a paired watch can act on them: phone notifications mirror to
 * the watch with their buttons, so each task gets its own notification with Done and Snooze, and
 * an "Add a task" notification takes a dictated reply and turns it into an action.
 *
 * A reminder is a group per [tag]: a summary that alerts once (titles, "+N more") and up to
 * [MAX_TASKS] silent child notifications, one per task, keyed by the task's id.
 */
object TaskNotifications {
    const val MAX_TASKS = 5
    private const val SNOOZE_MINUTES = 30L
    private const val SUMMARY_ID = 0
    private const val TAG_PREFIX = "task:"
    private const val TAG_SNOOZED = "task:snoozed"
    private const val QUICK_ADD_CHANNEL = "quick_add"
    private const val QUICK_ADD_ID = 30
    private const val FEEDBACK_MILLIS = 5_000L
    private const val PREFS = "reminders"

    internal const val ACTION_DONE = "com.sonas.actiontracker.TASK_DONE"
    internal const val ACTION_SNOOZE = "com.sonas.actiontracker.TASK_SNOOZE"
    internal const val ACTION_SNOOZE_FIRE = "com.sonas.actiontracker.TASK_SNOOZE_FIRE"
    internal const val ACTION_QUICK_ADD = "com.sonas.actiontracker.QUICK_ADD"
    internal const val EXTRA_ITEM = "item"
    internal const val KEY_TEXT = "task_text"

    fun digestTag() = "${TAG_PREFIX}digest"
    fun slotTag(slot: String) = "${TAG_PREFIX}slot:$slot"

    // ------------------------------------------------------------------ text (pure, unit-tested)

    data class Plan(val shown: List<Item>, val more: Int)

    /** The tasks that get their own notification, and how many are left for the "+N more" line. */
    fun plan(items: List<Item>, max: Int = MAX_TASKS) = Plan(items.take(max), maxOf(0, items.size - max))

    /** One line of the summary list: "Overdue: Pay fees · Vibu". */
    fun line(item: Item, todayEpoch: Long): String {
        val who = if (item.person != "Me") " · ${item.person}" else ""
        val late = if (item.dueDate < todayEpoch) "Overdue: " else ""
        return "$late${item.title}$who"
    }

    /** A task notification's second line: "Overdue · Vibu", "Due today". */
    fun detail(item: Item, todayEpoch: Long): String {
        val due = when {
            item.dueDate < todayEpoch -> "Overdue"
            item.dueDate == todayEpoch -> "Due today"
            else -> "Due ${dayLabel(item.dueDate, todayEpoch)}"
        }
        return if (item.person != "Me") "$due · ${item.person}" else due
    }

    /** What the "Add a task" notification says after a reply: "Added: Call the school · Tomorrow · Vibu". */
    fun addedFeedback(item: Item, todayEpoch: Long): String {
        val who = if (item.person != "Me") " · ${item.person}" else ""
        val repeat = if (item.recurrence != "NONE") " · repeats" else ""
        return "Added: ${item.title} · ${dayLabel(item.dueDate, todayEpoch)}$who$repeat"
    }

    internal fun dayLabel(epochDay: Long, todayEpoch: Long): String = when (epochDay - todayEpoch) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }

    // ------------------------------------------------------------------ reminder groups

    /**
     * Replaces [tag]'s notifications with a summary titled [title] plus one notification per
     * task (up to [MAX_TASKS]), each with Done and Snooze 30 min. The channel must exist.
     */
    fun postGroup(context: Context, tag: String, channel: String, title: String, items: List<Item>, today: LocalDate, urgent: Boolean) {
        if (!canPost(context)) return
        cancelTag(context, tag)
        if (items.isEmpty()) return
        val todayEpoch = today.toEpochDay()
        val plan = plan(items)
        val nm = NotificationManagerCompat.from(context)

        plan.shown.forEach { item ->
            nm.notify(tag, item.id.toInt(), task(context, channel, tag, item, todayEpoch, urgent, grouped = true))
        }

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        plan.shown.forEach { style.addLine(line(it, todayEpoch)) }
        if (plan.more > 0) style.setSummaryText("+${plan.more} more")
        val summary = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(items.first().title)
            .setStyle(style)
            .setNumber(items.size)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(locked(context, channel, title))
            .setGroup(tag)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        nm.notify(tag, SUMMARY_ID, summary)
    }

    fun cancelTag(context: Context, tag: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching { nm.activeNotifications }.getOrDefault(emptyArray())
            .filter { it.tag == tag }
            .forEach { nm.cancel(it.tag, it.id) }
    }

    private fun task(
        context: Context, channel: String, tag: String, item: Item, todayEpoch: Long, urgent: Boolean, grouped: Boolean
    ): Notification {
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(item.title)
            .setContentText(detail(item, todayEpoch))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(locked(context, channel, "Action reminder"))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .addAction(0, "Done", taskAction(context, ACTION_DONE, tag, item.id))
            .addAction(0, "Snooze 30 min", taskAction(context, ACTION_SNOOZE, tag, item.id))
        if (grouped) n.setGroup(tag).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        return n.build()
    }

    /** The lock screen shows only this; titles stay hidden until the phone is unlocked. */
    private fun locked(context: Context, channel: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Action Tracker")
            .setContentText(text)
            .build()

    private fun taskAction(context: Context, action: String, tag: String, id: Long): PendingIntent {
        // The data URI keeps one PendingIntent per task and button.
        val intent = Intent(context, TaskActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("actiontracker://task/$action/$tag/$id"))
            .putExtra(EXTRA_ITEM, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // ------------------------------------------------------------------ Done / Snooze

    /** Ticks the task done (a repeating occurrence just completes; later ones already exist) and clears its notifications. */
    suspend fun done(context: Context, id: Long) {
        Actions.complete(Db.get(context), id)
        clearTask(context, id)
        afterWrite(context)
    }

    /** Hides the task now and shows it again, on its own, in [SNOOZE_MINUTES] if it is still open. */
    fun snooze(context: Context, id: Long) {
        clearTask(context, id)
        val intent = Intent(context, TaskActionReceiver::class.java)
            .setAction(ACTION_SNOOZE_FIRE)
            .setData(Uri.parse("actiontracker://snooze/$id"))
            .putExtra(EXTRA_ITEM, id)
        SlotReminders.setAlarm(
            context, LocalDateTime.now().plusMinutes(SNOOZE_MINUTES),
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    suspend fun snoozeOver(context: Context, id: Long) {
        val item = Db.get(context).dao().byId(id)?.takeIf { it.status == STATUS_OPEN } ?: return
        if (!canPost(context)) return
        SlotReminders.ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            TAG_SNOOZED, id.toInt(),
            task(context, SlotReminders.CHANNEL, TAG_SNOOZED, item, LocalDate.now().toEpochDay(), urgent = true, grouped = false)
        )
    }

    /** Removes the task from every reminder, and a reminder's summary once none of its tasks are left. */
    private fun clearTask(context: Context, id: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val active = runCatching { nm.activeNotifications }.getOrDefault(emptyArray())
            .filter { it.tag?.startsWith(TAG_PREFIX) == true }
        val hit = active.filter { it.id == id.toInt() && it.id != SUMMARY_ID }
        hit.forEach { nm.cancel(it.tag, it.id) }
        hit.map { it.tag }.distinct().forEach { tag ->
            val left = active.any { it.tag == tag && it.id != SUMMARY_ID && it.id != id.toInt() }
            if (!left) nm.cancel(tag, SUMMARY_ID)
        }
    }

    // ------------------------------------------------------------------ Add a task (reply box)

    fun quickAddEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("quick_add", true)

    fun setQuickAdd(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("quick_add", on).apply()
        if (on) showQuickAdd(context) else NotificationManagerCompat.from(context).cancel(QUICK_ADD_ID)
    }

    /**
     * The silent, ongoing "Add a task" notification. Its reply box takes a typed or dictated
     * sentence (on the phone or a paired watch); [feedback] briefly replaces the hint after a reply.
     */
    fun showQuickAdd(context: Context, feedback: String? = null) {
        if (!quickAddEnabled(context) || !canPost(context)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(QUICK_ADD_CHANNEL, "Add a task by voice", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Always-there notification with a reply box that adds a task"
                setShowBadge(false)
            }
        )
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val reply = PendingIntent.getBroadcast(
            context, QUICK_ADD_ID,
            Intent(context, TaskActionReceiver::class.java).setAction(ACTION_QUICK_ADD),
            mutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val add = NotificationCompat.Action.Builder(0, "Add task", reply)
            .addRemoteInput(RemoteInput.Builder(KEY_TEXT).setLabel("Say the task").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(false)
            .setShowsUserInterface(false)
            .build()
        val n = NotificationCompat.Builder(context, QUICK_ADD_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("Add a task")
            .setContentText(feedback ?: "Tap Add task and say it, like \"Pay school fees by Friday\"")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(locked(context, QUICK_ADD_CHANNEL, "Add a task"))
            .setContentIntent(openApp(context))
            .addAction(add)
            .build()
        NotificationManagerCompat.from(context).notify(QUICK_ADD_ID, n)
    }

    /** Turns a reply into a task with the voice-capture rules (no Gemini Nano: this runs in the background). */
    suspend fun quickAdd(context: Context, text: String, today: LocalDate = LocalDate.now()): Item? {
        if (text.isBlank()) return null
        val db = Db.get(context)
        val all = db.dao().allItems()
        val item = VoiceDraft.fromRules(
            text,
            VoiceDraft.recentValues(all, VoiceDraft.DEFAULT_PEOPLE) { it.person },
            VoiceDraft.recentValues(all, VoiceDraft.DEFAULT_CONTEXTS) { it.context },
            today
        )
        Series.create(db, item, today)
        afterWrite(context)
        return item
    }

    internal suspend fun replied(context: Context, text: String) {
        val item = runCatching { quickAdd(context, text) }.getOrNull()
        val today = LocalDate.now().toEpochDay()
        // Re-posting also clears the reply spinner on the phone and watch.
        showQuickAdd(context, if (item != null) addedFeedback(item, today) else "Couldn't add that. Try again.")
        delay(FEEDBACK_MILLIS)
        showQuickAdd(context)
    }

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

/** Done, Snooze and the "Add task" reply from any reminder notification, on the phone or a watch. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val id = intent.getLongExtra(TaskNotifications.EXTRA_ITEM, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    when (intent.action) {
                        TaskNotifications.ACTION_DONE -> if (id > 0) TaskNotifications.done(app, id)
                        TaskNotifications.ACTION_SNOOZE -> if (id > 0) TaskNotifications.snooze(app, id)
                        TaskNotifications.ACTION_SNOOZE_FIRE -> if (id > 0) TaskNotifications.snoozeOver(app, id)
                        TaskNotifications.ACTION_QUICK_ADD -> {
                            val text = RemoteInput.getResultsFromIntent(intent)
                                ?.getCharSequence(TaskNotifications.KEY_TEXT)?.toString().orEmpty()
                            TaskNotifications.replied(app, text)
                        }
                    }
                    Unit
                }
            } finally {
                pending.finish()
            }
        }
    }
}
