package com.sonas.actiontracker

import com.google.mlkit.genai.common.FeatureStatus
import com.sonas.actiontracker.sync.Features
import com.sonas.actiontracker.sync.Features.State
import com.sonas.actiontracker.sync.GoogleAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    private val all = Features.Snapshot(
        notifications = true, exactAlarms = true, microphone = true, speechService = true,
        gmail = true, calendar = true, googleSignedIn = true, driveBackup = true,
        nanoSummary = FeatureStatus.AVAILABLE, nanoProofread = FeatureStatus.AVAILABLE,
        dateModel = true, screenLock = true
    )

    private fun List<Features.Row>.state(title: String) = first { it.title == title }.state

    @Test fun everythingAllowedIsOn() {
        val rows = Features.permissionRows(all) + Features.googleRows(all) + Features.aiRows(all)
        assertTrue(rows.all { it.state == State.ON })
    }

    @Test fun deniedPermissionsSayWhatIsMissing() {
        val s = all.copy(notifications = false, microphone = false, exactAlarms = false)
        val rows = Features.permissionRows(s)
        assertEquals(State.OFF, rows.state("Notifications"))
        assertTrue(rows.first { it.title == "Notifications" }.detail.startsWith("No reminders"))
        assertEquals(State.OFF, rows.state("Microphone"))
        assertEquals(State.BASIC, rows.state("Alarms & reminders"))
    }

    @Test fun noExactAlarmRowBeforeAndroid12() {
        assertNull(Features.permissionRows(all.copy(exactAlarms = null)).firstOrNull { it.title == "Alarms & reminders" })
    }

    @Test fun micWithoutSpeechServiceIsOff() {
        val row = Features.permissionRows(all.copy(speechService = false)).first { it.title == "Microphone" }
        assertEquals(State.OFF, row.state)
        assertTrue(row.detail.contains("no speech recognition service"))
    }

    @Test fun missingNanoFallsBackToBasicRules() {
        val rows = Features.aiRows(all.copy(nanoSummary = FeatureStatus.UNAVAILABLE, nanoProofread = null, dateModel = false))
        assertTrue(rows.all { it.state == State.BASIC })
        assertTrue(rows.first().detail.contains("built-in rules"))
    }

    @Test fun switchedOffGoogleSourceIsOff() {
        val rows = Features.googleRows(all.copy(gmail = false))
        assertEquals(State.OFF, rows.state("Gmail"))
        assertEquals(State.ON, rows.state("Google Calendar"))
    }

    @Test fun declinedScopesAreTheMissingOnes() {
        val both = listOf(GoogleAuth.GMAIL_SCOPE, GoogleAuth.CALENDAR_SCOPE)
        assertEquals(listOf(GoogleAuth.GMAIL_SCOPE), GoogleAuth.declined(both, listOf(GoogleAuth.CALENDAR_SCOPE)))
        assertEquals(emptyList<String>(), GoogleAuth.declined(both, both))
    }
}
