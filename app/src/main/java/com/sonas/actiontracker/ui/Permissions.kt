package com.sonas.actiontracker.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sonas.actiontracker.sync.Features
import com.sonas.actiontracker.sync.GoogleAuth

/**
 * ⋮ → Settings → Permissions & features: every permission, Google source and on-device model, with
 * what works, what runs in basic (rule-based) mode and what is off, plus a button to fix it.
 * Re-reads everything when the user comes back from Android settings.
 */
@Composable
fun PermissionsDialog(onSync: () -> Unit, onOpenBackup: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<Features.Snapshot?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) { snapshot = runCatching { Features.read(context) }.getOrNull() }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Android stops showing the prompt after two refusals; settings is the only way back then.
        if (!granted) openSettings(context, appDetails(context))
        refresh++
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions & features") },
        text = {
            val s = snapshot
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (s == null) {
                    Text("Checking this phone…", style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim)
                    return@Column
                }
                Section("Phone permissions")
                Features.permissionRows(s).forEach { row ->
                    FeatureRow(row) {
                        when (row.title) {
                            "Notifications" -> if (row.state != Features.State.ON) FixButton { openSettings(context, notificationSettings(context)) }
                            "Alarms & reminders" -> if (row.state != Features.State.ON && Build.VERSION.SDK_INT >= 31) FixButton {
                                openSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                            "Microphone" -> if (!s.microphone) FixButton { mic.launch(Manifest.permission.RECORD_AUDIO) }
                            "Screen lock" -> if (row.state != Features.State.ON) FixButton { openSettings(context, Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                        }
                    }
                }
                Section("Google account")
                Features.googleRows(s).forEach { row ->
                    FeatureRow(row) {
                        when (row.title) {
                            "Gmail" -> Switch(checked = s.gmail, onCheckedChange = {
                                GoogleAuth.setGmail(context, it)
                                refresh++
                                if (it) onSync()
                            })
                            "Google Calendar" -> Switch(checked = s.calendar, onCheckedChange = {
                                GoogleAuth.setCalendar(context, it)
                                refresh++
                                if (it) onSync()
                            })
                            else -> TextButton(onClick = { onDismiss(); onOpenBackup() }) { Text("Open") }
                        }
                    }
                }
                Text(
                    "Switching a source off stops the app using it. To remove the app's access completely, " +
                        "go to your Google Account → Security → Third-party apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fresh.TextDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Section("On-device AI")
                Features.aiRows(s).forEach { row -> FeatureRow(row) {} }
                Text(
                    "Basic mode means the built-in rules do the work: everything still works, the wording is just simpler. " +
                        "AI runs only on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fresh.TextDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Fresh.Indigo)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun FeatureRow(row: Features.Row, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                StateChip(row.state)
            }
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = Fresh.TextDim)
        }
        action()
    }
}

@Composable
private fun StateChip(state: Features.State) {
    val (label, color) = when (state) {
        Features.State.ON -> "On" to Fresh.Green
        Features.State.BASIC -> "Basic" to Fresh.Amber
        Features.State.OFF -> "Off" to Fresh.Coral
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun FixButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("Fix") }
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private fun notificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Some phones lack a settings screen; fall back to the app's own page. */
private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
