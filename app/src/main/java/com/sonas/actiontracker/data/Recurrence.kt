package com.sonas.actiontracker.data

import androidx.room.withTransaction
import java.time.DayOfWeek
import java.time.LocalDate

/** How an action repeats; stored by name in [Item.recurrence]. */
enum class Recurrence(val label: String) {
    NONE("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly");

    /**
     * Every due date of a series that starts on [start], in order. The first is always [start];
     * weekly, monthly and yearly dates are counted from it, so a 31st falls back to a shorter
     * month's last day without drifting.
     */
    fun dates(start: LocalDate): Sequence<LocalDate> = when (this) {
        NONE -> sequenceOf(start)
        DAILY -> generateSequence(start) { it.plusDays(1) }
        WEEKDAYS -> generateSequence(start) { d ->
            var n = d.plusDays(1)
            while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1)
            n
        }
        WEEKLY -> generateSequence(0L) { it + 1 }.map { start.plusWeeks(it) }
        MONTHLY -> generateSequence(0L) { it + 1 }.map { start.plusMonths(it) }
        YEARLY -> generateSequence(0L) { it + 1 }.map { start.plusYears(it) }
    }

    /** Due dates after [after], up to and including [until]. */
    fun datesBetween(start: LocalDate, after: LocalDate, until: LocalDate): List<LocalDate> =
        dates(start).dropWhile { !it.isAfter(after) }.takeWhile { !it.isAfter(until) }.toList()

    /** The date of occurrence number [count] (1 = [start]); the last day of an "Ends after N times" series. */
    fun nthDate(start: LocalDate, count: Int): LocalDate = dates(start).elementAt(maxOf(count, 1) - 1)

    companion object {
        fun of(name: String): Recurrence = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Which occurrences an edit or delete of a repeating action applies to. */
enum class SeriesScope(val label: String) { THIS("This one"), FOLLOWING("This and following"), ALL("All") }

/**
 * Everything a scoped edit or delete changed, so Undo can put it back: [rows] as they were,
 * and any series it started ([created]), whose rows Undo removes.
 */
data class Snapshot(val rows: List<Item>, val created: Set<Long> = emptySet())

/**
 * Repeating actions are stored as real rows, one per occurrence, sharing a [Item.seriesId].
 * Rows are kept [WINDOW_MONTHS] months ahead ([topUpAll] runs at app start and daily), so the
 * list, calendar, stats, widget, reminders and backup all see them like any other action.
 * Ticking one done doesn't create anything; the window does.
 */
object Series {
    const val WINDOW_MONTHS = 3L

    /** How far ahead a series is filled. */
    fun horizon(today: LocalDate): LocalDate = today.plusMonths(WINDOW_MONTHS)

    /** Fills [item]'s end date from "after N times", or clears the end rule for a one-off. */
    fun withEnd(item: Item): Item {
        val rule = Recurrence.of(item.recurrence)
        if (rule == Recurrence.NONE) return item.copy(seriesId = null, seriesStart = null, endDate = null, endCount = null)
        val start = LocalDate.ofEpochDay(item.seriesStart ?: item.dueDate)
        val count = item.endCount
        return if (count != null) item.copy(endDate = rule.nthDate(start, count).toEpochDay()) else item
    }

    /** Saves a new action; a repeating one becomes a series and is filled ahead. */
    suspend fun create(db: Db, item: Item, today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val dao = db.dao()
            if (Recurrence.of(item.recurrence) == Recurrence.NONE) {
                dao.insert(withEnd(item))
                return@withTransaction
            }
            val first = withEnd(item.copy(id = 0, seriesId = null, seriesStart = item.dueDate))
            val id = dao.insert(first)
            dao.update(first.copy(id = id, seriesId = id))
            topUp(db, id, today)
        }
    }

    /** Adds the missing occurrences of every series up to the window's end. Safe to run any number of times. */
    suspend fun topUpAll(db: Db, today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val dao = db.dao()
            dao.adoptLegacySeries()
            dao.clearLooseRecurrence()
            dao.seriesIds().forEach { topUp(db, it, today) }
        }
    }

    /** Adds the series' dates after its last row (skipped ones count) up to the window or its end. */
    private suspend fun topUp(db: Db, seriesId: Long, today: LocalDate) {
        val dao = db.dao()
        val rows = dao.series(seriesId)
        val template = rows.lastOrNull() ?: return
        val rule = Recurrence.of(template.recurrence)
        if (rule == Recurrence.NONE) return
        val start = LocalDate.ofEpochDay(template.seriesStart ?: rows.first().dueDate)
        val last = LocalDate.ofEpochDay(template.dueDate)
        var until = horizon(today)
        template.endDate?.let { if (it < until.toEpochDay()) until = LocalDate.ofEpochDay(it) }
        val now = System.currentTimeMillis()
        rule.datesBetween(start, last, until).forEach { d ->
            dao.insert(
                template.copy(id = 0, dueDate = d.toEpochDay(), status = STATUS_OPEN, completedAt = null, createdAt = now, body = "")
            )
        }
    }

    /**
     * Saves an edit of [original] as [edited]. One-off actions are simply updated (and become a
     * series if a repeat was picked). For a series row, [scope] decides which occurrences change:
     * title, person, context, time and notes go to those rows; a new repeat, end or due date
     * re-plans the series from this occurrence on ([SeriesScope.THIS] only moves this one).
     * Returns what Undo needs, or null when there is nothing to undo.
     */
    suspend fun save(db: Db, original: Item, edited: Item, scope: SeriesScope, today: LocalDate = LocalDate.now()): Snapshot? =
        db.withTransaction {
            val dao = db.dao()
            val seriesId = original.seriesId
            if (original.id == 0L) {
                create(db, edited, today)
                return@withTransaction null
            }
            if (seriesId == null) {
                // A suggestion keeps its repeat rule and becomes a series once confirmed.
                if (edited.status != STATUS_OPEN || Recurrence.of(edited.recurrence) == Recurrence.NONE) {
                    dao.update(withEnd(edited))
                } else {
                    dao.update(withEnd(edited.copy(seriesId = edited.id, seriesStart = edited.dueDate)))
                    topUp(db, edited.id, today)
                }
                return@withTransaction null
            }
            val rows = dao.series(seriesId)
            val snapshot = Snapshot(rows)
            fun describe(row: Item) = row.copy(
                title = edited.title, person = edited.person, context = edited.context,
                timeOfDay = edited.timeOfDay, notes = edited.notes
            )
            if (scope == SeriesScope.THIS) {
                dao.update(describe(original).copy(dueDate = edited.dueDate))
                return@withTransaction snapshot
            }
            val d = original.dueDate
            rows.filter { scope == SeriesScope.ALL || (it.dueDate >= d && it.status != STATUS_DONE) }
                .filter { it.id != original.id }
                .forEach { dao.update(describe(it)) }
            if (!scheduleChanged(original, edited)) {
                dao.update(describe(original))
                return@withTransaction snapshot
            }
            // New plan from this occurrence on: the old series stops the day before it. The new
            // series is keyed by this row's id; if that was the old series' key too, its other
            // rows are left as plain one-off history so the two never mix.
            val reusesKey = original.id == seriesId
            rows.filter { it.id != original.id }.forEach { row ->
                val base = if (scope == SeriesScope.ALL) describe(row) else row
                when {
                    row.dueDate >= d && row.status != STATUS_DONE -> dao.delete(row)
                    reusesKey -> dao.update(
                        base.copy(recurrence = Recurrence.NONE.name, seriesId = null, seriesStart = null, endDate = null, endCount = null)
                    )
                    else -> dao.update(base.copy(endDate = d - 1, endCount = null))
                }
            }
            if (Recurrence.of(edited.recurrence) == Recurrence.NONE) {
                dao.update(withEnd(edited))
                return@withTransaction snapshot
            }
            val restarted = withEnd(edited.copy(seriesId = original.id, seriesStart = edited.dueDate))
            dao.update(restarted)
            topUp(db, original.id, today)
            snapshot.copy(created = setOf(original.id))
        }

    /** Deletes [item]; for a series row, [scope] decides which occurrences go. Returns what Undo needs. */
    suspend fun delete(db: Db, item: Item, scope: SeriesScope): Snapshot = db.withTransaction {
        val dao = db.dao()
        val seriesId = item.seriesId
        if (seriesId == null) {
            dao.delete(item)
            return@withTransaction Snapshot(listOf(item))
        }
        val rows = dao.series(seriesId)
        when (scope) {
            // Kept as a hidden row so the window doesn't fill that date in again.
            SeriesScope.THIS -> dao.update(item.copy(status = STATUS_SKIPPED))
            SeriesScope.FOLLOWING -> rows.forEach { row ->
                if (row.dueDate >= item.dueDate && row.status != STATUS_DONE) dao.delete(row)
                else dao.update(row.copy(endDate = item.dueDate - 1, endCount = null))
            }
            // Done occurrences stay as plain history so stats keep them.
            SeriesScope.ALL -> rows.forEach { row ->
                if (row.status == STATUS_DONE) {
                    dao.update(row.copy(recurrence = Recurrence.NONE.name, seriesId = null, seriesStart = null, endDate = null, endCount = null))
                } else {
                    dao.delete(row)
                }
            }
        }
        Snapshot(rows)
    }

    /** Puts back everything a scoped edit or delete changed. */
    suspend fun undo(db: Db, snapshot: Snapshot) {
        db.withTransaction {
            val dao = db.dao()
            dao.deleteSeries(snapshot.created + snapshot.rows.mapNotNull { it.seriesId })
            dao.upsert(snapshot.rows)
        }
    }

    /** A repeat, end or due-date change: the dates themselves move, not just the wording. */
    fun scheduleChanged(original: Item, edited: Item): Boolean =
        original.recurrence != edited.recurrence || original.dueDate != edited.dueDate ||
            original.endCount != edited.endCount || (edited.endCount == null && original.endDate != edited.endDate)
}

/** Ticking done and back, shared by the list, the calendar, the widget and the reminder's Done button. */
object Actions {
    /** Ticks [id] done. A second tap is a no-op. */
    suspend fun complete(db: Db, id: Long, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val dao = db.dao()
            if (dao.byId(id)?.status == STATUS_OPEN) dao.markDone(id, now)
        }
    }

    suspend fun reopen(db: Db, id: Long) {
        db.withTransaction {
            val dao = db.dao()
            val item = dao.byId(id)?.takeIf { it.status == STATUS_DONE } ?: return@withTransaction
            dao.update(item.copy(status = STATUS_OPEN, completedAt = null))
        }
    }
}
