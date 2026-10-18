package com.sonas.actiontracker

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.sync.TaskNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskNotificationsTest {
    private val sat = LocalDate.of(2026, 10, 17)
    private val today = sat.toEpochDay()

    private fun item(id: Long, title: String = "Task $id", due: Long = today, person: String = "Me") =
        Item(id = id, title = title, person = person, dueDate = due, status = STATUS_OPEN, source = "CUSTOM")

    @Test fun manyTasksEndWithAMoreLine() {
        val plan = TaskNotifications.plan((1L..9L).map { item(it) })
        assertEquals((1L..6L).toList(), plan.shown.map { it.id })
        assertEquals(3, plan.more)
    }

    @Test fun nothingLeftOverWhenSixOrFewer() {
        val plan = TaskNotifications.plan((1L..6L).map { item(it) })
        assertEquals(6, plan.shown.size)
        assertEquals(0, plan.more)
    }

    @Test fun lineMarksOverdueAndNamesThePerson() {
        assertEquals("Overdue: Pay fees · Vibu", TaskNotifications.line(item(1, "Pay fees", today - 2, "Vibu"), today))
        assertEquals("Call bank", TaskNotifications.line(item(2, "Call bank"), today))
    }

    @Test fun doneOnlyForASingleTask() {
        assertTrue(TaskNotifications.offersDone(listOf(item(1))))
        assertFalse(TaskNotifications.offersDone(listOf(item(1), item(2))))
    }

    @Test fun snoozedTitleCountsWhatIsStillOpen() {
        assertEquals("Snoozed: 1 action still open", TaskNotifications.snoozedTitle(1))
        assertEquals("Snoozed: 3 actions still open", TaskNotifications.snoozedTitle(3))
    }
}
