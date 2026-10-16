package com.sonas.actiontracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

private enum class BackupMode { MAIN, SET_UP, RESTORE }

private const val MIN_PASSPHRASE = 8

/**
 * Google Drive backup settings. Drive access is requested by the caller, so each
 * action hands back only what it needs (the passphrase) and the activity supplies a token.
 */
@Composable
fun BackupDialog(
    vm: MainVM,
    onTurnOn: (passphrase: String) -> Unit,
    onBackUpNow: () -> Unit,
    onRestore: (passphrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    val status = vm.backupStatus
    var mode by remember { mutableStateOf(if (status.enabled) BackupMode.MAIN else BackupMode.SET_UP) }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == BackupMode.RESTORE) "Restore from Google Drive" else "Google Drive backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    BackupMode.MAIN -> {
                        Text("Your actions are encrypted with your backup passphrase and saved daily to a hidden app folder in your Google Drive.")
                        Text(vm.lastBackupLabel(), style = MaterialTheme.typography.bodyMedium)
                    }
                    BackupMode.SET_UP -> {
                        Text(
                            "Choose a backup passphrase. It encrypts the backup before it leaves the phone, " +
                                "and you'll need it to restore on a new phone. It can't be recovered if you forget it."
                        )
                        PassField("Passphrase (8+ characters)", pass) { pass = it }
                        PassField("Repeat passphrase", confirm) { confirm = it }
                        if (confirm.isNotEmpty() && confirm != pass) {
                            Text("Passphrases don't match", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    BackupMode.RESTORE -> {
                        Text(
                            "This replaces every action on this phone with the backup in your Google Drive.",
                            color = MaterialTheme.colorScheme.error
                        )
                        PassField("Backup passphrase", pass) { pass = it }
                    }
                }
                if (vm.backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            when (mode) {
                BackupMode.MAIN -> TextButton(onClick = onBackUpNow, enabled = !vm.backupBusy) { Text("Back up now") }
                BackupMode.SET_UP -> TextButton(
                    onClick = { onTurnOn(pass) },
                    enabled = !vm.backupBusy && pass.length >= MIN_PASSPHRASE && pass == confirm
                ) { Text("Turn on") }
                BackupMode.RESTORE -> TextButton(
                    onClick = { onRestore(pass) },
                    enabled = !vm.backupBusy && pass.isNotEmpty()
                ) { Text("Restore") }
            }
        },
        dismissButton = {
            when (mode) {
                BackupMode.MAIN -> Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { pass = ""; mode = BackupMode.RESTORE }) { Text("Restore…") }
                    TextButton(onClick = { vm.disableBackup(); onDismiss() }) { Text("Turn off") }
                }
                BackupMode.SET_UP -> TextButton(onClick = { pass = ""; mode = BackupMode.RESTORE }) { Text("Restore instead") }
                BackupMode.RESTORE -> TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    // After a successful turn-on, backup or restore, show the backup summary.
    val successes = vm.backupSuccesses
    val seen = remember { successes }
    LaunchedEffect(successes) {
        if (successes != seen) { pass = ""; confirm = ""; mode = BackupMode.MAIN }
    }
}

@Composable
private fun PassField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}
