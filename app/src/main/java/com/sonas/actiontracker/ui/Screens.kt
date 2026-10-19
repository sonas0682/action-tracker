@file:OptIn(ExperimentalMaterial3Api::class)

package com.sonas.actiontracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun fullDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DAY_FORMAT)

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
    "SHARE" -> Icons.Rounded.Share
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
    var readingPhoto by remember { mutableStateOf<String?>(null) }
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
    var permissions by remember { mutableStateOf(false) }
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
    /** A snackbar with Undo when [onUndo] is given, plain otherwise. */
    val showUndo: (String, (() -> Unit)?) -> Unit = { text, onUndo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(text, actionLabel = onUndo?.let { "Undo" }, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo?.invoke()
        }
    }
    val people = remember(actions, suggested) { VoiceDraft.recentValues(actions + suggested, VoiceDraft.DEFAULT_PEOPLE) { it.person } }
    val contexts = remember(actions, suggested) { VoiceDraft.recentValues(actions + suggested, VoiceDraft.DEFAULT_CONTEXTS) { it.context } }
    val usualContexts = remember(actions) { VoiceDraft.usualContexts(actions) }
    // A repeating action asks which occurrences first (this one / following / all).
    var pendingDelete by remember { mutableStateOf<Item?>(null) }
    var pendingSave by remember { mutableStateOf<Pair<Item, Item>?>(null) }
    val deleteNow: (Item, SeriesScope) -> Unit = { item, which ->
        scope.launch {
            val snapshot = runCatching { vm.delete(item, which) }
                .getOrElse { vm.message = "Couldn't delete \"${item.title}\": ${it.message}"; return@launch }
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
            val snapshot = runCatching { vm.save(original, edited, which) }
                .getOrElse { vm.message = "Couldn't save \"${edited.title}\": ${it.message}"; return@launch }
            if (original.seriesId != null) withUndo("Updated \"${edited.title}\"", snapshot)
        }
    }
    vm.message?.let { lastMessage = it }
    val open = remember(actions, today()) { actions.filter { it.status == STATUS_OPEN && !it.isUpcomingRepeat() } }

    AuroraBackground {
        if (insights) {
            InsightsScreen(
                actions,
                topBar = { TopBar(counts = listOf(open.size, suggested.size), open = open) },
                onClose = { insights = false },
                onToggle = { vm.toggleDone(it) },
                onEdit = { editing = it }
            )
        } else {
            // Thumb-first: a calm slim bar on top for rare actions; tabs, stats and the type bar sit in
            // a bottom dock, and the Actions list is anchored to the bottom so the most urgent row is
            // nearest the thumb. Side insets keep it clear of the camera cutout in landscape.
            Column(
                Modifier.fillMaxSize()
                    // The list sits low on a sunken tint; the stats and dock panels float above it.
                    .background(Fresh.Recess)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ) {
                TopBar(counts = listOf(open.size, suggested.size), open = open)
                AnimatedVisibility(
                    visible = vm.message != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    MessageBanner(lastMessage, onClose = { vm.message = null })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PullToRefreshBox(
                        isRefreshing = vm.syncing,
                        onRefresh = onSync,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (tab == TAB_ACTIONS) {
                            ActionsList(actions, group, vm = vm, onEdit = { editing = it }, onDelete = deleteWithUndo)
                        } else {
                            SuggestedDeck(
                                suggested, vm,
                                onEdit = { editing = it },
                                onSync = onSync,
                                onAdded = { item ->
                                    // A repeating suggestion fills its series on confirm, so only a one-off can be put back.
                                    if (item.recurrence == Recurrence.NONE.name) showUndo("Added \"${item.title}\"") { vm.restore(item) }
                                    else showUndo("Added \"${item.title}\"", null)
                                },
                                onSkipped = { item -> showUndo("Skipped \"${item.title}\"") { vm.restore(item) } }
                            )
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp))
                }
                BottomDock(
                    tab = tab,
                    onTab = { tab = it },
                    counts = listOf(open.size, suggested.size),
                    people = people,
                    contexts = contexts,
                    usualContexts = usualContexts,
                    onNew = { voiceDraft = null; photoDraft = null; editing = Item.blank() },
                    onVoice = startListening,
                    onAdd = { draft ->
                        tab = TAB_ACTIONS
                        saveNow(Item.blank(), draft, SeriesScope.THIS)
                        showUndo("Added \"${draft.title}\"", null)
                    },
                    contextual = {
                        if (tab == TAB_ACTIONS) GroupMenu(group) { group = it }
                        else if (suggested.isNotEmpty()) DockChip("Add all ${suggested.size}", Icons.Rounded.Check, Fresh.Green) { vm.confirmAll(suggested) }
                    },
                    tools = {
                        GlassIconButton(onClick = { insights = true }) {
                            Icon(Icons.Rounded.DateRange, "Calendar and stats", tint = Fresh.Text, modifier = Modifier.size(20.dp))
                        }
                        SyncButton(vm.syncing, onSync)
                        MoreMenu(
                            lockEnabled, onLockChange,
                            onOpenSlotReminders = { slotReminders = true },
                            onOpenPermissions = { permissions = true },
                            onOpenBackup = onOpenBackup
                        )
                    }
                )
            }
        }
    }

    // Photo to task: a camera shot, a picked image or one shared into the app becomes a draft.
    val captureFile = remember { File(File(appContext.cacheDir, "photos"), "capture.jpg") }
    val readPhoto: (Uri, Boolean) -> Unit = { uri, captured ->
        editing = null
        readingPhoto = "Reading the photo"
        scope.launch {
            val draft = runCatching { PhotoDraft.build(appContext, uri, people, contexts, usualContexts) }.getOrNull()
            if (captured) captureFile.delete()
            readingPhoto = null
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
    // Share to task: text shared from another app becomes a draft the same way.
    LaunchedEffect(vm.sharedText) {
        val text = vm.sharedText ?: return@LaunchedEffect
        vm.sharedText = null
        editing = null
        readingPhoto = "Reading the shared text"
        // Not in this effect's scope: clearing sharedText restarts the effect.
        scope.launch {
            val draft = runCatching { PhotoDraft.fromShare(appContext, text, people, contexts, usualContexts) }.getOrNull()
            readingPhoto = null
            if (draft == null) {
                vm.message = "Nothing to add from that share"
            } else {
                voiceDraft = null
                photoDraft = draft
                editing = draft.item
            }
        }
    }
    readingPhoto?.let { PhotoReadingDialog(it) }

    if (listening) {
        ListenSheet(
            drafting = drafting,
            onHeard = { text ->
                drafting = true
                scope.launch {
                    // Nano trouble is already handled inside build; this keeps the sheet from hanging on anything else.
                    val draft = runCatching { VoiceDraft.build(appContext, text, people, contexts, usualContexts) }
                        .getOrElse { VoiceDraft.Draft(VoiceDraft.fromRules(text, people, contexts, LocalDate.now(), usualContexts), text.trim(), false) }
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
    if (permissions) PermissionsDialog(onSync = onSync, onOpenBackup = onOpenBackup, onDismiss = { permissions = false })
}

/** The slim top bar: just the Late / Today / Open / Inbox pills, read at a glance. Calendar and Stats show it too. */
@Composable
internal fun TopBar(counts: List<Int>, open: List<Item>) {
    // Its own white panel, running up under the status bar, with rounded lower corners.
    Box(
        // zIndex keeps its shadow above the list, which is drawn after it.
        Modifier.fillMaxWidth().zIndex(1f).panel(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentAlignment = Alignment.Center
    ) {
        StatsStrip(
            open, inbox = counts[1],
            Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
        )
    }
}

/**
 * Everything the thumb touches, at the bottom: a tools row (group-by on Actions or "Add all" on
 * Suggested on the left, today's date in the middle, calendar/stats, sync and menu on the right under
 * the right thumb), then the type-to-capture bar (with its live chips), then the Actions | Suggested
 * tabs. While the keyboard is up only the bar shows. On wide windows it is one row: tabs, bar, then the tools.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomDock(
    tab: Int,
    onTab: (Int) -> Unit,
    counts: List<Int>,
    people: List<String>,
    contexts: List<String>,
    usualContexts: Map<String, String>,
    onNew: () -> Unit,
    onVoice: () -> Unit,
    onAdd: (Item) -> Unit,
    contextual: @Composable () -> Unit,
    tools: @Composable () -> Unit
) {
    val typing = WindowInsets.isImeVisible
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .zIndex(1f)
            .panel(DOCK_SHAPE)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)
    ) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(verticalAlignment = Alignment.Bottom) {
                HomeTabs(tab, counts, onTab, Modifier.width(280.dp))
                Spacer(Modifier.width(12.dp))
                CaptureBar(people, contexts, usualContexts, onNew, onVoice, onAdd, Modifier.weight(1f))
                if (!typing) {
                    Spacer(Modifier.width(12.dp))
                    Row(Modifier.height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                        contextual()
                        Spacer(Modifier.width(8.dp))
                        tools()
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!typing) ToolsRow(contextual, tools, middle = { TodayLabel() })
                CaptureBar(people, contexts, usualContexts, onNew, onVoice, onAdd, Modifier.fillMaxWidth())
                if (!typing) HomeTabs(tab, counts, onTab, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The bottom dock's white panel: rounded top corners, running down under the navigation bar. */
internal val DOCK_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * A screen's contextual chip on the left, its icon buttons on the right and an optional [middle]
 * (today's date on the main screen) centred in the space between them.
 */
@Composable
internal fun ToolsRow(
    contextual: @Composable () -> Unit,
    tools: @Composable () -> Unit,
    middle: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        contextual()
        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { middle?.invoke() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { tools() }
    }
}

/** Today's date ("Sun, Oct 18") for the gap in the tools row; ellipsizes rather than crowd the icons. */
@Composable
private fun TodayLabel() {
    Text(
        LocalDate.now().format(DAY_FORMAT),
        style = MaterialTheme.typography.titleSmall,
        color = Fresh.TextDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Small glass chip used for the dock's contextual action ("Add all"). */
@Composable
private fun DockChip(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).well(PILL).clickable(onClick = onClick).padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}

/**
 * Type-to-capture: "ask Vibu about the science fair Friday evening" becomes a task with the same
 * rules as voice capture ([VoiceDraft.fromRules]); chips show what was understood while typing.
 * Empty, the bar offers the mic and the `+` (tap: full edit sheet, long-press: voice).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CaptureBar(
    people: List<String>,
    contexts: List<String>,
    usualContexts: Map<String, String>,
    onNew: () -> Unit,
    onVoice: () -> Unit,
    onAdd: (Item) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf("") }
    val draft = remember(text, people, contexts, usualContexts) {
        if (text.isBlank()) null
        else VoiceDraft.fromRules(text, people, contexts, LocalDate.now(), usualContexts)
            .copy(source = "CUSTOM", notes = "")
            .takeIf { it.title.isNotBlank() }
    }
    val submit = {
        draft?.let { onAdd(it); text = "" }
        Unit
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedVisibility(visible = draft != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            // Keep the last chips while the row collapses.
            var shown by remember { mutableStateOf<Item?>(null) }
            if (draft != null) shown = draft
            shown?.let { DraftChips(it) }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).well(RoundedCornerShape(18.dp))
                .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fresh.Text),
                cursorBrush = SolidColor(Fresh.Indigo),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 3,
                modifier = Modifier.weight(1f).semantics { contentDescription = "New task" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                "Type a task…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Fresh.TextDim.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                }
            )
            if (text.isEmpty()) {
                IconButton(onClick = onVoice, modifier = Modifier.size(40.dp)) {
                    Icon(MicIcon, "Add action by voice", tint = Fresh.Indigo, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Fresh.accent).combinedClickable(
                        onClickLabel = "Add action",
                        onLongClickLabel = "Add action by voice",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVoice()
                        },
                        onClick = onNew
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add action", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (draft != null) Fresh.accent else Brush.linearGradient(listOf(Fresh.Rim, Fresh.Rim)))
                        .clickable(enabled = draft != null, onClickLabel = "Add task", onClick = submit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Add task", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** What the type bar understood: who, when, time of day, context and repeat. */
@Composable
private fun DraftChips(item: Item) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        InfoChip(item.person, personColor(item.person))
        InfoChip(shortDue(item.dueDate), dueColor(item.dueDate).takeIf { it != Fresh.TextDim } ?: Fresh.Teal)
        val time = TimeOfDay.of(item.timeOfDay)
        InfoChip(time.label, timeColor(time))
        InfoChip(item.context, Fresh.Indigo)
        val repeat = Recurrence.of(item.recurrence)
        if (repeat != Recurrence.NONE) InfoChip(repeat.label, Fresh.Teal)
    }
}

@Composable
private fun InfoChip(label: String, dot: Color) {
    Row(
        Modifier.height(28.dp).well(PILL).padding(start = 9.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Fresh.Text, maxLines = 1)
    }
}

/**
 * Two-segment control. Each segment always shows its count badge (dimmed at zero), so nothing
 * appears or disappears when switching tabs and the bar never reflows.
 */
@Composable
private fun HomeTabs(tab: Int, counts: List<Int>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(46.dp).well(PILL).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TAB_LABELS.forEachIndexed { i, label ->
            val selected = tab == i
            val fg by animateColorAsState(if (selected) Color.White else Fresh.TextDim, label = "tabFg")
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(PILL)
                    .then(if (selected) Modifier.background(Fresh.accentHorizontal) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(start = 14.dp, end = 8.dp),
                horizontalArrangement = Arrangement.Center,
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
        // Tinted in the stat's own colour, so the pills read as numbers, not as buttons or rows.
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
            .background((if (color == Fresh.Text) Fresh.Indigo else color).copy(alpha = 0.10f))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${shown.toInt()}", style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
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
            Modifier.height(36.dp).well(PILL).clickable { open = true }.padding(start = 12.dp, end = 4.dp),
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
        Modifier.size(36.dp).well(CircleShape).clickable(enabled = enabled, onClick = onClick),
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
    onOpenPermissions: () -> Unit,
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
            DropdownMenuItem(
                text = { Text("Permissions & features") },
                onClick = { open = false; onOpenPermissions() }
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
    BoxWithConstraints(modifier.height(40.dp).well(PILL).padding(3.dp)) {
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
                            "Everything due today or overdue, in one notification",
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
                    "One pop-up per time of day lists its open actions due today or overdue.",
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
    val dy = bob.animateFloat(
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
            Modifier.offset { IntOffset(0, dy.value.dp.roundToPx()) }.size(92.dp).clip(RoundedCornerShape(30.dp)).background(Fresh.accent),
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

private enum class Swipe { ADD, SKIP, EDIT }

/**
 * Suggested as a deck of cards: swipe the top card right to add it, left to skip it, up to edit it
 * first. The buttons under the deck do the same, for one-handed taps and for TalkBack.
 */
@Composable
private fun SuggestedDeck(
    items: List<Item>,
    vm: MainVM,
    onEdit: (Item) -> Unit,
    onSync: () -> Unit,
    onAdded: (Item) -> Unit,
    onSkipped: (Item) -> Unit
) {
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
    val top = items.first()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    // One offset per top card; a new top card starts centred.
    val offset = remember(top.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val fling: (Swipe) -> Unit = { action ->
        scope.launch {
            val away = with(density) { 600.dp.toPx() }
            offset.animateTo(
                when (action) {
                    Swipe.ADD -> Offset(away, offset.value.y)
                    Swipe.SKIP -> Offset(-away, offset.value.y)
                    Swipe.EDIT -> Offset(0f, -away)
                },
                tween(220)
            )
            when (action) {
                Swipe.ADD -> { vm.confirm(top); onAdded(top) }
                Swipe.SKIP -> { vm.dismiss(top); onSkipped(top) }
                Swipe.EDIT -> onEdit(top)
            }
            // Edit leaves the card in the deck, and a failed write keeps it: bring it back after a beat.
            delay(if (action == Swipe.EDIT) 300 else 1500)
            offset.snapTo(Offset.Zero)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().widthIn(max = 480.dp), contentAlignment = Alignment.BottomCenter) {
            val cardHeight = minOf(maxHeight - 24.dp, 440.dp)
            // Up to two cards peek out behind the top one.
            items.take(3).asReversed().forEachIndexed { i, item ->
                val depth = minOf(items.size, 3) - 1 - i
                val isTop = depth == 0
                SuggestionCard(
                    item,
                    offset = if (isTop) offset.value else Offset.Zero,
                    threshold = threshold,
                    modifier = Modifier.fillMaxWidth().height(cardHeight)
                        .graphicsLayer {
                            if (isTop) {
                                translationX = offset.value.x
                                translationY = offset.value.y
                                rotationZ = offset.value.x / 40f
                            } else {
                                translationY = -depth * 12.dp.toPx()
                                val s = 1f - depth * 0.05f
                                scaleX = s; scaleY = s
                            }
                        }
                        .then(
                            if (isTop) Modifier.pointerInput(item.id) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val (x, y) = offset.value
                                        when {
                                            x > threshold -> fling(Swipe.ADD)
                                            x < -threshold -> fling(Swipe.SKIP)
                                            y < -threshold && abs(x) < threshold / 2 -> fling(Swipe.EDIT)
                                            else -> scope.launch { offset.animateTo(Offset.Zero, spring(dampingRatio = 0.6f)) }
                                        }
                                    },
                                    onDragCancel = { scope.launch { offset.animateTo(Offset.Zero, spring(dampingRatio = 0.6f)) } }
                                ) { change, drag ->
                                    change.consume()
                                    scope.launch { offset.snapTo(offset.value + drag) }
                                }
                            } else Modifier
                        )
                )
            }
        }
        Row(
            Modifier.padding(top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeckButton(Icons.Rounded.Close, "Skip", Fresh.Coral, 54.dp) { fling(Swipe.SKIP) }
            DeckButton(Icons.Rounded.Edit, "Edit first", Fresh.Indigo, 44.dp) { fling(Swipe.EDIT) }
            DeckButton(Icons.Rounded.Check, "Add", Fresh.Green, 54.dp) { fling(Swipe.ADD) }
        }
    }
}

@Composable
private fun DeckButton(icon: ImageVector, label: String, tint: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).glass(CircleShape, strong = true).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** One suggestion as a card: source, title, summary, and chips for who / when / time / context. */
@Composable
private fun SuggestionCard(item: Item, offset: Offset, threshold: Float, modifier: Modifier = Modifier) {
    val tint = sourceColor(item.source)
    Box(modifier.glass(RoundedCornerShape(26.dp), strong = true)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(sourceIcon(item.source), null, tint = tint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when (item.source) {
                        "GMAIL" -> "From Gmail"
                        "CALENDAR" -> "From Calendar"
                        else -> "Added by you"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = tint
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(item.title, style = MaterialTheme.typography.headlineSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (item.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    item.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fresh.TextDim,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            DraftChips(item)
        }
        // Stamps fade in as the card is dragged toward each action.
        Stamp("ADD", Fresh.Green, alpha = (offset.x / threshold).coerceIn(0f, 1f), Modifier.align(Alignment.TopEnd).padding(18.dp).rotate(12f))
        Stamp("SKIP", Fresh.Coral, alpha = (-offset.x / threshold).coerceIn(0f, 1f), Modifier.align(Alignment.TopStart).padding(18.dp).rotate(-12f))
        Stamp(
            "EDIT", Fresh.Indigo,
            alpha = if (abs(offset.x) < threshold / 2) (-offset.y / threshold).coerceIn(0f, 1f) else 0f,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        )
    }
}

@Composable
private fun Stamp(text: String, color: Color, alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
        color = color,
        modifier = modifier.graphicsLayer { this.alpha = alpha }
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ---------------------------------------------------------------- Actions

private data class Section(val key: String, val label: String, val items: List<Item>)

/** Today / Tomorrow / Later pages (Today on launch), switched by a compact tab row right above the dock. */
@Composable
private fun ActionsList(
    actions: List<Item>,
    group: Group,
    vm: MainVM,
    onEdit: (Item) -> Unit,
    onDelete: (Item) -> Unit
) {
    val pager = rememberPagerState { DayPage.entries.size }
    val scope = rememberCoroutineScope()
    val t = today()
    val pages = remember(actions, t) { DayPage.entries.map { actions.onPage(it, t) } }
    val counts = remember(pages) { pages.map { list -> list.count { it.status == STATUS_OPEN && !it.isUpcomingRepeat() } } }
    var showUpcoming by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Rows already swipe sideways to delete, so pages change from the tabs only.
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = false, key = { it }) { index ->
            DayPageList(
                DayPage.entries[index], pages[index], group, showUpcoming,
                onToggleUpcoming = { showUpcoming = !showUpcoming },
                vm = vm, onEdit = onEdit, onDelete = onDelete
            )
        }
        DayTabs(
            pager.currentPage, counts,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
    }
}

/** Segmented Today | Tomorrow | Later control; each segment shows its open count. */
@Composable
private fun DayTabs(selected: Int, counts: List<Int>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(38.dp).glass(PILL, strong = true).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayPage.entries.forEachIndexed { i, page ->
            val isSelected = selected == i
            val fg by animateColorAsState(if (isSelected) Color.White else Fresh.TextDim, label = "dayTabFg")
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(PILL)
                    .then(if (isSelected) Modifier.background(Fresh.accentHorizontal) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    page.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(5.dp))
                CountBadge(counts[i], isSelected)
            }
        }
    }
}

@Composable
private fun DayPageList(
    page: DayPage,
    actions: List<Item>,
    group: Group,
    showUpcoming: Boolean,
    onToggleUpcoming: () -> Unit,
    vm: MainVM,
    onEdit: (Item) -> Unit,
    onDelete: (Item) -> Unit
) {
    // Regrouped only when the page's list, grouping or Upcoming toggle changes, not on every recomposition.
    val (upcoming, sections) = remember(actions, group, showUpcoming) { buildSections(actions, group, showUpcoming) }

    // Anchored to the bottom (reverseLayout): the first section's first row sits right above the
    // tabs, later sections float up, and a short list rests on the dock instead of the top bar.
    // So each section emits its rows first and its header last, which puts the header above them.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        reverseLayout = true
    ) {
        if (sections.isEmpty()) {
            item(key = "empty") {
                val (title, body) = when (page) {
                    DayPage.TODAY -> "All clear today" to "Nothing due or late. Type a task below, or check Tomorrow."
                    DayPage.TOMORROW -> "Nothing for tomorrow" to "Actions due tomorrow show up here."
                    DayPage.LATER -> "Nothing planned later" to "Actions due after tomorrow show up here."
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim)
                }
            }
        }
        sections.forEach { section ->
            items(section.items, key = { it.id }) { item ->
                ActionRow(
                    item, if (section.key == UPCOMING_KEY) Group.TIME else group,
                    onToggle = { vm.toggleDone(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    modifier = Modifier.animateItem()
                )
            }
            item(key = section.key) {
                if (section.key == UPCOMING_KEY) {
                    GroupHeader(
                        section.label, upcoming.size, group,
                        Modifier.animateItem().clickable(onClick = onToggleUpcoming),
                        trailing = if (showUpcoming) "Hide" else "Show"
                    )
                } else {
                    GroupHeader(section.label, section.items.size, group, Modifier.animateItem())
                }
            }
        }
    }
}

private const val UPCOMING_KEY = "upcoming-header"

/** The Upcoming repeats, and the list's sections: the open actions grouped by [group], then Upcoming and Done. */
private fun buildSections(actions: List<Item>, group: Group, showUpcoming: Boolean): Pair<List<Item>, List<Section>> {
    // Repeats more than a week out are folded under "Upcoming" so the list stays about now.
    val (upcoming, open) = actions.filter { it.status == STATUS_OPEN }.partition { it.isUpcomingRepeat() }
    val done = actions.filter { it.status == STATUS_DONE }
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
    return upcoming to sections
}

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
                .glass(ROW_SHAPE, strong = true)
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
                        photo != null -> if (photo.fromPhoto) "Photo draft" else "Shared draft"
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
