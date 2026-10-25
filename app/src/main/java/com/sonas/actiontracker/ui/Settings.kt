package com.sonas.actiontracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sonas.actiontracker.sync.Features
import com.sonas.actiontracker.sync.GOD_LAMP
import com.sonas.actiontracker.sync.GodTasks
import com.sonas.actiontracker.sync.Reminders
import com.sonas.actiontracker.sync.SlotReminders
import com.sonas.actiontracker.sync.THOUGHT_MARK

private val CARD = RoundedCornerShape(20.dp)
private val SEGMENT = RoundedCornerShape(50)

/**
 * ⋮ → Settings: everything that is set once and left alone, in three sections.
 * Daily (Reminders, God task, Thought of the day), Look (Theme) and Privacy & data
 * (fingerprint lock, Drive backup, Permissions & features). Each row says its current
 * state underneath, so most of it reads without opening anything.
 */
@Composable
internal fun SettingsScreen(
    vm: MainVM,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onOpenBackup: () -> Unit,
    onSync: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    var reminders by remember { mutableStateOf(false) }
    var godTask by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(false) }
    // Bumped when a dialog closes or the app comes back to the front, so the summaries re-read.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val reminderText = remember(tick) {
        remindersSummary(Reminders.enabled(context), SlotReminders.settings(context).count { it.enabled })
    }
    val godText = remember(tick) {
        val choice = GodTasks.choice(context)
        godTaskSummary(GodTasks.choiceLabel(choice), choice == GodTasks.OFF, GodTasks.weekdayCount(context), GodTasks.weekendCount(context))
    }
    val backup = vm.backupStatus
    val backupText = remember(tick, backup) { backupSummary(backup.enabled, backup.lastBackupAt, System.currentTimeMillis()) }
    var featuresText by remember { mutableStateOf("Checking…") }
    LaunchedEffect(tick) {
        vm.refreshBackupStatus()
        runCatching { Features.read(context) }.getOrNull()?.let { s ->
            featuresText = featuresSummary((Features.permissionRows(s) + Features.googleRows(s) + Features.aiRows(s)).map { it.state })
        }
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    ContextFrame(caption = "Action Tracker", title = "Settings", onBack = onBack, dock = null) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            vm.message?.let { text ->
                item(key = "message") {
                    Box(Modifier.widthIn(max = 640.dp).padding(top = 8.dp)) { MessageBanner(text, onClose = { vm.message = null }) }
                }
            }
            item(key = "daily") {
                SettingsSection("Daily") {
                    SettingRow(
                        icon = { IconBadge(Fresh.Sky) { Icon(Icons.Rounded.Notifications, null, tint = Fresh.Sky, modifier = Modifier.size(20.dp)) } },
                        title = "Reminders",
                        summary = reminderText,
                        onClick = { reminders = true }
                    )
                    SettingDivider()
                    SettingRow(
                        icon = { IconBadge(Fresh.Gold) { Text(GOD_LAMP) } },
                        title = "God task",
                        summary = godText,
                        onClick = { godTask = true }
                    )
                    SettingDivider()
                    SettingSwitch(
                        icon = { IconBadge(Fresh.Indigo) { Text(THOUGHT_MARK, color = Fresh.Indigo, style = MaterialTheme.typography.titleMedium) } },
                        title = "Thought of the day",
                        summary = "A quote to read each morning",
                        checked = vm.showThought,
                        onChange = vm::setShowThought
                    )
                }
            }
            item(key = "look") {
                SettingsSection("Look") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(Fresh.Teal) { ThemeGlyph(Fresh.Teal) }
                        Spacer(Modifier.width(14.dp))
                        Text("Theme", style = MaterialTheme.typography.titleSmall, color = Fresh.Text, modifier = Modifier.weight(1f))
                        ThemeSwitcher(ThemePref.mode, vm::setThemeMode)
                    }
                }
            }
            item(key = "privacy") {
                SettingsSection("Privacy & data") {
                    SettingSwitch(
                        icon = { IconBadge(Fresh.Indigo) { Icon(Icons.Rounded.Lock, null, tint = Fresh.Indigo, modifier = Modifier.size(20.dp)) } },
                        title = "Fingerprint lock",
                        summary = if (lockEnabled) "Asks each time the app opens" else "Off",
                        checked = lockEnabled,
                        onChange = onLockChange
                    )
                    SettingDivider()
                    SettingRow(
                        icon = { IconBadge(Fresh.Sky) { CloudGlyph(Fresh.Sky) } },
                        title = "Google Drive backup",
                        summary = backupText,
                        onClick = onOpenBackup
                    )
                    SettingDivider()
                    SettingRow(
                        icon = { IconBadge(Fresh.Green) { Icon(Icons.Rounded.CheckCircle, null, tint = Fresh.Green, modifier = Modifier.size(20.dp)) } },
                        title = "Permissions & features",
                        summary = featuresText,
                        onClick = { permissions = true }
                    )
                }
            }
            item(key = "version") {
                Text(
                    listOfNotNull("Action Tracker", version).joinToString(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Fresh.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                )
            }
        }
    }

    if (reminders) RemindersDialog(onDismiss = { reminders = false; tick++ })
    if (godTask) GodTaskDialog(onPick = vm::setGodTheme, onCounts = vm::setGodCounts, onDismiss = { godTask = false; tick++ })
    if (permissions) PermissionsDialog(onSync = onSync, onOpenBackup = onOpenBackup, onDismiss = { permissions = false; tick++ })
}

