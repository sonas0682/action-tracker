@file:OptIn(ExperimentalMaterial3Api::class)

package com.sonas.actiontracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.sync.Reminders
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Group(val label: String) { DATE("Date"), PERSON("Person"), CONTEXT("Context") }

private val PEOPLE = listOf("Me", "Nathiya", "Vibu", "Dharshu")
private val CONTEXTS = listOf("Work", "Family", "Kids/School")
private const val DAY_MS = 86_400_000L
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
private val PILL = RoundedCornerShape(50)
private val ROW_SHAPE = RoundedCornerShape(18.dp)

private fun today(): Long = LocalDate.now().toEpochDay()

private fun fullDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d"))

private fun dateLabel(epochDay: Long): String {
    val diff = epochDay - today()
    return when {
        diff < 0 -> "Overdue"
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        else -> fullDate(epochDay)
    }
}

/** Short due label for compact rows: "Today", "Tomorrow", "3d late", "Fri, Oct 24". */
private fun shortDue(epochDay: Long): String {
    val diff = epochDay - today()
    return when {
        diff < 0 -> "${-diff}d late"
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        else -> fullDate(epochDay)
    }
}

private fun dueColor(epochDay: Long, done: Boolean = false): Color {
    val diff = epochDay - today()
    return when {
        done -> Midnight.TextDim
        diff < 0 -> Midnight.Coral
        diff == 0L -> Midnight.Cyan
        diff == 1L -> Midnight.Amber
        else -> Midnight.TextDim
    }
}

private fun sourceIcon(source: String): ImageVector = when (source) {
    "GMAIL" -> Icons.Rounded.Email
    "CALENDAR" -> Icons.Rounded.DateRange
    else -> Icons.Rounded.Create
}

private fun sourceColor(source: String): Color = when (source) {
    "GMAIL" -> Midnight.Magenta
    "CALENDAR" -> Midnight.Cyan
    else -> Midnight.Violet
}

private fun contextIcon(context: String): ImageVector = when (context.lowercase()) {
    "work" -> Icons.Rounded.Build
    "family" -> Icons.Rounded.Home
    "kids/school", "kids", "school" -> Icons.Rounded.Face
    else -> Icons.Rounded.Place
}

private fun personColor(name: String): Color = when (Math.floorMod(name.hashCode(), 4)) {
    0 -> Midnight.Violet
    1 -> Midnight.Magenta
    2 -> Midnight.Cyan
    else -> Midnight.Amber
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

// ---------------------------------------------------------------- Lock screen

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "ring"
    )
    AuroraBackground {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(132.dp).scale(ring).clip(CircleShape).background(Midnight.Violet.copy(alpha = 0.18f)))
                    Box(
                        Modifier.size(100.dp).clip(CircleShape).background(Midnight.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text("Action\nTracker", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("Locked to keep your actions private.", color = Midnight.TextDim, textAlign = TextAlign.Center)
            }
            GradientButton(
                "Unlock", Icons.Rounded.Lock, onUnlock,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(58.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- Main screen

@Composable
fun AppScreen(
    vm: MainVM,
    onSync: () -> Unit,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    reminders: Reminders.Settings,
    onRemindersChange: (Reminders.Settings) -> Unit,
    onOpenBackup: () -> Unit
) {
    val suggested by vm.suggested.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var group by remember { mutableStateOf(Group.DATE) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var pickingTime by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }
    vm.message?.let { lastMessage = it }
    val open = actions.filter { it.status == STATUS_OPEN }

    AuroraBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(
                open = open,
                inbox = suggested.size,
                syncing = vm.syncing,
                onSync = onSync,
                menu = {
                    MoreMenu(
                        lockEnabled, onLockChange, reminders, onRemindersChange,
                        onPickTime = { pickingTime = true }, onOpenBackup = onOpenBackup
                    )
                }
            )
            AnimatedVisibility(
                visible = vm.message != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                MessageBanner(lastMessage, onClose = { vm.message = null })
            }
            TabSwitcher(
                tab = tab,
                labels = listOf("Suggested", "Actions"),
                counts = listOf(suggested.size, open.size),
                onSelect = { tab = it }
            )
            PullToRefreshBox(
                isRefreshing = vm.syncing,
                onRefresh = onSync,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                if (tab == 0) {
                    SuggestedList(suggested, vm, onEdit = { editing = it }, onSync = onSync)
                } else {
                    ActionsList(actions, group, onGroup = { group = it }, vm = vm, onEdit = { editing = it })
                }
            }
        }
        NewActionFab(
            onClick = { editing = Item.blank() },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
        )
    }

    editing?.let { item ->
        EditSheet(item, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null })
    }

    if (pickingTime) {
        ReminderTimeDialog(
            initial = reminders.time,
            onDismiss = { pickingTime = false },
            onConfirm = {
                onRemindersChange(reminders.copy(time = it))
                pickingTime = false
            }
        )
    }
}

@Composable
private fun Header(open: List<Item>, inbox: Int, syncing: Boolean, onSync: () -> Unit, menu: @Composable () -> Unit) {
    val t = today()
    val overdue = open.count { it.dueDate < t }
    val dueToday = open.count { it.dueDate == t }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Midnight.accent))
            Spacer(Modifier.width(8.dp))
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · MMM d")).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Midnight.TextDim,
                modifier = Modifier.weight(1f)
            )
            SyncButton(syncing, onSync)
            Spacer(Modifier.width(6.dp))
            menu()
        }
        Spacer(Modifier.height(6.dp))
        Text(greeting(), style = MaterialTheme.typography.titleMedium, color = Midnight.TextDim)
        Text(
            when {
                open.isEmpty() && inbox == 0 -> "All clear."
                overdue > 0 -> "Let's catch up."
                dueToday > 0 -> "Focus on today."
                open.isEmpty() -> "Inbox waiting."
                else -> "You're ahead."
            },
            style = MaterialTheme.typography.displaySmall.copy(brush = Midnight.accentHorizontal),
            maxLines = 1
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Late", overdue, if (overdue > 0) Midnight.Coral else Midnight.Text, Modifier.weight(1f))
            StatTile("Today", dueToday, Midnight.Cyan, Modifier.weight(1f))
            StatTile("Open", open.size, Midnight.Text, Modifier.weight(1f))
            StatTile("Inbox", inbox, Midnight.Magenta, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun StatTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(value.toFloat(), tween(600), label = "stat")
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            "${shown.toInt()}",
            style = MaterialTheme.typography.headlineMedium,
            color = color
        )
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Midnight.TextDim)
    }
}

