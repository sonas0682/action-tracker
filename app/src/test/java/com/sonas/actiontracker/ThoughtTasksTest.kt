package com.sonas.actiontracker

import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.Thoughts
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.SOURCE_THOUGHT
import com.sonas.actiontracker.sync.ThoughtTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ThoughtTasksTest {
    @Test fun thoughtBecomesAnOpenMorningTaskForTheDay() {
        val thought = Thoughts.all.first()
        val day = LocalDate.of(2026, 10, 25).toEpochDay()
        val item = ThoughtTasks.item(thought, day)
        assertEquals(thought.text, item.title)
        assertEquals(thought.source, item.notes)
        assertEquals(day, item.dueDate)
        assertEquals(STATUS_OPEN, item.status)
        assertEquals(SOURCE_THOUGHT, item.source)
        assertEquals(TimeOfDay.MORNING.name, item.timeOfDay)
        assertEquals("Me", item.person)
        assertTrue(ThoughtTasks.isThought(item))
    }

    @Test fun otherTasksAreNotThoughts() {
        val item = ThoughtTasks.item(Thoughts.all.first(), 0).copy(source = "CUSTOM")
        assertTrue(!ThoughtTasks.isThought(item))
    }
}