/** A small caption, then one white card holding the section's rows. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 18.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Fresh.TextDim,
            modifier = Modifier.padding(start = 14.dp, bottom = 6.dp)
        )
        Column(Modifier.fillMaxWidth().glass(CARD, strong = true)) { content() }
    }
}

/** Tappable row: icon, title over its current state, and a chevron. */
@Composable
private fun SettingRow(icon: @Composable () -> Unit, title: String, summary: String, onClick: () -> Unit) {
    SettingLine(icon, title, summary, Modifier.clickable(onClick = onClick)) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Fresh.TextDim)
    }
}

/** On/off row: the whole row toggles, the switch shows the state. */
@Composable
private fun SettingSwitch(icon: @Composable () -> Unit, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingLine(icon, title, summary, Modifier.clickable(role = Role.Switch) { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingLine(
    icon: @Composable () -> Unit,
    title: String,
    summary: String,
    modifier: Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Fresh.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Hairline between rows, starting after the icon so the icons read as one column. */
@Composable
private fun SettingDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp), color = Fresh.Rim)
}

/** Every row's icon sits on the same soft tinted circle, so the left edge lines up. */
@Composable
private fun IconBadge(tint: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** System / Light / Dark as one segmented pill; a tap applies it at once. */
@Composable
private fun ThemeSwitcher(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(Modifier.height(34.dp).well(SEGMENT).padding(3.dp)) {
        ThemeMode.entries.forEach { mode ->
            val on = mode == current
            Box(
                Modifier.fillMaxHeight().clip(SEGMENT)
                    .background(if (on) Fresh.Indigo else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onPick(mode) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (mode == ThemeMode.SYSTEM) "System" else mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) Color.White else Fresh.Text
                )
            }
        }
    }
}

/** A circle half filled: the Theme row's icon (the core icon set has no contrast glyph). */
@Composable
private fun ThemeGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = size.width / 9
        drawCircle(color, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        drawArc(color, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = Offset.Zero, size = size)
    }
}

/** A small cloud: the Drive backup row's icon (the core icon set has none). */
@Composable
private fun CloudGlyph(color: Color) {
    Canvas(Modifier.size(width = 20.dp, height = 14.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = h * 0.30f, center = Offset(w * 0.28f, h * 0.66f))
        drawCircle(color, radius = h * 0.42f, center = Offset(w * 0.52f, h * 0.46f))
        drawCircle(color, radius = h * 0.30f, center = Offset(w * 0.74f, h * 0.64f))
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.28f, h * 0.55f),
            size = Size(w * 0.46f, h * 0.39f)
        )
    }
}
