package com.sonas.actiontracker

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.TaskNotifications
import com.sonas.actiontracker.sync.VoiceDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskNotificationsTest {
    private val sat = LocalDate.of(2026, 10, 17)
    private val today = sat.toEpochDay()

    private fun item(id: Long, title: String = "Task $id", due: Long = today, person: String = "Me") =
        Item(id = id, title = title, person = person, dueDate = due, status = STATUS_OPEN, source = "CUSTOM")

    @Test fun upToFiveTasksGetTheirOwnNotification() {
        val plan = TaskNotifications.plan((1L..7L).map { item(it) })
        assertEquals((1L..5L).toList(), plan.shown.map { it.id })
        assertEquals(2, plan.more)
    }

    @Test fun nothingLeftOverWhenFiveOrFewer() {
        val plan = TaskNotifications.plan((1L..3L).map { item(it) })
        assertEquals(3, plan.shown.size)
        assertEquals(0, plan.more)
    }

    @Test fun summaryLineMarksOverdueAndNamesThePerson() {
        assertEquals("Overdue: Pay fees · Vibu", TaskNotifications.line(item(1, "Pay fees", today - 2, "Vibu"), today))
        assertEquals("Call bank", TaskNotifications.line(item(2, "Call bank"), today))
    }

    @Test fun taskDetail() {
        assertEquals("Overdue · Nathiya", TaskNotifications.detail(item(1, due = today - 1, person = "Nathiya"), today))
        assertEquals("Due today", TaskNotifications.detail(item(2), today))
    }

    @Test fun watchReplyUsesTheVoiceRules() {
        val people = VoiceDraft.recentValues(emptyList(), VoiceDraft.DEFAULT_PEOPLE) { it.person }
        val contexts = VoiceDraft.recentValues(emptyList(), VoiceDraft.DEFAULT_CONTEXTS) { it.context }

        val plain = VoiceDraft.fromRules("remind me to buy milk", people, contexts, sat)
        assertEquals("Buy milk", plain.title)
        assertEquals(sat.plusDays(1).toEpochDay(), plain.dueDate) // default: tomorrow
        assertEquals("VOICE", plain.source)
        assertEquals(TimeOfDay.defaultFor(plain.context).name, plain.timeOfDay)

        val vibu = VoiceDraft.fromRules("sign Vibu's permission slip every Monday", people, contexts, sat)
        assertEquals("Vibu", vibu.person)
        assertEquals("WEEKLY", vibu.recurrence)
    }

    @Test fun replyFeedback() {
        val added = item(1, "Buy milk", today + 1)
        assertEquals("Added: Buy milk · Tomorrow", TaskNotifications.addedFeedback(added, today))
        val weekly = added.copy(person = "Vibu", recurrence = "WEEKLY")
        assertEquals("Added: Buy milk · Tomorrow · Vibu · repeats", TaskNotifications.addedFeedback(weekly, today))
    }
}
