package com.sonas.actiontracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
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
import com.sonas.actiontracker.sync.GoogleAuth
import com.sonas.actiontracker.sync.Notify
import com.sonas.actiontracker.sync.SyncWorker
import com.sonas.actiontracker.ui.AppScreen
import com.sonas.actiontracker.ui.AppTheme
import com.sonas.actiontracker.ui.LockScreen
import com.sonas.actiontracker.ui.MainVM
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {
    private val vm: MainVM by viewModels()
    private var unlocked by mutableStateOf(false)
    private var dynamicColor by mutableStateOf(true)
    private var lastStop = 0L

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(res.data) }
                .onSuccess { r -> r.accessToken?.let { vm.sync(it) } }
                .onFailure { vm.message = "Google sign-in cancelled" }
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        dynamicColor = prefs.getBoolean("dynamic_color", true)
        // Hide contents in the recent-apps switcher and block screenshots.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Notify.ensureChannel(this)
        scheduleBackgroundSync()
        setContent {
            AppTheme(dynamicColor = dynamicColor) {
                if (unlocked) {
                    AppScreen(
                        vm,
                        onSync = ::startSync,
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = {
                            dynamicColor = it
                            prefs.edit().putBoolean("dynamic_color", it).apply()
                        }
                    )
                } else {
                    LockScreen(onUnlock = ::promptUnlock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
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
                    unlocked = true
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