@Composable
private fun GlassIconButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier.size(42.dp).glass(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun SyncButton(syncing: Boolean, onSync: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle"
    )
    GlassIconButton(onClick = onSync, enabled = !syncing) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = if (syncing) "Syncing" else "Sync",
            tint = if (syncing) Midnight.Cyan else Midnight.Text,
            modifier = Modifier.size(22.dp).then(if (syncing) Modifier.rotate(angle) else Modifier)
        )
    }
}

@Composable
private fun MoreMenu(
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    reminders: Reminders.Settings,
    onRemindersChange: (Reminders.Settings) -> Unit,
    onPickTime: () -> Unit,
    onOpenBackup: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassIconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Midnight.Text) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            DropdownMenuItem(
                text = { Text("Fingerprint lock") },
                trailingIcon = { Switch(checked = lockEnabled, onCheckedChange = null) },
                onClick = { onLockChange(!lockEnabled) }
            )
            DropdownMenuItem(
                text = { Text("Daily due-date reminder") },
                trailingIcon = { Switch(checked = reminders.enabled, onCheckedChange = null) },
                onClick = { onRemindersChange(reminders.copy(enabled = !reminders.enabled)) }
            )
            DropdownMenuItem(
                text = { Text("Reminder time: ${reminders.time.format(TIME_FORMAT)}") },
                enabled = reminders.enabled,
                onClick = { open = false; onPickTime() }
            )
            DropdownMenuItem(
                text = { Text("Google Drive backup") },
                onClick = { open = false; onOpenBackup() }
            )
        }
    }
}

