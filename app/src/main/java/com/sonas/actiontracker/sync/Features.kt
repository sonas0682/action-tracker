package com.sonas.actiontracker.sync

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.nl.entityextraction.EntityExtractionRemoteModel
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the Permissions & features screen shows: for each permission, Google source and
 * on-device model, whether the feature that needs it works fully, in basic (rule-based) mode,
 * or not at all. Reading these never asks the user for anything.
 */
object Features {
    enum class State { ON, BASIC, OFF }

    /** Plain flags read from the phone, so the wording below stays testable without a device. */
    data class Snapshot(
        val notifications: Boolean,
        /** Null below Android 12, where exact alarms need no permission. */
        val exactAlarms: Boolean?,
        val microphone: Boolean,
        val speechService: Boolean,
        val gmail: Boolean,
        val calendar: Boolean,
        val googleSignedIn: Boolean,
        val driveBackup: Boolean,
        /** [FeatureStatus] values; null when the check failed or timed out. */
        val nanoSummary: Int?,
        val nanoProofread: Int?,
        val dateModel: Boolean,
        val screenLock: Boolean
    )

    data class Row(val title: String, val state: State, val detail: String)

    suspend fun read(context: Context): Snapshot {
        val app = context.applicationContext
        val granted = { p: String -> ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED }
        return Snapshot(
            notifications = NotificationManagerCompat.from(app).areNotificationsEnabled(),
            exactAlarms = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() else null,
            microphone = granted(Manifest.permission.RECORD_AUDIO),
            speechService = SpeechRecognizer.isRecognitionAvailable(app) ||
                (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)),
            gmail = GoogleAuth.gmailOn(app),
            calendar = GoogleAuth.calendarOn(app),
            googleSignedIn = GoogleAuth.everGranted(app),
            driveBackup = Backup.status(app).enabled,
            nanoSummary = SmartTitles.status(app),
            nanoProofread = VoiceDraft.proofreadStatus(app),
            dateModel = dateModelReady(),
            screenLock = BiometricManager.from(app).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        )
    }

    private suspend fun dateModelReady(): Boolean = runCatching {
        val model = EntityExtractionRemoteModel.Builder(EntityExtractorOptions.ENGLISH).build()
        withTimeoutOrNull(5_000) { RemoteModelManager.getInstance().isModelDownloaded(model).await() } == true
    }.getOrDefault(false)

    fun permissionRows(s: Snapshot): List<Row> = listOfNotNull(
        Row(
            "Notifications",
            if (s.notifications) State.ON else State.OFF,
            if (s.notifications) "Reminders, the morning digest and new-suggestion alerts"
            else "No reminders, morning digest or new-suggestion alerts. The app still works when you open it."
        ),
        s.exactAlarms?.let { exact ->
            Row(
                "Alarms & reminders",
                if (exact) State.ON else State.BASIC,
                if (exact) "Time-of-day reminders arrive on time"
                else "Time-of-day reminders still come, but Android may delay them by a few minutes"
            )
        },
        Row(
            "Microphone",
            when {
                !s.microphone -> State.OFF
                !s.speechService -> State.OFF
                else -> State.ON
            },
            when {
                !s.microphone -> "No voice capture (long-press +). Typing, photos and sharing still work."
                !s.speechService -> "Allowed, but this phone has no speech recognition service, so voice capture is unavailable"
                else -> "Voice capture: long-press + and say the task"
            }
        ),
        Row(
            "Screen lock",
            if (s.screenLock) State.ON else State.OFF,
            if (s.screenLock) "Fingerprint lock can be turned on from the ⋮ menu"
            else "Set up a fingerprint, face or PIN in Android settings to use the app lock"
        )
    )

    fun googleRows(s: Snapshot): List<Row> = listOf(
        googleRow("Gmail", s.gmail, s.googleSignedIn, "Suggested actions from recent emails"),
        googleRow("Google Calendar", s.calendar, s.googleSignedIn, "Suggested actions from the next 7 days of events"),
        Row(
            "Google Drive backup",
            if (s.driveBackup) State.ON else State.OFF,
            if (s.driveBackup) "Encrypted backup to the app's hidden Drive folder"
            else "No backup: your actions live only on this phone. Turn on under ⋮ → Google Drive backup."
        )
    )

    private fun googleRow(name: String, on: Boolean, signedIn: Boolean, what: String) = Row(
        name,
        if (on && signedIn) State.ON else State.OFF,
        when {
            !on -> "Off: no suggestions from $name. Switch on to allow it."
            !signedIn -> "$what, after you sign in with Sync"
            else -> what
        }
    )

    fun aiRows(s: Snapshot): List<Row> = listOf(
        Row(
            "Smart titles and summaries",
            nanoState(s.nanoSummary),
            nanoDetail(s.nanoSummary, "Gemini Nano rewrites email and photo titles and adds a three-bullet summary",
                "Titles come from the built-in rules instead of Gemini Nano")
        ),
        Row(
            "Voice title clean-up",
            nanoState(s.nanoProofread),
            nanoDetail(s.nanoProofread, "Gemini Nano tidies the words of spoken tasks",
                "Spoken tasks use the built-in rules only")
        ),
        Row(
            "Dates in photos and shared text",
            if (s.dateModel) State.ON else State.BASIC,
            if (s.dateModel) "On-device ML Kit finds dates and times"
            else "Simple date rules until ML Kit's small date model has downloaded once (needs internet)"
        )
    )

    fun nanoState(status: Int?): State = when (status) {
        FeatureStatus.AVAILABLE -> State.ON
        else -> State.BASIC
    }

    private fun nanoDetail(status: Int?, on: String, basic: String): String = when (status) {
        FeatureStatus.AVAILABLE -> on
        FeatureStatus.DOWNLOADING -> "Gemini Nano is downloading. $basic for now."
        FeatureStatus.DOWNLOADABLE -> "Gemini Nano downloads the first time it's needed. $basic until then."
        else -> "Gemini Nano isn't available on this phone. $basic."
    }
}
