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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonas.actiontracker.sync.PhotoDraft
import com.sonas.actiontracker.sync.VoiceDraft
import com.sonas.actiontracker.R
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.data.SeriesScope
import com.sonas.actiontracker.data.Snapshot
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.Reminders
import com.sonas.actiontracker.sync.SlotReminders
import com.sonas.actiontracker.sync.TaskNotifications
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

enum class Group(val label: String) { TIME("Time"), DATE("Date"), PERSON("Person"), CONTEXT("Context") }

private const val DAY_MS = 86_400_000L
private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
private val PILL = RoundedCornerShape(50)
private const val TAB_ACTIONS = 0
private val TAB_LABELS = listOf("Actions", "Suggested")
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
        done -> Fresh.TextDim
        diff < 0 -> Fresh.Coral
        diff == 0L -> Fresh.Sky
        diff == 1L -> Fresh.Amber
        else -> Fresh.TextDim
    }
}

private fun sourceIcon(source: String): ImageVector = when (source) {
    "GMAIL" -> Icons.Rounded.Email
    "CALENDAR" -> Icons.Rounded.DateRange
    "VOICE" -> MicIcon
    "PHOTO" -> CameraIcon
    else -> Icons.Rounded.Create
}

private fun sourceColor(source: String): Color = when (source) {
    "GMAIL" -> Fresh.Teal
    "CALENDAR" -> Fresh.Sky
    else -> Fresh.Indigo
}

private fun contextIcon(context: String): ImageVector = when (context.lowercase()) {
    "work" -> Icons.Rounded.Build
    "family" -> Icons.Rounded.Home
    "kids/school", "kids", "school" -> Icons.Rounded.Face
    else -> Icons.Rounded.Place
}

