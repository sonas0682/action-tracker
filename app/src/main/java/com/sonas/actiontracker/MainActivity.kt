package com.sonas.actiontracker

import android.Manifest
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.Identity
import com.sonas.actiontracker.sync.Backup
import com.sonas.actiontracker.sync.GoogleAuth
import com.sonas.actiontracker.sync.Notify
import com.sonas.actiontracker.sync.Reminders
import com.sonas.actiontracker.sync.SyncWorker
import com.sonas.actiontracker.ui.AppScreen
import com.sonas.actiontracker.ui.AppTheme
import com.sonas.actiontracker.ui.BackupDialog
import com.sonas.actiontracker.ui.LockScreen
import com.sonas.actiontracker.ui.MainVM
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {
    private val vm: MainVM by viewModels()
    private var unlocked by mutableStateOf(false)
    /** Fingerprint lock at launch. Off by default; the database key never depends on it. */
    private var lockEnabled by mutableStateOf(false)
    private var reminders by mutableStateOf(Reminders.Settings(true, LocalTime.of(8, 0)))
    private var lastStop = 0L

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(res.data) }
                .onSuccess { r -> r.accessToken?.let { vm.sync(it) } }
                .onFailure { vm.message = "Google sign-in cancelled" }
        }

    /** Drive work waiting for the user to grant Drive access. */
    private var pendingDrive: ((String) -> Unit)? = null
    private var showBackup by mutableStateOf(false)

    private val driveAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            val action = pendingDrive
            pendingDrive = null
            runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(res.data) }
                .onSuccess { r -> r.accessToken?.let { action?.invoke(it) } }
                .onFailure { vm.message = "Google Drive access cancelled" }
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The UI is always light, so keep dark system-bar icons whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        lockEnabled = prefs.getBoolean("lock_enabled", false)
        if (!lockEnabled) {
            unlocked = true
            askNotificationPermission()
        }
        // Hide contents in the recent-apps switcher and block screenshots.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Notify.ensureChannel(this)
        scheduleBackgroundSync()
        reminders = Reminders.settings(this)
        Reminders.schedule(this)
        Backup.schedule(this)
        setContent {
            AppTheme {
                if (unlocked) {
                    AppScreen(
                        vm,
                        onSync = ::startSync,
                        lockEnabled = lockEnabled,
                        onLockChange = {
                            lockEnabled = it
                            prefs.edit().putBoolean("lock_enabled", it).apply()
                        },
                        reminders = reminders,
                        onRemindersChange = {
                            reminders = it
                            Reminders.save(this, it)
                        },
                        onOpenBackup = { showBackup = true }
                    )
                    if (showBackup) {
                        BackupDialog(
                            vm,
                            onTurnOn = { pass -> withDriveToken { vm.enableBackup(pass, it) } },
                            onBackUpNow = { withDriveToken { vm.backUpNow(it) } },
                            onRestore = { pass -> withDriveToken { vm.restoreBackup(pass, it) } },
                            onDismiss = { showBackup = false }
                        )
                    }
                } else {
                    LockScreen(onUnlock = ::promptUnlock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!lockEnabled) {
            vm.smartTitles()
            return
        }
        // Re-lock if the app was in the background for more than 30 seconds.
        if (!unlocked || SystemClock.elapsedRealtime() - lastStop > 30_000) {
            unlocked = false
            promptUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        lastStop = SystemClock.elapsedRealtime()
    }

    private fun promptUnlock() {
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Action Tracker")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
        )
    }

    private fun onUnlocked() {
        unlocked = true
        vm.smartTitles()
        askNotificationPermission()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startSync() {
        lifecycleScope.launch {
            runCatching { GoogleAuth.authorize(this@MainActivity) }
                .onSuccess { r ->
                    val pending = r.pendingIntent
                    if (r.hasResolution() && pending != null) {
                        authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else {
                        r.accessToken?.let { vm.sync(it) }
                    }
                }
                .onFailure { vm.message = "Google sign-in failed: ${it.message}" }
        }
    }

    /** Gets a Drive access token, asking for Drive access the first time, then runs [action]. */
    private fun withDriveToken(action: (String) -> Unit) {
        lifecycleScope.launch {
            runCatching { Backup.authorize(this@MainActivity) }
                .onSuccess { r ->
                    val pending = r.pendingIntent
                    if (r.hasResolution() && pending != null) {
                        pendingDrive = action
                        driveAuthLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else {
                        r.accessToken?.let(action)
                    }
                }
                .onFailure { vm.message = "Google Drive sign-in failed: ${it.message}" }
        }
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
