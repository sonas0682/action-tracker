package com.sonas.actiontracker

import com.sonas.actiontracker.data.GodTheme
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.sync.GodTasks
import com.sonas.actiontracker.sync.SOURCE_GOD
import com.sonas.actiontracker.sync.TaskNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class GodTasksTest {
    private val wed = LocalDate.of(2026, 10, 21)
    private val sat = LocalDate.of(2026, 10, 24)

    @Test fun everyThemeHasThirtyDistinctTasks() {
        GodTheme.entries.forEach { theme ->
            assertEquals(theme.label, 30, theme.tasks.size)
            assertEquals(theme.label, 30, theme.tasks.toSet().size)
        }
        assertTrue(GodTheme.FAMILY.tasks.all { "{name}" in it })
        assertTrue(GodTheme.entries.filter { it != GodTheme.FAMILY }.all { t -> t.tasks.none { "{" in it } })
    }

    @Test fun oneOnWeekdaysThreeOnWeekends() {
        assertEquals(1, GodTasks.countFor(wed))
        assertEquals(3, GodTasks.countFor(sat))
        assertEquals(3, GodTasks.countFor(sat.plusDays(1)))
        assertEquals(1, GodTasks.plan(wed, GodTasks.RANDOM, 0, emptyMap(), 0, Random(1)).size)
        assertEquals(3, GodTasks.plan(sat, GodTasks.RANDOM, 0, emptyMap(), 0, Random(1)).size)
    }

    @Test fun carriedOverTasksCountTowardTheDay() {
        assertTrue(GodTasks.plan(wed, GodTasks.RANDOM, 1, emptyMap(), 0, Random(1)).isEmpty())
        assertEquals(1, GodTasks.plan(sat, GodTasks.RANDOM, 2, emptyMap(), 0, Random(1)).size)
        // A weekend's three left undone fill Monday; nothing new is added.
        assertTrue(GodTasks.plan(sat.plusDays(2), GodTasks.RANDOM, 3, emptyMap(), 0, Random(1)).isEmpty())
    }

    @Test fun offAddsNothing() = assertTrue(GodTasks.plan(sat, GodTasks.OFF, 0, emptyMap(), 0, Random(1)).isEmpty())

    @Test fun randomWeekendUsesThreeThemesAndFixedThemeIsKept() {
        val random = GodTasks.plan(sat, GodTasks.RANDOM, 0, emptyMap(), 0, Random(7))
        assertEquals(3, random.map { it.theme }.toSet().size)
        val health = GodTasks.plan(sat, GodTheme.HEALTH.name, 0, emptyMap(), 0, Random(7))
        assertTrue(health.all { it.theme == GodTheme.HEALTH })
        assertEquals(3, health.map { it.index }.toSet().size)
    }

    @Test fun familyRotatesThroughTheFamily() {
        val picks = GodTasks.plan(sat, GodTheme.FAMILY.name, 0, emptyMap(), 1, Random(3))
        assertEquals(listOf("Vibu", "Dharshu", "Nathiya"), picks.map { it.person })
        picks.forEach { assertTrue(it.title, it.person in it.title && "{" !in it.title) }
    }

    @Test fun recentTasksAreNotRepeated() {
        val today = wed.toEpochDay()
        // Everything but task 7 was picked in the last 60 days.
        val used = (0 until 30).filter { it != 7 }.associate { GodTasks.key(GodTheme.HEALTH, it) to today - 10 }
        repeat(20) { assertEquals(7, GodTasks.pickIndex(GodTheme.HEALTH, used, today, Random(it))) }
    }

    @Test fun whenAllAreRecentTheOldestComesBack() {
        val today = wed.toEpochDay()
        val used = (0 until 30).associate { GodTasks.key(GodTheme.HEALTH, it) to today - 1 - (it % 5) } +
            (GodTasks.key(GodTheme.HEALTH, 12) to today - 40)
        assertEquals(12, GodTasks.pickIndex(GodTheme.HEALTH, used, today, Random(1)))
    }

    @Test fun tasksOlderThanTheWindowCanReturn() {
        val today = wed.toEpochDay()
        val used = (0 until 30).associate { GodTasks.key(GodTheme.HEALTH, it) to today - GodTasks.NO_REPEAT_DAYS }
        val picked = (0 until 50).map { GodTasks.pickIndex(GodTheme.HEALTH, used, today, Random(it)) }.toSet()
        assertTrue(picked.size > 1)
    }

    @Test fun historyRoundTrips() {
        val history = mapOf("HEALTH:3" to 20_380L, "FAMILY:0" to 20_381L)
        assertEquals(history, GodTasks.parseHistory(GodTasks.formatHistory(history) + "junk"))
    }

    @Test fun pickBecomesAnEveningGodTask() {
        val item = GodTasks.Pick(GodTheme.FAMILY, 0, "Ask Vibu how the day was", "Vibu").toItem(wed.toEpochDay())
        assertEquals(SOURCE_GOD, item.source)
        assertEquals("EVENING", item.timeOfDay)
        assertEquals("Family", item.context)
        assertEquals(wed.toEpochDay(), item.dueDate)
        assertTrue(GodTasks.isGod(item))
        assertFalse(GodTasks.isGod(item.copy(source = "CUSTOM")))
    }

    @Test fun notificationLineShowsTheLamp() {
        val item = Item(title = "Do 10 pushups", dueDate = wed.toEpochDay(), status = STATUS_OPEN, source = SOURCE_GOD)
        assertEquals("🪔 Do 10 pushups", TaskNotifications.line(item, wed.toEpochDay()))
    }
}
