package com.sonas.actiontracker

import com.sonas.actiontracker.sync.Features.State
import com.sonas.actiontracker.ui.backupSummary
import com.sonas.actiontracker.ui.featuresSummary
import com.sonas.actiontracker.ui.godTaskSummary
import com.sonas.actiontracker.ui.remindersSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTextTest {
    @Test fun reminders() {
        assertEquals("8 AM digest · 4 pop-ups a day", remindersSummary(true, 4))
        assertEquals("8 AM digest only", remindersSummary(true, 0))
        assertEquals("1 pop-up a day", remindersSummary(false, 1))
        assertEquals("Off", remindersSummary(false, 0))
    }

    @Test fun godTask() {
        assertEquals("Random · 1 weekday, 3 weekend", godTaskSummary("Random", false, 1, 3))
        assertEquals("Random · none weekday, 2 weekend", godTaskSummary("Random", false, 0, 2))
        assertEquals("Off", godTaskSummary("Random", true, 1, 3))
        assertEquals("Off", godTaskSummary("Random", false, 0, 0))
    }

    @Test fun backup() {
        val hour = 3_600_000L
        assertEquals("Off", backupSummary(false, 5L, 10L))
        assertEquals("On · not backed up yet", backupSummary(true, null, 10L))
        assertEquals("On · backed up just now", backupSummary(true, 0L, 30_000L))
        assertEquals("On · backed up 5 min ago", backupSummary(true, 0L, 5 * 60_000L))
        assertEquals("On · backed up 2 h ago", backupSummary(true, 0L, 2 * hour))
        assertEquals("On · backed up 3 days ago", backupSummary(true, 0L, 72 * hour))
    }

    @Test fun features() {
        assertEquals("Everything on", featuresSummary(listOf(State.ON, State.ON)))
        assertEquals("2 basic · 1 off", featuresSummary(listOf(State.BASIC, State.OFF, State.BASIC, State.ON)))
        assertEquals("1 off", featuresSummary(listOf(State.OFF)))
    }
}
