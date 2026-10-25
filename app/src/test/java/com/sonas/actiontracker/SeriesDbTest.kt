package com.sonas.actiontracker

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sonas.actiontracker.data.Actions
import com.sonas.actiontracker.data.DB_VERSION
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.STATUS_SKIPPED
import com.sonas.actiontracker.data.Series
import com.sonas.actiontracker.data.SeriesScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Upgrades from real v4/v5 tables through Room's own schema check, and the series rules on a real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SeriesDbTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val file = File(context.cacheDir, "test-${System.nanoTime()}.db")
    private var db: Db? = null
    private val today = LocalDate.now()
    private val t = today.toEpochDay()

    @After fun close() {
        db?.close()
        file.delete()
    }

    private fun open(): Db = Room.databaseBuilder(context, Db::class.java, file.absolutePath)
        .addMigrations(*Db.MIGRATIONS)
        .build()
        .also { db = it }

    /** The tables exactly as an installed app at [version] (4 or 5) has them. */
    private fun oldDatabase(version: Int, rows: List<String>) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            val recurrence = if (version >= 5) ", recurrence TEXT NOT NULL DEFAULT 'NONE'" else ""
            sql.execSQL(
                "CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, " +
                    "person TEXT NOT NULL, context TEXT NOT NULL, dueDate INTEGER NOT NULL, status TEXT NOT NULL, " +
                    "source TEXT NOT NULL, createdAt INTEGER NOT NULL, body TEXT NOT NULL DEFAULT '', completedAt INTEGER, " +
                    "timeOfDay TEXT NOT NULL DEFAULT 'EVENING'$recurrence)"
            )
            sql.execSQL("CREATE TABLE processed (sourceKey TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(sourceKey))")
            rows.forEach { sql.execSQL(it) }
            sql.version = version
        }
    }

    private fun v5Row(id: Int, title: String, due: Long, status: String, recurrence: String) =
        "INSERT INTO items (id, title, notes, person, context, dueDate, status, source, createdAt, recurrence) " +
            "VALUES ($id, '$title', '', 'Me', 'Home', $due, '$status', 'CUSTOM', 1, '$recurrence')"

    private fun weekly(title: String, due: Long = t, endCount: Int? = null, endDate: Long? = null) = Item(
        title = title, dueDate = due, status = STATUS_OPEN, source = "CUSTOM",
        recurrence = Recurrence.WEEKLY.name, endCount = endCount, endDate = endDate
    )

    private fun weeksInWindow(from: LocalDate) =
        Recurrence.WEEKLY.dates(from).takeWhile { !it.isAfter(Series.horizon(today)) }.count()

    @Test fun upgradeFromV5FillsOpenRepeatsOnceAndKeepsHistory() = runBlocking {
        oldDatabase(5, listOf(
            v5Row(1, "Trash", t, STATUS_OPEN, "WEEKLY"),
            v5Row(2, "Trash", t - 7, STATUS_DONE, "WEEKLY"),
            v5Row(3, "Dentist", t + 3, STATUS_OPEN, "NONE")
        ))
        val db = open()
        Series.topUpAll(db, today)
        Series.topUpAll(db, today) // a second run (next app start) adds nothing
        val all = db.dao().allItems()
        val trash = all.filter { it.seriesId == 1L }
        assertEquals(weeksInWindow(today), trash.size)
        assertTrue(trash.all { it.status == STATUS_OPEN && it.title == "Trash" })
        assertEquals(trash.size, trash.map { it.dueDate }.toSet().size)
        val history = all.single { it.id == 2L }
        assertEquals(Recurrence.NONE.name, history.recurrence)
        assertNull(history.seriesId)
        assertNull(all.single { it.id == 3L }.seriesId)
        assertNull(all.single { it.id == 3L }.sourceRef)
        assertEquals(DB_VERSION, db.openHelper.readableDatabase.version)
    }

    @Test fun upgradeFromV4StillWorks() = runBlocking {
        oldDatabase(4, listOf(
            "INSERT INTO items (id, title, notes, person, context, dueDate, status, source, createdAt) " +
                "VALUES (1, 'Old', '', 'Me', 'Work', $t, 'OPEN', 'CUSTOM', 1)"
        ))
        val item = open().dao().allItems().single()
        assertEquals(Recurrence.NONE.name, item.recurrence)
        assertNull(item.seriesId)
    }

    @Test fun endsAfterNTimesAndOnADate() = runBlocking {
        oldDatabase(5, emptyList())
        val db = open()
        Series.create(db, weekly("Piano", endCount = 5), today)
        Series.create(db, weekly("Swim", endDate = t + 15), today)
        Series.topUpAll(db, today)
        val all = db.dao().allItems()
        assertEquals(5, all.count { it.title == "Piano" })
        assertEquals(t + 28, all.filter { it.title == "Piano" }.maxOf { it.dueDate })
        assertEquals(listOf(t, t + 7, t + 14), all.filter { it.title == "Swim" }.map { it.dueDate }.sorted())
    }

    @Test fun doneDoesNotAddAndSkippedDateStaysGone() = runBlocking {
        oldDatabase(5, emptyList())
        val db = open()
        Series.create(db, weekly("Trash"), today)
        val before = db.dao().allItems()
        Actions.complete(db, before.first().id)
        Actions.complete(db, before.first().id)
        assertEquals(before.size, db.dao().allItems().size)

        val second = before.sortedBy { it.dueDate }[1]
        val undo = Series.delete(db, second, SeriesScope.THIS)
        Series.topUpAll(db, today)
        assertEquals(STATUS_SKIPPED, db.dao().byId(second.id)!!.status)
        assertEquals(before.size, db.dao().allItems().size)
        Series.undo(db, undo)
        assertEquals(STATUS_OPEN, db.dao().byId(second.id)!!.status)
    }

    @Test fun deleteFollowingStopsTheSeriesAndUndoBringsItBack() = runBlocking {
        oldDatabase(5, emptyList())
        val db = open()
        Series.create(db, weekly("Trash"), today)
        val before = db.dao().allItems()
        val third = before.sortedBy { it.dueDate }[2]
        val undo = Series.delete(db, third, SeriesScope.FOLLOWING)
        Series.topUpAll(db, today.plusMonths(1))
        assertEquals(listOf(t, t + 7), db.dao().allItems().map { it.dueDate }.sorted())
        Series.undo(db, undo)
        assertEquals(before.map { it.id }.toSet(), db.dao().allItems().map { it.id }.toSet())
    }

    @Test fun deleteAllKeepsDoneHistory() = runBlocking {
        oldDatabase(5, emptyList())
        val db = open()
        Series.create(db, weekly("Trash"), today)
        val first = db.dao().allItems().minBy { it.dueDate }
        Actions.complete(db, first.id)
        Series.delete(db, db.dao().byId(first.id)!!, SeriesScope.ALL)
        Series.topUpAll(db, today)
        val left = db.dao().allItems().single()
        assertEquals(STATUS_DONE, left.status)
        assertNull(left.seriesId)
    }

    @Test fun editFollowingWithANewRepeatReplansFromThere() = runBlocking {
        oldDatabase(5, emptyList())
        val db = open()
        Series.create(db, weekly("Trash"), today)
        val rows = db.dao().allItems().sortedBy { it.dueDate }
        val second = rows[1]
        Series.save(db, second, second.copy(title = "Bins", recurrence = Recurrence.DAILY.name), SeriesScope.FOLLOWING, today)
        val after = db.dao().allItems().sortedBy { it.dueDate }
        assertEquals("Trash", after[0].title)
        assertEquals(t + 6, after[0].endDate)
        val bins = after.filter { it.title == "Bins" }
        assertTrue(bins.all { it.seriesId == second.id })
        assertEquals(bins.size, Recurrence.DAILY.dates(LocalDate.ofEpochDay(t + 7)).takeWhile { !it.isAfter(Series.horizon(today)) }.count())
    }

    @Test fun editThisOneOnlyTouchesThatRow() = runBlocking {
        oldDatabase(5, emptyList())
        val db = open()
        Series.create(db, weekly("Trash"), today)
        val first = db.dao().allItems().minBy { it.dueDate }
        Series.save(db, first, first.copy(title = "Trash + recycling", dueDate = t + 1), SeriesScope.THIS, today)
        val all = db.dao().allItems()
        assertEquals(1, all.count { it.title == "Trash + recycling" })
        assertEquals(t + 1, db.dao().byId(first.id)!!.dueDate)
    }

    @Test fun godTasksAndThoughtsMoveToOneContextEach() = runBlocking {
        oldDatabase(5, emptyList())
        val db = open()
        val dao = db.dao()
        dao.insert(Item(title = "Do 10 pushups", context = "Health", dueDate = t, status = STATUS_OPEN, source = "GOD"))
        dao.insert(Item(title = "Quote", context = "Thought", dueDate = t, status = STATUS_DONE, source = "THOUGHT"))
        dao.insert(Item(title = "Report", context = "Work", dueDate = t, status = STATUS_OPEN, source = "CUSTOM"))
        assertEquals(1, dao.moveGodTasksToOneContext("God task"))
        assertEquals(1, dao.moveThoughtsToOneContext("Thought of the day"))
        // Run again on every start: nothing left to move.
        assertEquals(0, dao.moveGodTasksToOneContext("God task"))
        assertEquals(0, dao.moveThoughtsToOneContext("Thought of the day"))
        val rows = dao.allItems().associateBy { it.title }
        assertEquals("God task", rows.getValue("Do 10 pushups").context)
        assertEquals("HEALTH", rows.getValue("Do 10 pushups").sourceRef)
        assertEquals("Thought of the day", rows.getValue("Quote").context)
        assertEquals("Work", rows.getValue("Report").context)
        assertNull(rows.getValue("Report").sourceRef)
    }
}