private fun personColor(name: String): Color = when (Math.floorMod(name.hashCode(), 4)) {
    0 -> Fresh.Indigo
    1 -> Fresh.Teal
    2 -> Fresh.Sky
    else -> Fresh.Amber
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
                    Box(Modifier.size(132.dp).scale(ring).clip(CircleShape).background(Fresh.Indigo.copy(alpha = 0.18f)))
                    Box(
                        Modifier.size(100.dp).clip(CircleShape).background(Fresh.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text("Action\nTracker", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("Locked to keep your actions private.", color = Fresh.TextDim, textAlign = TextAlign.Center)
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
    onOpenBackup: () -> Unit
) {
    val suggested by vm.suggested.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_ACTIONS) }
    var group by rememberSaveable { mutableStateOf(Group.DATE) }
    var insights by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var voiceDraft by remember { mutableStateOf<VoiceDraft.Draft?>(null) }
    var photoDraft by remember { mutableStateOf<PhotoDraft.Draft?>(null) }
    var readingPhoto by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var drafting by remember { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listening = true else vm.message = "Voice capture needs microphone access"
    }
    val startListening = {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listening = true
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var slotReminders by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val withUndo: (String, Snapshot?) -> Unit = { text, snapshot ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snapshot == null) return@launch
            val result = snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undo(snapshot)
        }
    }
    // A repeating action asks which occurrences first (this one / following / all).
    var pendingDelete by remember { mutableStateOf<Item?>(null) }
    var pendingSave by remember { mutableStateOf<Pair<Item, Item>?>(null) }
    val deleteNow: (Item, SeriesScope) -> Unit = { item, which ->
        scope.launch {
            val snapshot = vm.delete(item, which)
            withUndo(
                when {
                    item.seriesId == null -> "Deleted \"${item.title}\""
                    which == SeriesScope.THIS -> "Deleted this \"${item.title}\""
                    which == SeriesScope.FOLLOWING -> "Stopped \"${item.title}\" from ${shortDue(item.dueDate).lowercase()}"
                    else -> "Deleted every \"${item.title}\""
                },
                snapshot
            )
        }
    }
    val deleteWithUndo: (Item) -> Unit = { item ->
        if (item.seriesId != null) pendingDelete = item else deleteNow(item, SeriesScope.THIS)
    }
    val saveNow: (Item, Item, SeriesScope) -> Unit = { original, edited, which ->
        scope.launch {
            val snapshot = vm.save(original, edited, which)
            if (original.seriesId != null) withUndo("Updated \"${edited.title}\"", snapshot)
        }
    }
    vm.message?.let { lastMessage = it }
    val open = actions.filter { it.status == STATUS_OPEN && !it.isUpcomingRepeat() }

    AuroraBackground {
        if (insights) {
            InsightsScreen(
                actions,
                onClose = { insights = false },
                onToggle = { vm.toggleDone(it) },
                onEdit = { editing = it }
            )
        } else {
            // Top + side insets keep the bar clear of the status bar and the camera cutout in landscape.
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            ) {
                TopBar(
                    tab = tab,
                    onTab = { tab = it },
                    counts = listOf(open.size, suggested.size),
                    open = open,
                    syncing = vm.syncing,
                    onSync = onSync,
                    onInsights = { insights = true },
                    menu = {
                        MoreMenu(
                            lockEnabled, onLockChange,
                            onOpenSlotReminders = { slotReminders = true },
                            onOpenBackup = onOpenBackup
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
                PullToRefreshBox(
                    isRefreshing = vm.syncing,
                    onRefresh = onSync,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    if (tab == TAB_ACTIONS) {
                        ActionsList(actions, group, onGroup = { group = it }, vm = vm, onEdit = { editing = it }, onDelete = deleteWithUndo)
                    } else {
                        SuggestedList(suggested, vm, onEdit = { editing = it }, onSync = onSync)
                    }
                }
            }
            NewActionFab(
                onClick = { voiceDraft = null; photoDraft = null; editing = Item.blank() },
                onLongClick = startListening,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
            )
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 92.dp, bottom = 16.dp)
        )
    }

    val people = remember(actions, suggested) { VoiceDraft.recentValues(actions + suggested, VoiceDraft.DEFAULT_PEOPLE) { it.person } }
    val contexts = remember(actions, suggested) { VoiceDraft.recentValues(actions + suggested, VoiceDraft.DEFAULT_CONTEXTS) { it.context } }
    val usualContexts = remember(actions) { VoiceDraft.usualContexts(actions) }

    // Photo to task: a camera shot, a picked image or one shared into the app becomes a draft.
    val captureFile = remember { File(File(appContext.cacheDir, "photos"), "capture.jpg") }
    val readPhoto: (Uri, Boolean) -> Unit = { uri, captured ->
        editing = null
        readingPhoto = true
        scope.launch {
            val draft = runCatching { PhotoDraft.build(appContext, uri, people, contexts, usualContexts) }.getOrNull()
            if (captured) captureFile.delete()
            readingPhoto = false
            if (draft == null) {
                vm.message = "Couldn't read any text in that photo"
            } else {
                voiceDraft = null
                photoDraft = draft
                editing = draft.item
            }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) readPhoto(FileProvider.getUriForFile(appContext, "${appContext.packageName}.photos", captureFile), true)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { readPhoto(it, false) }
    }
    val startPhoto: (Boolean) -> Unit = { fromCamera ->
        editing = null
        if (fromCamera) {
            captureFile.parentFile?.mkdirs()
            runCatching { takePhoto.launch(FileProvider.getUriForFile(appContext, "${appContext.packageName}.photos", captureFile)) }
                .onFailure { vm.message = "No camera app found" }
        } else {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    LaunchedEffect(vm.sharedPhoto) {
        vm.sharedPhoto?.let { uri ->
            vm.sharedPhoto = null
            readPhoto(uri, false)
        }
    }
    if (readingPhoto) PhotoReadingDialog()

    if (listening) {
        ListenSheet(
            drafting = drafting,
            onHeard = { text ->
                drafting = true
                scope.launch {
                    val draft = VoiceDraft.build(appContext, text, people, contexts, usualContexts)
                    drafting = false
                    listening = false
                    voiceDraft = draft
                    photoDraft = null
                    editing = draft.item
                }
            },
            onDismiss = { if (!drafting) listening = false }
        )
    }

    editing?.let { item ->
        EditSheet(
            item,
            people = people,
            contexts = contexts,
            voice = voiceDraft,
            photo = photoDraft,
            onPhoto = startPhoto,
            onDismiss = { editing = null; voiceDraft = null; photoDraft = null },
            onSave = { edited ->
                if (item.seriesId != null && edited != item) pendingSave = item to edited
                else saveNow(item, edited, SeriesScope.THIS)
                editing = null; voiceDraft = null; photoDraft = null
            }
        )
    }

    pendingDelete?.let { item ->
        ScopeDialog(
            title = "Delete repeating action",
            scopes = SeriesScope.entries,
            onPick = { which -> pendingDelete = null; deleteNow(item, which) },
            onDismiss = { pendingDelete = null }
        )
    }

    pendingSave?.let { (original, edited) ->
        ScopeDialog(
            title = "Change repeating action",
            // Moving one date is fine; a new repeat or end rule only makes sense from here on.
            scopes = if (original.recurrence != edited.recurrence || original.endCount != edited.endCount ||
                original.endDate != edited.endDate
            ) listOf(SeriesScope.FOLLOWING, SeriesScope.ALL) else SeriesScope.entries,
            onPick = { which -> pendingSave = null; saveNow(original, edited, which) },
            onDismiss = { pendingSave = null }
        )
    }

    if (slotReminders) RemindersDialog(onDismiss = { slotReminders = false })
}

/**
 * The home bar. Row 1: Actions | Suggested tabs on the left, calendar/stats, sync and menu on the
 * right. The icons are measured first, so the tabs can only shrink (labels ellipsize) and never
 * push or cover them. Row 2: the four stat pills; on wide windows (landscape) they sit in row 1.
 */
@Composable
private fun TopBar(
    tab: Int,
    onTab: (Int) -> Unit,
    counts: List<Int>,
    open: List<Item>,
    syncing: Boolean,
    onSync: () -> Unit,
    onInsights: () -> Unit,
    menu: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeTabs(tab, counts, onTab, Modifier.weight(1f, fill = false))
                if (wide) {
                    Spacer(Modifier.width(16.dp))
                    StatsStrip(open, inbox = counts[1], Modifier.weight(1f))
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassIconButton(onClick = onInsights) {
                        Icon(Icons.Rounded.DateRange, "Calendar and stats", tint = Fresh.Text, modifier = Modifier.size(20.dp))
                    }
                    SyncButton(syncing, onSync)
                    menu()
                }
            }
            if (!wide) StatsStrip(open, inbox = counts[1], Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/**
 * Two-segment control. Each segment always shows its count badge (dimmed at zero), so nothing
 * appears or disappears when switching tabs and the bar never reflows.
 */
@Composable
private fun HomeTabs(tab: Int, counts: List<Int>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(40.dp).glass(PILL).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TAB_LABELS.forEachIndexed { i, label ->
            val selected = tab == i
            val fg by animateColorAsState(if (selected) Color.White else Fresh.TextDim, label = "tabFg")
            Row(
                Modifier.weight(1f, fill = false).fillMaxHeight().clip(PILL)
                    .then(if (selected) Modifier.background(Fresh.accentHorizontal) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                CountBadge(counts[i], selected)
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int, selected: Boolean) {
    val bg = when {
        selected -> Color(0x33FFFFFF)
        count > 0 -> Fresh.Indigo.copy(alpha = 0.12f)
        else -> Fresh.Glass
    }
    val fg = when {
        selected -> Color.White
        count > 0 -> Fresh.Indigo
        else -> Fresh.TextDim
    }
    Box(
        Modifier.widthIn(min = 22.dp).clip(PILL).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (count > 99) "99+" else "$count", style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** The four stats (late, today, open, inbox) as small one-line glass pills. */
@Composable
private fun StatsStrip(open: List<Item>, inbox: Int, modifier: Modifier = Modifier) {
    val t = today()
    val overdue = open.count { it.dueDate < t }
    val dueToday = open.count { it.dueDate == t }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniStat("Late", overdue, if (overdue > 0) Fresh.Coral else Fresh.Text, Modifier.weight(1f))
        MiniStat("Today", dueToday, Fresh.Sky, Modifier.weight(1f))
        MiniStat("Open", open.size, Fresh.Text, Modifier.weight(1f))
        MiniStat("Inbox", inbox, Fresh.Teal, Modifier.weight(1f))
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(value.toFloat(), tween(600), label = "stat")
    Row(
        modifier.height(28.dp).glass(PILL).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${shown.toInt()}", style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Fresh.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Small glass pill showing the current grouping; tap for Date / Person / Context. */
@Composable
private fun GroupMenu(group: Group, onGroup: (Group) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(32.dp).glass(PILL).clickable { open = true }.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("By ${group.label.lowercase()}", style = MaterialTheme.typography.labelLarge, color = Fresh.Text, maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Group by", tint = Fresh.TextDim)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                "GROUP BY",
                style = MaterialTheme.typography.labelSmall,
                color = Fresh.TextDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Group.entries.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.label) },
                    trailingIcon = { if (g == group) Icon(Icons.Rounded.Check, null, tint = Fresh.Green) },
                    onClick = { onGroup(g); open = false }
                )
            }
        }
    }
}

@Composable
internal fun GlassIconButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier.size(36.dp).glass(CircleShape).clickable(enabled = enabled, onClick = onClick),
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
            tint = if (syncing) Fresh.Sky else Fresh.Text,
            modifier = Modifier.size(20.dp).then(if (syncing) Modifier.rotate(angle) else Modifier)
        )
    }
}

@Composable
private fun MoreMenu(
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onOpenSlotReminders: () -> Unit,
    onOpenBackup: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassIconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Fresh.Text) }
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
                text = { Text("Reminders") },
                onClick = { open = false; onOpenSlotReminders() }
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
internal fun TabSwitcher(
    tab: Int,
    labels: List<String>,
    counts: List<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.height(40.dp).glass(PILL).padding(3.dp)) {
        val segment = maxWidth / labels.size
        val x by animateDpAsState(segment * tab, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "tab")
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().clip(PILL).background(Fresh.accentHorizontal))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val selected = tab == i
                val fg by animateColorAsState(if (selected) Color.White else Fresh.TextDim, label = "fg")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(PILL).clickable { onSelect(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                    if (counts[i] > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.clip(PILL).background(if (selected) Color(0x33FFFFFF) else Fresh.Glass)
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
@OptIn(ExperimentalFoundationApi::class)
private fun NewActionFab(onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val glow = rememberInfiniteTransition(label = "fab")
    val halo by glow.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "halo"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().scale(1f + halo * 0.18f).clip(CircleShape).background(Fresh.Teal.copy(alpha = halo * 0.35f)))
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Fresh.accent).combinedClickable(
                onClickLabel = "Add action",
                onLongClickLabel = "Add action by voice",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add action", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun GradientButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.clip(PILL)
            .background(if (enabled) Fresh.accentHorizontal else Brush.linearGradient(listOf(Fresh.Glass, Fresh.Glass)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (enabled) Color.White else Fresh.TextDim
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = fg)
    }
}

@Composable
private fun ReminderTimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** The 8 AM digest switch, then one row per time of day: on/off and the time its pop-up fires. */
@Composable
private fun RemindersDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var digest by remember { mutableStateOf(Reminders.enabled(context)) }
    var slots by remember { mutableStateOf(SlotReminders.settings(context)) }
    var editing by remember { mutableStateOf<SlotReminders.Slot?>(null) }
    var quickAdd by remember { mutableStateOf(TaskNotifications.quickAddEnabled(context)) }
    val update: (SlotReminders.Slot) -> Unit = { changed ->
        SlotReminders.save(context, changed)
        slots = slots.map { if (it.slot == changed.slot) changed else it }
    }
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminders") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("☀️  Morning digest · ${Reminders.DIGEST_TIME.format(TIME_FORMAT)}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Everything due today or overdue, in one summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = Fresh.TextDim
                        )
                    }
                    Switch(checked = digest, onCheckedChange = {
                        digest = it
                        Reminders.setEnabled(context, it)
                    })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "A pop-up lists the open actions for each time of day that are due today or overdue, each with Done and Snooze.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fresh.TextDim
                )
                if (!notificationsOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Notifications are off for Action Tracker; turn them on in Android settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                slots.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.slot.emoji}  ${s.slot.label}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editing = s }, enabled = s.enabled) { Text(s.time.format(TIME_FORMAT)) }
                        Switch(checked = s.enabled, onCheckedChange = { update(s.copy(enabled = it)) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🎙️  \"Add a task\" notification", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Reply to it, on the phone or your watch, to add a task",
                            style = MaterialTheme.typography.bodySmall,
                            color = Fresh.TextDim
                        )
                    }
                    Switch(checked = quickAdd, onCheckedChange = {
                        quickAdd = it
                        TaskNotifications.setQuickAdd(context, it)
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
    editing?.let { s ->
        ReminderTimeDialog(
            title = "Remind me about ${s.slot.label.lowercase()} actions at",
            initial = s.time,
            onDismiss = { editing = null },
            onConfirm = {
                update(s.copy(time = it))
                editing = null
            }
        )
    }
}

@Composable
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .glass(RoundedCornerShape(16.dp), strong = true)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Info, null, tint = Fresh.Sky, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Dismiss message", Modifier.size(18.dp), tint = Fresh.TextDim) }
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
            Modifier.offset(y = dy.dp).size(92.dp).clip(RoundedCornerShape(30.dp)).background(Fresh.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim, textAlign = TextAlign.Center)
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
                    color = Fresh.TextDim,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.confirmAll(items) }) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Fresh.Green)
                    Spacer(Modifier.width(4.dp))
                    Text("Add all", color = Fresh.Green, style = MaterialTheme.typography.labelLarge)
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
                        color = Fresh.TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Dismiss", tint = Fresh.TextDim, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Fresh.accent).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, "Add", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 40.dp, top = 6.dp, end = 6.dp)) {
                if (item.notes.isNotBlank()) {
                    Text(
                        item.notes, maxLines = 10, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = Fresh.TextDim
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
    onEdit: (Item) -> Unit,
    onDelete: (Item) -> Unit
) {
    // Repeats more than a week out are folded under "Upcoming" so the list stays about now.
    val (upcoming, open) = actions.filter { it.status == STATUS_OPEN }.partition { it.isUpcomingRepeat() }
    val done = actions.filter { it.status == STATUS_DONE }
    var showUpcoming by rememberSaveable { mutableStateOf(false) }
    val grouped: List<Pair<String, List<Item>>> = when (group) {
        Group.TIME -> open.groupBy { TimeOfDay.of(it.timeOfDay) }.toSortedMap()
            .map { (time, list) -> time.label to list }
        Group.DATE -> open.groupBy { dateLabel(it.dueDate) }.toList()
        Group.PERSON -> open.groupBy { it.person }.toList().sortedBy { it.first }
        Group.CONTEXT -> open.groupBy { it.context }.toList().sortedBy { it.first }
    }
    // Inside a section, actions run Morning → Night (a stable sort, so due order holds within a slot).
    val sections = grouped.map { (label, list) ->
        Section("h-$label", label, list.sortedBy { TimeOfDay.of(it.timeOfDay).ordinal })
    } +
        (if (upcoming.isNotEmpty()) listOf(Section(UPCOMING_KEY, "Upcoming", if (showUpcoming) upcoming else emptyList())) else emptyList()) +
        (if (done.isNotEmpty()) listOf(Section("done-header", "Done", done)) else emptyList())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Today's date and the group-by chip; mirrors Suggested's "N TO REVIEW · Add all" row and scrolls away.
        item(key = "list-head") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Fresh.TextDim,
                    modifier = Modifier.weight(1f)
                )
                GroupMenu(group, onGroup)
            }
        }
        if (sections.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing yet", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Tap + to add one, or review Suggested.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fresh.TextDim
                    )
                }
            }
        }
        sections.forEach { section ->
            item(key = section.key) {
                if (section.key == UPCOMING_KEY) {
                    GroupHeader(
                        section.label, upcoming.size, group,
                        Modifier.animateItem().clickable { showUpcoming = !showUpcoming },
                        trailing = if (showUpcoming) "Hide" else "Show"
                    )
                } else {
                    GroupHeader(section.label, section.items.size, group, Modifier.animateItem())
                }
            }
            items(section.items, key = { it.id }) { item ->
                ActionRow(
                    item, if (section.key == UPCOMING_KEY) Group.TIME else group,
                    onToggle = { vm.toggleDone(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

private const val UPCOMING_KEY = "upcoming-header"

/** How many days ahead repeats show in the main sections; later ones go under "Upcoming". */
private const val UPCOMING_AFTER_DAYS = 7

private fun Item.isUpcomingRepeat(): Boolean = seriesId != null && dueDate > today() + UPCOMING_AFTER_DAYS

@Composable
private fun GroupHeader(label: String, count: Int, group: Group, modifier: Modifier = Modifier, trailing: String? = null) {
    val time = if (group == Group.TIME) TimeOfDay.entries.firstOrNull { it.label == label } else null
    val accent = when {
        label == "Done" -> Fresh.TextDim
        time != null -> timeColor(time)
        group == Group.DATE && label == "Overdue" -> Fresh.Coral
        group == Group.DATE && label == "Today" -> Fresh.Sky
        group == Group.DATE && label == "Tomorrow" -> Fresh.Amber
        group == Group.PERSON -> personColor(label)
        else -> Fresh.Indigo
    }
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(18.dp).height(4.dp).clip(PILL).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = accent)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim)
        trailing?.let {
            Spacer(Modifier.weight(1f))
            Text(it, style = MaterialTheme.typography.labelMedium, color = Fresh.Teal, modifier = Modifier.padding(end = 4.dp))
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
    val isDone = item.status == STATUS_DONE
    // Swipe either way past the threshold to delete; the list's animateItem() closes the gap.
    // A repeating row springs back while the "which occurrences?" dialog asks.
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDelete()
            value != SwipeToDismissBoxValue.Settled && item.seriesId == null
        }
    )
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        backgroundContent = {
            // Only paint while a swipe is in progress: the glass row is translucent, so a
            // resting red layer would tint every row and show the trash icon through it.
            val direction = swipe.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier.fillMaxSize().clip(ROW_SHAPE).background(Fresh.Coral.copy(alpha = 0.9f))
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth()
                .glass(ROW_SHAPE)
                .clickable(onClick = onEdit)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlowCheck(isDone, onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isDone) Fresh.TextDim else Fresh.Text,
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
                            color = Fresh.TextDim,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (item.seriesId != null) {
                Icon(
                    painterResource(R.drawable.ic_repeat), "Repeats ${Recurrence.of(item.recurrence).label.lowercase()}",
                    tint = Fresh.TextDim, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            TimeIcon(TimeOfDay.of(item.timeOfDay))
        }
    }
}

fun timeColor(time: TimeOfDay): Color = when (time) {
    TimeOfDay.MORNING -> Fresh.Amber
    TimeOfDay.AFTERNOON -> Fresh.Sky
    TimeOfDay.EVENING -> Fresh.Coral
    TimeOfDay.NIGHT -> Fresh.Indigo
}

/** Sunrise / sunset drawn as tinted icons; Afternoon and Night keep their sun and moon emoji. */
@Composable
fun TimeIcon(time: TimeOfDay, size: Dp = 22.dp) {
    when (time) {
        TimeOfDay.MORNING -> Icon(painterResource(R.drawable.ic_time_morning), time.label, tint = timeColor(time), modifier = Modifier.size(size))
        TimeOfDay.EVENING -> Icon(painterResource(R.drawable.ic_time_evening), time.label, tint = timeColor(time), modifier = Modifier.size(size))
        else -> Text(time.emoji, fontSize = (size.value * 0.8f).sp, modifier = Modifier.semantics { contentDescription = time.label })
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
                if (checked) Modifier.background(Fresh.accent)
                else Modifier.border(2.dp, Fresh.accent, CircleShape)
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
private fun EditSheet(
    item: Item,
    people: List<String>,
    contexts: List<String>,
    voice: VoiceDraft.Draft? = null,
    photo: PhotoDraft.Draft? = null,
    onPhoto: ((fromCamera: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(item.title) }
    var person by remember { mutableStateOf(item.person) }
    var context by remember { mutableStateOf(item.context) }
    var due by remember { mutableLongStateOf(item.dueDate) }
    // New actions follow their context's default until a time chip is tapped; a voice or photo
    // draft keeps a time it found ("at 3 PM") because that differs from the default.
    var timePicked by remember { mutableStateOf(item.id != 0L || item.timeOfDay != TimeOfDay.defaultFor(item.context).name) }
    var pickedTime by remember { mutableStateOf(TimeOfDay.of(item.timeOfDay)) }
    val time = if (timePicked) pickedTime else TimeOfDay.defaultFor(context)
    var repeat by remember { mutableStateOf(Recurrence.of(item.recurrence)) }
    // Ends: never, on a date, or after a number of occurrences (the first counts).
    var endDate by remember { mutableStateOf(if (item.endCount == null) item.endDate else null) }
    var endCount by remember { mutableStateOf(item.endCount) }
    var pickDate by remember { mutableStateOf(false) }
    var pickEndDate by remember { mutableStateOf(false) }
    var pickEndCount by remember { mutableStateOf(false) }
    val t = today()

    // Never taller than 3/4 of the screen; on a wide (landscape) screen also no wider than 3/4.
    val screen = LocalConfiguration.current
    val maxHeight = (screen.screenHeightDp * 0.75f).dp
    val maxWidth = if (screen.screenWidthDp > screen.screenHeightDp) (screen.screenWidthDp * 0.75f).dp else BottomSheetDefaults.SheetMaxWidth
    val save = {
        onSave(
            item.copy(
                title = title.trim(),
                person = person.trim().ifEmpty { "Me" },
                context = context.trim().ifEmpty { "Work" },
                dueDate = due,
                timeOfDay = time.name,
                recurrence = repeat.name,
                // An unchanged "after N times" keeps the end date worked out for it.
                endDate = when {
                    repeat == Recurrence.NONE -> null
                    endCount != null -> if (endCount == item.endCount) item.endDate else null
                    else -> endDate
                },
                endCount = if (repeat == Recurrence.NONE) null else endCount
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = maxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            // Save sits in the header so the keyboard can never cover it.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        voice != null -> "Voice draft"
                        photo != null -> "Photo draft"
                        item.id == 0L -> "New action"
                        else -> "Edit action"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(brush = Fresh.accentHorizontal),
                    modifier = Modifier.weight(1f)
                )
                if (item.id == 0L && voice == null && photo == null && onPhoto != null) PhotoButton(onPhoto)
                TextButton(onClick = onDismiss) { Text("Cancel", color = Fresh.TextDim) }
                GradientButton("Save", Icons.Rounded.Check, enabled = title.isNotBlank(), onClick = save)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                voice?.let { VoiceNote(it) }
                photo?.let { PhotoNote(it) }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("What needs doing?") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SavedValueField(
                        value = person, onValueChange = { person = it }, label = "Person",
                        icon = Icons.Rounded.Person, saved = people, modifier = Modifier.weight(1f)
                    )
                    SavedValueField(
                        value = context, onValueChange = { context = it }, label = "Context",
                        icon = contextIcon(context), saved = contexts, modifier = Modifier.weight(1f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerPill(
                        label = "Due",
                        value = when (due) { t -> "Today"; t + 1 -> "Tomorrow"; t + 7 -> "Next week"; else -> fullDate(due) },
                        options = listOf("Today", "Tomorrow", "Next week", "Pick a date…"),
                        selected = when (due) { t -> 0; t + 1 -> 1; t + 7 -> 2; else -> -1 },
                        onPick = { i -> when (i) { 0 -> due = t; 1 -> due = t + 1; 2 -> due = t + 7; else -> pickDate = true } },
                        modifier = Modifier.weight(1f)
                    )
                    PickerPill(
                        label = "Time",
                        value = time.label,
                        options = TimeOfDay.entries.map { it.label },
                        selected = time.ordinal,
                        onPick = { i -> pickedTime = TimeOfDay.entries[i]; timePicked = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerPill(
                        label = "Repeat",
                        value = repeat.label,
                        options = Recurrence.entries.map { repeatLabel(it, due) },
                        selected = repeat.ordinal,
                        onPick = { i -> repeat = Recurrence.entries[i] },
                        modifier = Modifier.weight(1f)
                    )
                    // Beside Repeat; only active once the action repeats.
                    PickerPill(
                        label = "Ends",
                        value = if (repeat == Recurrence.NONE) "—" else endCount?.let { "$it times" } ?: endDate?.let { shortDate(it) } ?: "Never",
                        options = listOf("Never", "On a date…", "After a number of times…"),
                        enabled = repeat != Recurrence.NONE,
                        selected = when { endCount != null -> 2; endDate != null -> 1; else -> 0 },
                        onPick = { i ->
                            when (i) {
                                0 -> { endDate = null; endCount = null }
                                1 -> pickEndDate = true
                                else -> pickEndCount = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (pickEndDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (endDate ?: (due + 30)) * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { pickEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDate = maxOf(Math.floorDiv(it, DAY_MS), due); endCount = null }
                    pickEndDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickEndDate = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state, title = { Text("Last day", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    if (pickEndCount) {
        EndCountDialog(
            initial = endCount ?: 10,
            onDismiss = { pickEndCount = false },
            onConfirm = { endCount = it; endDate = null; pickEndCount = false }
        )
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

/** "Dec 31", with the year only when it isn't this year. */
private fun shortDate(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return d.format(DateTimeFormatter.ofPattern(if (d.year == LocalDate.now().year) "MMM d" else "MMM d, yyyy"))
}

/** "Ends after N times": how many occurrences in all, the first one included. */
@Composable
private fun EndCountDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val n = text.toIntOrNull()?.takeIf { it in 1..999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ends after") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                label = { Text("Times, this one included") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = { TextButton(enabled = n != null, onClick = { n?.let(onConfirm) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Asks which occurrences of a repeating action an edit or delete applies to. */
@Composable
private fun ScopeDialog(title: String, scopes: List<SeriesScope>, onPick: (SeriesScope) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                scopes.forEach { scope ->
                    TextButton(onClick = { onPick(scope) }, modifier = Modifier.fillMaxWidth()) {
                        Text(scope.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Fresh.TextDim) } }
    )
}

/** "Weekly on Fri", "Monthly on the 17th": the anchor comes from the due date. */
private fun repeatLabel(r: Recurrence, due: Long): String {
    val d = LocalDate.ofEpochDay(due)
    return when (r) {
        Recurrence.WEEKLY -> "Weekly on " + d.format(DateTimeFormatter.ofPattern("EEE"))
        Recurrence.MONTHLY -> "Monthly on the " + ordinal(d.dayOfMonth)
        Recurrence.YEARLY -> "Yearly on " + d.format(DateTimeFormatter.ofPattern("MMM d"))
        else -> r.label
    }
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/**
 * Half-width text field that is also a dropdown of previously used values: tap to pick one,
 * or type a new value (it joins the list once an action is saved with it).
 */
@Composable
private fun SavedValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    saved: List<String>,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val typed = value.trim()
    // Show everything until the text narrows it; a value picked from the list keeps the full list.
    val options = if (typed.isEmpty() || saved.any { it.equals(typed, ignoreCase = true) }) saved
        else saved.filter { it.contains(typed, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = open && options.isNotEmpty(), onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value, onValueChange = { onValueChange(it); open = true },
            label = { Text(label) }, singleLine = true,
            leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
            trailingIcon = { if (saved.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = open && options.isNotEmpty()) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(expanded = open && options.isNotEmpty(), onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onValueChange(option); open = false },
                    trailingIcon = { if (option.equals(typed, ignoreCase = true)) Icon(Icons.Rounded.Check, null, tint = Fresh.Teal) }
                )
            }
        }
    }
}

/** Compact labelled box that opens a dropdown of choices; three fit side by side. */
@Composable
private fun PickerPill(
    label: String,
    value: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.4f), MaterialTheme.shapes.medium)
                .clickable(enabled = enabled) { open = true }
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (enabled) Fresh.Teal else Fresh.TextDim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.ArrowDropDown, null, tint = Fresh.TextDim)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { open = false; onPick(i) },
                    trailingIcon = { if (i == selected) Icon(Icons.Rounded.Check, null, tint = Fresh.Teal) }
                )
            }
        }
    }
}
