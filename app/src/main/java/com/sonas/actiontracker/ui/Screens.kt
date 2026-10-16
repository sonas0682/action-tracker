@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sonas.actiontracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

private fun sourceIcon(source: String): ImageVector = when (source) {
    "GMAIL" -> Icons.Rounded.Email
    "CALENDAR" -> Icons.Rounded.DateRange
    else -> Icons.Rounded.Create
}

private fun sourceLabel(source: String): String = when (source) {
    "GMAIL" -> "From Gmail"
    "CALENDAR" -> "From Calendar"
    else -> "Added by you"
}

private fun contextIcon(context: String): ImageVector = when (context.lowercase()) {
    "work" -> Icons.Rounded.Build
    "family" -> Icons.Rounded.Home
    "kids/school", "kids", "school" -> Icons.Rounded.Face
    else -> Icons.Rounded.Place
}

// ---------------------------------------------------------------- Lock screen

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "ring"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface, cs.surface)))
            .systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(120.dp).scale(ring).clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.15f))
                )
                Surface(shape = CircleShape, color = cs.primary, shadowElevation = 8.dp, modifier = Modifier.size(96.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(44.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Action Tracker", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Locked to keep your actions private.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onUnlock,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Unlock with fingerprint", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------- Main screen

@Composable
fun AppScreen(
    vm: MainVM,
    onSync: () -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    reminders: Reminders.Settings,
    onRemindersChange: (Reminders.Settings) -> Unit
) {
    val suggested by vm.suggested.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf(Group.DATE) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }
    vm.message?.let { lastMessage = it }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val cs = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Action Tracker") },
                actions = {
                    SyncButton(syncing = vm.syncing, onSync = onSync)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Match wallpaper colors") },
                                trailingIcon = { Switch(checked = dynamicColor, onCheckedChange = null) },
                                onClick = { onDynamicColorChange(!dynamicColor) }
                            )
                            DropdownMenuItem(
                                text = { Text("Daily due-date reminder") },
                                trailingIcon = { Switch(checked = reminders.enabled, onCheckedChange = null) },
                                onClick = { onRemindersChange(reminders.copy(enabled = !reminders.enabled)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Reminder time: ${reminders.time.format(TIME_FORMAT)}") },
                                enabled = reminders.enabled,
                                onClick = { menu = false; pickingTime = true }
                            )
                        }
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.surface,
                    scrolledContainerColor = cs.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Item.blank() },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New action") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            AnimatedVisibility(
                visible = vm.message != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                MessageBanner(lastMessage, onClose = { vm.message = null })
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = cs.surface) {
                Tab(
                    selected = tab == 0, onClick = { tab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Suggested")
                            if (suggested.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Badge { Text("${suggested.size}") }
                            }
                        }
                    }
                )
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Actions") })
            }
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
private fun SyncButton(syncing: Boolean, onSync: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle"
    )
    FilledTonalIconButton(onClick = onSync, enabled = !syncing) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = if (syncing) "Syncing" else "Sync",
            modifier = if (syncing) Modifier.rotate(angle) else Modifier
        )
    }
}