/** Glass capsule with a gradient pill that slides between the two tabs. */
@Composable
private fun TabSwitcher(tab: Int, labels: List<String>, counts: List<Int>, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp).glass(PILL).padding(4.dp)
    ) {
        val segment = maxWidth / labels.size
        val x by animateDpAsState(segment * tab, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "tab")
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().clip(PILL).background(Midnight.accentHorizontal))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val selected = tab == i
                val fg by animateColorAsState(if (selected) Color.White else Midnight.TextDim, label = "fg")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(PILL).clickable { onSelect(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                    if (counts[i] > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.clip(PILL).background(if (selected) Color(0x33FFFFFF) else Midnight.Glass)
                                .padding(horizontal = 7.dp, vertical = 1.dp)
                        ) {
                            Text("${counts[i]}", style = MaterialTheme.typography.labelMedium, color = fg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewActionFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glow = rememberInfiniteTransition(label = "fab")
    val halo by glow.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "halo"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().scale(1f + halo * 0.18f).clip(PILL).background(Midnight.Magenta.copy(alpha = halo * 0.35f)))
        Row(
            Modifier.clip(PILL).background(Midnight.accent).clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("New", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
private fun GradientButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.clip(PILL)
            .background(if (enabled) Midnight.accentHorizontal else Brush.linearGradient(listOf(Midnight.Glass, Midnight.Glass)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (enabled) Color.White else Midnight.TextDim
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = fg)
    }
}

@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me about due actions at") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .glass(RoundedCornerShape(16.dp), strong = true)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Info, null, tint = Midnight.Cyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Dismiss message", Modifier.size(18.dp), tint = Midnight.TextDim) }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    val bob = rememberInfiniteTransition(label = "bob")
    val dy by bob.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "dy"
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.offset(y = dy.dp).size(92.dp).clip(RoundedCornerShape(30.dp)).background(Midnight.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Midnight.TextDim, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}

// ---------------------------------------------------------------- Suggested inbox

@Composable
private fun SuggestedList(items: List<Item>, vm: MainVM, onEdit: (Item) -> Unit, onSync: () -> Unit) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Email,
            title = "Inbox zero",
            body = "Nothing to review. Sync to scan Gmail and Calendar for new action items."
        ) {
            GradientButton("Sync now", Icons.Rounded.Refresh, onSync, enabled = !vm.syncing)
        }
        return
    }
    var expanded by remember { mutableStateOf<Long?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "intro") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${items.size} TO REVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = Midnight.TextDim,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.confirmAll(items) }) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Midnight.Lime)
                    Spacer(Modifier.width(4.dp))
                    Text("Add all", color = Midnight.Lime, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        items(items, key = { it.id }) { item ->
            SuggestionRow(
                item,
                expanded = expanded == item.id,
                onToggle = { expanded = if (expanded == item.id) null else item.id },
                onAdd = { vm.confirm(item) },
                onEdit = { onEdit(item) },
                onDismiss = { vm.dismiss(item) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

/** One compact suggestion: icon, one-line title, one-line meta, inline dismiss/add. Tap to expand. */
@Composable
private fun SuggestionRow(
    item: Item,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = sourceColor(item.source)
    Column(
        modifier.fillMaxWidth()
            .glass(ROW_SHAPE, strong = expanded)
            .clickable(onClick = onToggle)
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(sourceIcon(item.source), null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (expanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shortDue(item.dueDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = dueColor(item.dueDate)
                    )
                    Text(
                        "  ·  ${item.person}  ·  ${item.context}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Midnight.TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Dismiss", tint = Midnight.TextDim, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Midnight.accent).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, "Add", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 40.dp, top = 6.dp, end = 6.dp)) {
                if (item.notes.isNotBlank()) {
                    Text(
                        item.notes, maxLines = 4, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = Midnight.TextDim
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (item.source) {
                            "GMAIL" -> "From Gmail"
                            "CALENDAR" -> "From Calendar"
                            else -> "Added by you"
                        } + " · due ${fullDate(item.dueDate)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Actions

private data class Section(val key: String, val label: String, val items: List<Item>)

@Composable
private fun ActionsList(
    actions: List<Item>,
    group: Group,
    onGroup: (Group) -> Unit,
    vm: MainVM,
    onEdit: (Item) -> Unit
) {
    val open = actions.filter { it.status == STATUS_OPEN }
    val done = actions.filter { it.status == STATUS_DONE }
    val grouped: List<Pair<String, List<Item>>> = when (group) {
        Group.DATE -> open.groupBy { dateLabel(it.dueDate) }.toList()
        Group.PERSON -> open.groupBy { it.person }.toList().sortedBy { it.first }
        Group.CONTEXT -> open.groupBy { it.context }.toList().sortedBy { it.first }
    }
    val sections = grouped.map { (label, list) -> Section("h-$label", label, list) } +
        if (done.isNotEmpty()) listOf(Section("done-header", "Done", done)) else emptyList()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "group") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("GROUP BY", style = MaterialTheme.typography.labelSmall, color = Midnight.TextDim)
                Spacer(Modifier.width(2.dp))
                Group.entries.forEach { g -> GroupChip(g.label, group == g) { onGroup(g) } }
            }
        }
        if (sections.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing yet", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Tap New to add one, or review Suggested.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Midnight.TextDim
                    )
                }
            }
        }
        sections.forEach { section ->
            item(key = section.key) {
                GroupHeader(section.label, section.items.size, group, Modifier.animateItem())
            }
            items(section.items, key = { it.id }) { item ->
                ActionRow(
                    item, group,
                    onToggle = { vm.toggleDone(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { vm.delete(item) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val fg by animateColorAsState(if (selected) Color.White else Midnight.TextDim, label = "chip")
    Box(
        Modifier.clip(PILL)
            .then(if (selected) Modifier.background(Midnight.accentHorizontal) else Modifier.glass(PILL))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun GroupHeader(label: String, count: Int, group: Group, modifier: Modifier = Modifier) {
    val accent = when {
        label == "Done" -> Midnight.TextDim
        group == Group.DATE && label == "Overdue" -> Midnight.Coral
        group == Group.DATE && label == "Today" -> Midnight.Cyan
        group == Group.DATE && label == "Tomorrow" -> Midnight.Amber
        group == Group.PERSON -> personColor(label)
        else -> Midnight.Violet
    }
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(18.dp).height(4.dp).clip(PILL).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = accent)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Midnight.TextDim)
    }
}

@Composable
private fun ActionRow(
    item: Item,
    group: Group,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = item.status == STATUS_DONE
    Row(
        modifier.fillMaxWidth()
            .glass(ROW_SHAPE)
            .clickable(onClick = onEdit)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlowCheck(isDone, onToggle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDone) Midnight.TextDim else Midnight.Text,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(item.person, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (group == Group.DATE && !isDone) shortDue(item.dueDate) else dateLabel(item.dueDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = dueColor(item.dueDate, isDone)
                )
                val rest = buildList {
                    if (group != Group.PERSON) add(item.person)
                    if (group != Group.CONTEXT) add(item.context)
                }
                if (rest.isNotEmpty()) {
                    Text(
                        rest.joinToString(prefix = "  ·  ", separator = "  ·  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Midnight.TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Delete, "Delete", tint = Midnight.TextDim, modifier = Modifier.size(20.dp))
        }
    }
}

/** Round check that fills with the accent gradient and pops when ticked. */
@Composable
private fun GlowCheck(checked: Boolean, onToggle: () -> Unit) {
    val pop by animateFloatAsState(
        if (checked) 1f else 0.85f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop"
    )
    Box(
        Modifier.size(26.dp).scale(pop).clip(CircleShape)
            .then(
                if (checked) Modifier.background(Midnight.accent)
                else Modifier.border(2.dp, Midnight.accent, CircleShape)
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Rounded.Check, "Mark not done", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PersonAvatar(name: String, size: Dp) {
    val c = personColor(name)
    Box(Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = c,
            fontSize = (size.value * 0.55f).sp,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ---------------------------------------------------------------- Edit sheet

@Composable
private fun EditSheet(item: Item, onDismiss: () -> Unit, onSave: (Item) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(item.title) }
    var person by remember { mutableStateOf(item.person) }
    var context by remember { mutableStateOf(item.context) }
    var due by remember { mutableLongStateOf(item.dueDate) }
    var pickDate by remember { mutableStateOf(false) }
    val t = today()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (item.id == 0L) "New action" else "Edit action",
                style = MaterialTheme.typography.headlineMedium.copy(brush = Midnight.accentHorizontal)
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("What needs doing?") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            SheetLabel("Person")
            OutlinedTextField(
                value = person, onValueChange = { person = it },
                label = { Text("Person") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PEOPLE.forEach { FilterChip(selected = person == it, onClick = { person = it }, label = { Text(it) }) }
            }

            SheetLabel("Context")
            OutlinedTextField(
                value = context, onValueChange = { context = it },
                label = { Text("Context") }, singleLine = true,
                leadingIcon = { Icon(contextIcon(context), null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONTEXTS.forEach { FilterChip(selected = context == it, onClick = { context = it }, label = { Text(it) }) }
            }

            SheetLabel("Due")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = due == t, onClick = { due = t }, label = { Text("Today") })
                FilterChip(selected = due == t + 1, onClick = { due = t + 1 }, label = { Text("Tomorrow") })
                FilterChip(selected = due == t + 7, onClick = { due = t + 7 }, label = { Text("Next week") })
                AssistChip(
                    onClick = { pickDate = true },
                    label = { Text(fullDate(due)) },
                    leadingIcon = { Icon(Icons.Rounded.DateRange, null, Modifier.size(18.dp)) }
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Midnight.TextDim) }
                Spacer(Modifier.weight(1f))
                GradientButton(
                    "Save", Icons.Rounded.Check,
                    enabled = title.isNotBlank(),
                    onClick = {
                        onSave(
                            item.copy(
                                title = title.trim(),
                                person = person.trim().ifEmpty { "Me" },
                                context = context.trim().ifEmpty { "Work" },
                                dueDate = due
                            )
                        )
                    }
                )
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = due * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = Math.floorDiv(it, DAY_MS) }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Midnight.Magenta,
        modifier = Modifier.padding(top = 4.dp)
    )
}
