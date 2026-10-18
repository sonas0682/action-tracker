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
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.sync.Backup
import com.sonas.actiontracker.sync.GoogleAuth
import com.sonas.actiontracker.sync.Notify
import com.sonas.actiontracker.sync.Reminders
import com.sonas.actiontracker.sync.SeriesWorker
import com.sonas.actiontracker.sync.SlotReminders
import com.sonas.actiontracker.sync.TaskNotifications
import com.sonas.actiontracker.sync.SyncWorker
import com.sonas.actiontracker.ui.AppScreen
import com.sonas.actiontracker.ui.AppTheme
import com.sonas.actiontracker.ui.BackupDialog
import com.sonas.actiontracker.ui.LockScreen
import com.sonas.actiontracker.ui.MainVM
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {
    private val vm: MainVM by viewModels()
    private var unlocked by mutableStateOf(false)
    /** Fingerprint lock at launch. Off by default; the database key never depends on it. */
    private var lockEnabled by mutableStateOf(false)
    private var lastStop = 0L
    /** True while the saved crash trace is on screen instead of the app. */
    private var crashScreen = false

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(res.data) }
                .onSuccess { r -> r.accessToken?.let { vm.sync(it) } }
                .onFailure { vm.message = "Google sign-in cancelled" }
        }

    /** Drive work waiting for the user to grant Drive access. */
    private var pendingDrive: ((String) -> Unit)? = null
    private var showBackup by mutableStateOf(false)
    /** Opens the backup dialog on the fresh-install "restore your actions?" offer. */
    private var backupWelcome by mutableStateOf(false)

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
        // After a crash, show only the saved trace: the normal start-up may be what crashes.
        if (showLastCrash()) return
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
        Reminders.schedule(this)
        SlotReminders.schedule(this)
        TaskNotifications.removeLegacy(this)
        Backup.schedule(this)
        SeriesWorker.schedule(this)
        offerRestoreOnFreshInstall(prefs)
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
                        onOpenBackup = {
                            vm.refreshBackupStatus()
                            backupWelcome = false
                            showBackup = true
                        }
                    )
                    if (showBackup) {
                        BackupDialog(
                            vm,
                            welcome = backupWelcome,
                            onFindBackup = { withDriveToken { vm.findBackup(it) } },
                            onTurnOn = { pass, replace -> withDriveToken { vm.enableBackup(pass, it, replace) } },
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
        if (crashScreen) return
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

    /**
     * Right after a fresh install (or on a new phone) the database is empty and backup
     * is off: offer once to look for a Google Drive backup. The ⋮ menu has it after that.
     */
    private fun offerRestoreOnFreshInstall(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean("restore_offered", false) || Backup.status(this).enabled) return
        lifecycleScope.launch {
            val empty = runCatching { Db.get(this@MainActivity).dao().count() == 0 }.getOrDefault(false)
            prefs.edit().putBoolean("restore_offered", true).apply()
            if (empty) {
                backupWelcome = true
                showBackup = true
            }
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

    /**
     * If [App] saved a crash, shows its stack trace on a plain screen that touches
     * nothing else (no database, no Compose), with Copy and Open app buttons.
     */
    private fun showLastCrash(): Boolean {
        val file = App.crashFile(application)
        val trace = runCatching { file.readText() }.getOrNull() ?: return false
        crashScreen = true
        val pad = (16 * resources.displayMetrics.density).toInt()
        val text = android.widget.TextView(this).apply {
            setText(trace)
            setTextIsSelectable(true)
            textSize = 12f
            setTextColor(android.graphics.Color.BLACK)
        }
        val copy = android.widget.Button(this).apply {
            setText("Copy error")
            setOnClickListener {
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("crash", trace))
                android.widget.Toast.makeText(this@MainActivity, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val open = android.widget.Button(this).apply {
            setText("Open app")
            setOnClickListener {
                file.delete()
                recreate()
            }
        }
        val column = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(android.widget.TextView(this@MainActivity).apply {
                setText("Action Tracker crashed last time")
                textSize = 18f
                setTextColor(android.graphics.Color.BLACK)
            })
            addView(copy)
            addView(open)
            addView(text)
        }
        setContentView(android.widget.ScrollView(this).apply { addView(column) })
        return true
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
