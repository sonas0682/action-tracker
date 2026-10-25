package com.sonas.actiontracker.sync

import android.content.Context
import androidx.room.withTransaction
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.Thought
import com.sonas.actiontracker.data.Thoughts
import com.sonas.actiontracker.data.TimeOfDay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

const val SOURCE_THOUGHT = "THOUGHT"

/** The sparkle that marks the Thought of the day in the list, widget and notifications. */
const val THOUGHT_MARK = "✦"

/**
 * Thought of the day as a task: one quote a day from [Thoughts], added to today's Morning on an
 * aurora quote card. Ticking it done means it was read. Yesterday's unread thought isn't carried
 * over: today's replaces it. ⋮ → Thought of the day turns it on (the default) or off; off removes
 * the open one and hides thought rows from the lists.
 */
object ThoughtTasks {
    private const val PREFS = "thought_task"
    const val CONTEXT = "Thought"

    private val lock = Mutex()

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", true)

    /** Saves the switch; off removes the open thought, on adds today's right away. */
    suspend fun setEnabled(context: Context, on: Boolean): Boolean {
        lock.withLock {
            prefs(context).edit().putBoolean("enabled", on).remove("last_day").apply()
            if (!on) Db.get(context).dao().deleteOpenThoughts()
        }
        Reminders.schedule(context)
        return if (on) ensureToday(context) else true
    }

    fun isThought(item: Item): Boolean = item.source == SOURCE_THOUGHT

    /**
     * Once a day: drops an unread thought from an earlier day and adds today's, unless today already
     * has one (open, done or deleted by hand). Returns true when it changed anything. Safe to call often.
     */
    suspend fun ensureToday(context: Context, today: LocalDate = LocalDate.now()): Boolean = lock.withLock {
        if (!enabled(context)) return@withLock false
        val t = today.toEpochDay()
        val prefs = prefs(context)
        if (prefs.getLong("last_day", Long.MIN_VALUE) == t) return@withLock false

        val index = Thoughts.next(prefs.getInt("last_index", -1))
        val db = Db.get(context)
        val changed = db.withTransaction {
            val dao = db.dao()
            val dropped = dao.deleteOpenThoughtsBefore(t)
            val added = if (dao.thoughtCountOn(t) == 0) { dao.insert(item(Thoughts.all[index], t)); true } else false
            dropped > 0 || added
        }
        val edit = prefs.edit().putLong("last_day", t)
        if (changed) edit.putInt("last_index", index)
        edit.apply()
        changed
    }

    /** Today's row for [thought]: the quote is the title, who said it the note. */
    fun item(thought: Thought, epochDay: Long) = Item(
        title = thought.text, notes = thought.source, person = "Me", context = CONTEXT, dueDate = epochDay,
        status = STATUS_OPEN, source = SOURCE_THOUGHT, timeOfDay = TimeOfDay.MORNING.name
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
