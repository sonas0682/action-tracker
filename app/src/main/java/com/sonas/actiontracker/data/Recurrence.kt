package com.sonas.actiontracker.data

import androidx.room.withTransaction
import java.time.DayOfWeek
import java.time.LocalDate

/** How an action repeats; stored by name in [Item.recurrence]. */
enum class Recurrence(val label: String) {
    NONE("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly");

    /**
     * The next due date after an occurrence due on [due] is finished on [today]. It always
     * lands after today, so a long-overdue action skips ahead instead of leaving a pile of
     * past dates. Weekly, monthly and yearly keep the original weekday / day of month
     * (a 31st falls back to the month's last day without drifting). Null for [NONE].
     */
    fun nextDue(due: LocalDate, today: LocalDate): LocalDate? = when (this) {
        NONE -> null
        DAILY -> maxOf(due, today).plusDays(1)
        WEEKDAYS -> {
            var d = maxOf(due, today).plusDays(1)
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
            d
        }
        WEEKLY, MONTHLY, YEARLY -> {
            var n = 1L
            while (!nth(due, n).isAfter(today)) n++
            nth(due, n)
        }
    }

    private fun nth(due: LocalDate, n: Long): LocalDate = when (this) {
        WEEKLY -> due.plusWeeks(n)
        MONTHLY -> due.plusMonths(n)
        else -> due.plusYears(n)
    }

    companion object {
        fun of(name: String): Recurrence = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * Done / undo / skip for actions, kept in one place so the list, the calendar and the
 * widget treat repeating actions the same way. A finished occurrence stays as a done
 * row (calendar and stats stay right) and the series continues as a new open row.
 * That row's createdAt equals the finished one's completedAt, which links the two
 * for undo.
 */
object Actions {
    /** Ticks [id] done and queues the next occurrence of a repeating action. A second tap is a no-op. */
    suspend fun complete(db: Db, id: Long, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val dao = db.dao()
            val item = dao.byId(id)?.takeIf { it.status == STATUS_OPEN } ?: return@withTransaction
            dao.markDone(id, now)
            val following = next(item, now)
            if (following != null) dao.insert(following)
        }
    }

    /** Un-ticks [id]; for a repeating action the occurrence queued by [complete] is removed again. */
    suspend fun reopen(db: Db, id: Long) {
        db.withTransaction {
            val dao = db.dao()
            val item = dao.byId(id)?.takeIf { it.status == STATUS_DONE } ?: return@withTransaction
            dao.update(item.copy(status = STATUS_OPEN, completedAt = null))
            val at = item.completedAt
            if (Recurrence.of(item.recurrence) != Recurrence.NONE && at != null) dao.deleteQueuedOccurrence(item.title, at)
        }
    }

    /**
     * Deletes one occurrence of a repeating open action and queues the next, so the series
     * goes on. Returns the queued occurrence (with its id) for Undo, or null if [item]
     * doesn't repeat, in which case it is simply deleted.
     */
    suspend fun deleteOccurrence(db: Db, item: Item, now: Long = System.currentTimeMillis()): Item? = db.withTransaction {
        val dao = db.dao()
        dao.delete(item)
        if (item.status != STATUS_OPEN) return@withTransaction null
        next(item, now)?.let { it.copy(id = dao.insert(it)) }
    }

    private fun next(item: Item, now: Long): Item? =
        Recurrence.of(item.recurrence).nextDue(LocalDate.ofEpochDay(item.dueDate), LocalDate.now())?.let {
            item.copy(id = 0, dueDate = it.toEpochDay(), status = STATUS_OPEN, completedAt = null, createdAt = now, body = "")
        }
}