@Composable
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Dismiss message", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
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
            Button(onClick = onSync, enabled = !vm.syncing) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Sync now")
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text("${items.size} to review", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Found in Gmail and Calendar. Add the ones that matter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(items, key = { it.id }) { item ->
            SuggestionCard(
                item,
                onAdd = { vm.confirm(item) },
                onEdit = { onEdit(item) },
                onDismiss = { vm.dismiss(item) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    item: Item,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(sourceIcon(item.source), null, tint = cs.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sourceLabel(item.source), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.notes.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.notes, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DuePill(item.dueDate)
                MetaPill(Icons.Rounded.Person, item.person)
                MetaPill(contextIcon(item.context), item.context)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Dismiss", color = cs.onSurfaceVariant) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onEdit, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Edit")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAdd, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun MetaPill(icon: ImageVector, text: String, container: Color? = null, content: Color? = null) {
    val cs = MaterialTheme.colorScheme
    Surface(color = container ?: cs.surfaceContainerHighest, contentColor = content ?: cs.onSurfaceVariant, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun DuePill(dueDate: Long) {
    val cs = MaterialTheme.colorScheme
    val diff = dueDate - today()
    val (bg, fg) = when {
        diff < 0 -> cs.errorContainer to cs.onErrorContainer
        diff == 0L -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
    val text = if (diff in 0L..1L) dateLabel(dueDate) else fullDate(dueDate)
    MetaPill(Icons.Rounded.DateRange, if (diff < 0) "Overdue · $text" else text, bg, fg)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary") { SummaryCard(open) }
        item(key = "group") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Group.entries.forEachIndexed { i, g ->
                    SegmentedButton(
                        selected = group == g,
                        onClick = { onGroup(g) },
                        shape = SegmentedButtonDefaults.itemShape(i, Group.entries.size)
                    ) { Text(g.label) }
                }
            }
        }
        if (sections.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No actions yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap New action to add one, or review Suggested.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun SummaryCard(open: List<Item>) {
    val cs = MaterialTheme.colorScheme
    val t = today()
    val overdue = open.count { it.dueDate < t }
    val dueToday = open.count { it.dueDate == t }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
            .padding(20.dp)
    ) {
        Column {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                when {
                    open.isEmpty() -> "All clear"
                    overdue > 0 -> "Let's catch up"
                    dueToday > 0 -> "Focus on today"
                    else -> "You're ahead"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = cs.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Overdue", overdue, if (overdue > 0) cs.error else cs.onPrimaryContainer, Modifier.weight(1f))
                Stat("Today", dueToday, cs.onPrimaryContainer, Modifier.weight(1f))
                Stat("Open", open.size, cs.onPrimaryContainer, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupHeader(label: String, count: Int, group: Group, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val accent = when {
        label == "Done" -> cs.outline
        group == Group.DATE && label == "Overdue" -> cs.error
        group == Group.DATE && label == "Today" -> cs.primary
        group == Group.DATE && label == "Tomorrow" -> cs.tertiary
        else -> cs.secondary
    }
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            label == "Done" -> Icon(Icons.Rounded.CheckCircle, null, tint = accent, modifier = Modifier.size(20.dp))
            group == Group.PERSON -> PersonAvatar(label, 22.dp)
            group == Group.CONTEXT -> Icon(contextIcon(label), null, tint = accent, modifier = Modifier.size(20.dp))
            else -> Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = accent)
        Spacer(Modifier.width(8.dp))
        Surface(color = accent.copy(alpha = 0.14f), contentColor = accent, shape = CircleShape) {
            Text("$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
        }
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
    val cs = MaterialTheme.colorScheme
    val isDone = item.status == STATUS_DONE
    val overdue = !isDone && item.dueDate < today()
    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (isDone) cs.surfaceContainerLow else cs.surfaceContainer)
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isDone, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDone) cs.onSurfaceVariant else cs.onSurface,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(item.person, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    val parts = buildList {
                        if (group != Group.PERSON) add(item.person)
                        if (group != Group.CONTEXT) add(item.context)
                        add(if (group == Group.DATE && !isDone) fullDate(item.dueDate) else dateLabel(item.dueDate))
                    }
                    Text(
                        parts.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdue) cs.error else cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "Delete", tint = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PersonAvatar(name: String, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (Math.floorMod(name.hashCode(), 3)) {
        0 -> cs.primaryContainer to cs.onPrimaryContainer
        1 -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.tertiaryContainer to cs.onTertiaryContainer
    }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = fg,
            fontSize = (size.value * 0.5f).sp,
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (item.id == 0L) "New action" else "Edit action",
                style = MaterialTheme.typography.headlineSmall
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
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
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
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Save")
                }
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
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
