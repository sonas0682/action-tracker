package com.sonas.actiontracker.ui

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonas.actiontracker.R
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.GOD_LAMP
import com.sonas.actiontracker.sync.GodTasks
import com.sonas.actiontracker.sync.THOUGHT_MARK
import com.sonas.actiontracker.sync.ThoughtTasks
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The layout of each day page (Now, Near, Next), kept in the "ui" prefs beside the theme
 * (no schema change). "All pages" in the picker simply writes the three at once.
 */
object LayoutPref {
    private const val KEY = "layout"
    private fun key(page: DayPage) = "layout_${page.name}"
    private fun prefs(context: Context) = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var byPage by mutableStateOf<Map<DayPage, AppLayout>>(emptyMap())
        private set

    fun of(page: DayPage): AppLayout = byPage[page] ?: AppLayout.CLASSIC

    fun load(context: Context) {
        val p = prefs(context)
        // KEY is the one-for-all choice from the first build of this feature; a page's own key wins.
        val all = p.getString(KEY, null)
        byPage = DayPage.entries.associateWith { AppLayout.of(p.getString(key(it), null) ?: all) }
    }

    /** Sets [page]'s layout, or every page's when [page] is null. */
    fun set(context: Context, page: DayPage?, value: AppLayout) {
        val pages = if (page == null) DayPage.entries else listOf(page)
        val edit = prefs(context).edit()
        pages.forEach { edit.putString(key(it), value.name) }
        edit.apply()
        byPage = byPage + pages.associateWith { value }
    }
}

/** What a row can do in every layout: tick, open the edit sheet, delete, or swap a God task. */
internal class RowActions(
    val toggle: (Item) -> Unit,
    val edit: (Item) -> Unit,
    val delete: (Item) -> Unit,
    val replace: (Item) -> Unit
)

private val LAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val LAY_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private fun layDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(LAY_DATE)

private val Item.done: Boolean get() = status == STATUS_DONE

/** A steady colour per context, so a context keeps its badge colour from day to day. */
@Composable
private fun contextColor(item: Item): Color = when {
    GodTasks.isGod(item) -> Fresh.Gold
    ThoughtTasks.isThought(item) -> Fresh.Indigo
    else -> when (Math.floorMod(item.context.lowercase().hashCode(), 5)) {
        0 -> Fresh.Indigo
        1 -> Fresh.Coral
        2 -> Fresh.Teal
        3 -> Fresh.Amber
        else -> Fresh.Sky
    }
}

/**
 * A Now / Near / Next page drawn in one of the non-Classic layouts. Every layout keeps the same
 * gestures as Classic: tap a row to edit, long-press for Delete (or Another God task), tap its
 * mark to tick it (with a short buzz). Far-off repeats fold under "Upcoming" at the end, the
 * Thought of the day keeps its quote card, and a finished page closes with a line of its own.
 */
@Composable
internal fun LayoutPage(
    layout: AppLayout,
    page: DayPage,
    group: Group,
    items: List<Item>,
    all: List<Item>,
    showUpcoming: Boolean,
    onToggleUpcoming: () -> Unit,
    act: RowActions
) {
    val today = LocalDate.now().toEpochDay()
    val split = remember(items, today) { splitUpcoming(items, today) }
    val upcoming = split.first
    val shown = split.second
    val thoughts = remember(shown) { shown.filter { ThoughtTasks.isThought(it) } }
    val tasks = remember(shown) { shown.filterNot { ThoughtTasks.isThought(it) } }
    val sections = remember(tasks, page, today) { layoutSections(tasks, page, today, ::layDate) }
    if (shown.isEmpty() && upcoming.isEmpty()) {
        val (title, body) = when (page) {
            DayPage.TODAY -> "All clear today" to "Nothing due or late. Type a task below, or check Near."
            DayPage.TOMORROW -> "Nothing for tomorrow" to "Actions due tomorrow show up here."
            DayPage.LATER -> "Nothing planned next" to "Actions due after tomorrow show up here."
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim, textAlign = TextAlign.Center)
        }
        return
    }
    val haptic = LocalHapticFeedback.current
    val acts = RowActions(
        toggle = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); act.toggle(it) },
        edit = act.edit, delete = act.delete, replace = act.replace
    )
    val up = Upcoming(upcoming, showUpcoming, onToggleUpcoming)
    val data = PageData(page, sections, tasks, thoughts, shown, today, allDone(shown), up)
    when (layout) {
        AppLayout.LEDGER -> LedgerPage(data, group, acts)
        AppLayout.QUEST -> QuestPage(data, all, acts)
        AppLayout.DIAL -> DialPage(data, acts)
        AppLayout.JOURNAL -> JournalPage(data, acts)
        AppLayout.METRO -> MetroPage(data, acts)
        AppLayout.CLASSIC -> Unit
    }
}

private class Upcoming(val items: List<Item>, val show: Boolean, val onToggle: () -> Unit)

/** What every layout page is drawn from: [tasks] is [shown] without the Thought of the day rows. */
private class PageData(
    val page: DayPage,
    val sections: List<LaySection>,
    val tasks: List<Item>,
    val thoughts: List<Item>,
    val shown: List<Item>,
    val today: Long,
    val finished: Boolean,
    val up: Upcoming
)

/** The folded "Upcoming repeats" block every layout ends with. */
private fun LazyListScope.upcomingRows(up: Upcoming, text: Color, act: RowActions) {
    if (up.items.isEmpty()) return
    item(key = "upcoming") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = up.onToggle).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Upcoming repeats  ·  ${up.items.size}", style = MaterialTheme.typography.labelLarge, color = text, modifier = Modifier.weight(1f))
            Text(if (up.show) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = text)
        }
    }
    if (up.show) {
        items(up.items, key = { it.id }) { item ->
            Box(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                PlainRow(item, "${layDate(item.dueDate)}  ·  ${item.context}", Fresh.TextDim, act)
            }
        }
    }
}

/** The Thought of the day on its own quote card, as in Classic (Journal Page prints it as the epigraph instead). */
private fun LazyListScope.thoughtCards(thoughts: List<Item>, act: RowActions, pad: Dp) {
    items(thoughts, key = { it.id }) { item ->
        Box(Modifier.padding(horizontal = pad, vertical = 4.dp)) {
            ThoughtRow(item, onToggle = { act.toggle(item) }, onEdit = { act.edit(item) }, onDelete = { act.delete(item) })
        }
    }
}

/** A finished page's ending: the layout's own words for it, then a closing thought. */
private fun LazyListScope.finale(layout: AppLayout, d: PageData, title: Color, body: Color, family: FontFamily? = null) {
    if (!d.finished) return
    item(key = "finale") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(finaleTitle(layout), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = family, color = title)
            Spacer(Modifier.height(4.dp))
            Text(finaleLine(d.today), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = body, textAlign = TextAlign.Center)
        }
    }
}

/** A mark's little bounce when its task is ticked. */
@Composable
private fun popScale(done: Boolean): Float {
    val pop by animateFloatAsState(
        if (done) 1f else 0.88f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop"
    )
    return pop
}

/** Tap to edit, long-press for the Delete menu: the same gestures as a Classic row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowMenuBox(item: Item, act: RowActions, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val canReplace = GodTasks.isGod(item) && item.status == STATUS_OPEN
    Box(
        modifier.combinedClickable(
            onClick = { act.edit(item) },
            onLongClickLabel = if (canReplace) "Another God task or delete" else "Delete options",
            onLongClick = { menu = true }
        )
    ) {
        content()
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (canReplace) {
                DropdownMenuItem(
                    text = { Text("Another God task") },
                    leadingIcon = { Text(GOD_LAMP) },
                    onClick = { menu = false; act.replace(item) }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Fresh.Coral) },
                onClick = { menu = false; act.delete(item) }
            )
        }
    }
}

/** A 44dp touch target around a small tick mark of any shape. */
@Composable
private fun TickTarget(item: Item, act: RowActions, width: Dp = 44.dp, mark: @Composable () -> Unit) {
    Box(
        Modifier.width(width).heightIn(min = 44.dp)
            .clickable(onClickLabel = if (item.done) "Mark not done" else "Mark done", role = Role.Checkbox) { act.toggle(item) },
        contentAlignment = Alignment.Center
    ) { mark() }
}

@Composable
private fun TickMark(done: Boolean, color: Color, shape: Shape, size: Dp = 20.dp) {
    Box(
        Modifier.size(size).scale(popScale(done)).clip(shape)
            .then(if (done) Modifier.background(color) else Modifier.border(2.dp, color, shape)),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Rounded.Check, null, tint = Fresh.GlassStrong, modifier = Modifier.size(size - 6.dp))
    }
}

/** A plain white card row: round tick, title and one line under it. Used by Day Dial and Upcoming. */
@Composable
private fun PlainRow(item: Item, sub: String, subColor: Color, act: RowActions) {
    val shape = RoundedCornerShape(14.dp)
    RowMenuBox(item, act, Modifier.fillMaxWidth().glass(shape, strong = true)) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TickTarget(item, act, 48.dp) { TickMark(item.done, if (item.done) Fresh.Teal else Fresh.Indigo, CircleShape) }
            Column(Modifier.weight(1f)) {
                TitleText(item, 14f)
                Text(sub, style = MaterialTheme.typography.labelMedium, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TitleText(
    item: Item,
    sizeSp: Float,
    modifier: Modifier = Modifier,
    color: Color = Fresh.Text,
    dim: Color = Fresh.TextDim,
    family: FontFamily? = null,
    style: FontStyle? = null
) {
    Text(
        item.title,
        modifier = modifier,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = family,
        fontStyle = style,
        color = if (item.done) dim else color,
        textDecoration = if (item.done) TextDecoration.LineThrough else null,
        maxLines = 2, overflow = TextOverflow.Ellipsis
    )
}

// ---------------------------------------------------------------- 1. Ledger

private val MONO = FontFamily.Monospace

/**
 * Professional: one flat table. The day's numbers on top (tap Open, Late or Done to show only
 * those rows), then mono-coded rows under each block of the dock's group-by choice.
 */
@Composable
private fun LedgerPage(d: PageData, group: Group, act: RowActions) {
    val today = d.today
    var filter by remember { mutableStateOf<LedgerFilter?>(null) }
    val sections = remember(d.tasks, group, d.page, today, filter) {
        ledgerSections(d.tasks.ledgerFilter(filter, today), group, d.page, today, ::layDate)
    }
    val open = d.shown.count { !it.done }
    val late = d.shown.count { it.isLate(today) }
    val done = d.shown.count { it.done }
    val closed = if (d.shown.isEmpty()) 0 else done * 100 / d.shown.size
    val bar by animateFloatAsState(closed / 100f, label = "closed")
    val sheet = RoundedCornerShape(12.dp)
    fun pick(f: LedgerFilter) { filter = if (filter == f) null else f }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item(key = "kpi") {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).glass(sheet, strong = true)) {
                Row(Modifier.fillMaxWidth()) {
                    LedgerKpi("$open", "Open", Fresh.Text, filter == LedgerFilter.OPEN, Modifier.weight(1f)) { pick(LedgerFilter.OPEN) }
                    LedgerKpi("$late", "Late", if (late > 0) Fresh.Coral else Fresh.Text, filter == LedgerFilter.LATE, Modifier.weight(1f)) { pick(LedgerFilter.LATE) }
                    LedgerKpi("$done", "Done", Fresh.Text, filter == LedgerFilter.DONE, Modifier.weight(1f)) { pick(LedgerFilter.DONE) }
                    LedgerKpi("$closed%", "Closed", Fresh.Indigo, false, Modifier.weight(1f)) { filter = null }
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(Fresh.Rim)) {
                    Box(Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).height(4.dp).background(Fresh.Indigo))
                }
            }
        }
        thoughtCards(d.thoughts, act, 0.dp)
        if (sections.isEmpty() && filter != null) {
            item(key = "no-rows") {
                Text(
                    "No rows match. Tap the number again to show everything.",
                    style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim, modifier = Modifier.padding(16.dp)
                )
            }
        }
        sections.forEach { section ->
            item(key = section.key) {
                Row(
                    Modifier.fillMaxWidth().background(Fresh.Canvas).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        section.label.uppercase(), fontFamily = MONO, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        color = if (section.late) Fresh.Coral else Fresh.TextDim, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${section.items.size}", fontFamily = MONO, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Fresh.TextDim)
                }
            }
            items(section.items, key = { it.id }) { item -> LedgerRow(item, group, today, act) }
        }
        finale(AppLayout.LEDGER, d, Fresh.Text, Fresh.TextDim, MONO)
        upcomingRows(d.up, Fresh.TextDim, act)
    }
}

@Composable
private fun LedgerKpi(value: String, label: String, color: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(if (on) Fresh.Indigo.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClickLabel = "Show only $label", role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(value, fontFamily = MONO, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Fresh.TextDim, maxLines = 1)
    }
}

@Composable
private fun LedgerRow(item: Item, group: Group, today: Long, act: RowActions) {
    val late = item.isLate(today)
    val rule = Fresh.Rim
    RowMenuBox(
        item, act,
        Modifier.fillMaxWidth().background(Fresh.GlassStrong).drawBehind {
            drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TickTarget(item, act) { TickMark(item.done, if (late) Fresh.Coral else Fresh.TextDim, RoundedCornerShape(4.dp), 18.dp) }
            TitleText(item, 14f, Modifier.weight(1f).padding(vertical = 6.dp))
            Text(
                personCode(item.person), fontFamily = MONO, fontSize = 11.sp, color = Fresh.TextDim,
                modifier = Modifier.width(38.dp), textAlign = TextAlign.End, maxLines = 1
            )
            Text(
                when {
                    late -> "-${today - item.dueDate}D"
                    GodTasks.isGod(item) -> "GOD"
                    group == Group.CONTEXT && item.dueDate > today + 1 -> layDate(item.dueDate).substringAfter(' ').uppercase()
                    group == Group.CONTEXT -> TimeOfDay.of(item.timeOfDay).label.uppercase()
                    else -> contextCode(item.context)
                },
                fontFamily = MONO, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = if (late) Fresh.Coral else Fresh.Text,
                modifier = Modifier.width(72.dp), textAlign = TextAlign.End, maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------- 2. Quest Path

/** Where each step sits across the board, cycling left → middle → right → middle. */
private val QUEST_X = floatArrayOf(0.06f, 0.40f, 0.74f, 0.40f)
private val QUEST_NODE = 56.dp
private val QUEST_BOARD = RoundedCornerShape(20.dp)

/**
 * Game: finished steps lie behind you on a winding trail, the next one says GO, God tasks are gold.
 * The XP bar and count move as steps are ticked, and reaching a level is announced for a few seconds.
 */
@Composable
private fun QuestPage(d: PageData, all: List<Item>, act: RowActions) {
    val today = d.today
    val xp = remember(all) { Quest.totalXp(all) }
    val earnedToday = remember(all, today) { Quest.xpOn(all, today) }
    val streak = remember(all, today) { Quest.streak(Quest.doneDays(all), today) }
    val level = Quest.levelOf(xp)
    val floor = Quest.xpToReach(level)
    val ceiling = Quest.xpToReach(level + 1)
    val xpShown by animateIntAsState(xp, label = "xp")
    val part by animateFloatAsState(((xp - floor).toFloat() / (ceiling - floor)).coerceIn(0f, 1f), label = "bar")
    // Level-up moment: only when the level rises while this page is open.
    var seenLevel by remember { mutableIntStateOf(level) }
    var cheer by remember { mutableStateOf(false) }
    LaunchedEffect(level) {
        if (level > seenLevel) {
            seenLevel = level
            cheer = true
            delay(4_000)
            cheer = false
        } else {
            seenLevel = level
        }
    }
    // The trail: what is finished first, then what is open in reading order.
    val steps = remember(d.sections) {
        val flat = d.sections.flatMap { s -> s.items.map { it to s } }
        flat.filter { it.first.done } + flat.filterNot { it.first.done }
    }
    val current = steps.indexOfFirst { !it.first.done }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp).clip(QUEST_BOARD).background(Fresh.QuestNight),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item(key = "level") {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (cheer) "Level up!  Level $level" else "Level $level  ·  ${Quest.title(level)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (cheer) Fresh.QuestGold else Fresh.QuestText,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (streak == 1) "1 day streak" else "$streak day streak",
                        style = MaterialTheme.typography.labelLarge, color = if (streak > 0) Fresh.QuestGold else Fresh.QuestDim
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Fresh.QuestTrack)) {
                    Box(Modifier.fillMaxWidth(part.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(Fresh.QuestGold))
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("$xpShown / $ceiling XP", style = MaterialTheme.typography.labelMedium, color = Fresh.QuestDim, modifier = Modifier.weight(1f))
                    Text(
                        if (cheer) "You are now ${Quest.title(level)}" else "+$earnedToday XP today",
                        style = MaterialTheme.typography.labelMedium, color = if (earnedToday > 0 || cheer) Fresh.QuestMint else Fresh.QuestDim
                    )
                }
            }
        }
        thoughtCards(d.thoughts, act, 10.dp)
        itemsIndexed(steps, key = { _, step -> step.first.id }) { index, step ->
            val item = step.first
            val sub = when {
                item.done -> "Done"
                step.second.late -> lateText(item.dueDate, today)
                else -> step.second.label
            } + "  ·  +${Quest.xpFor(item)} XP"
            QuestRow(
                index = index, count = steps.size, item = item, sub = sub,
                current = index == current, behindDone = index > 0 && steps[index - 1].first.done, act = act
            )
        }
        finale(AppLayout.QUEST, d, Fresh.QuestGold, Fresh.QuestDim)
        upcomingRows(d.up, Fresh.QuestDim, act)
    }
}

@Composable
private fun QuestRow(index: Int, count: Int, item: Item, sub: String, current: Boolean, behindDone: Boolean, act: RowActions) {
    val frac = QUEST_X[index % QUEST_X.size]
    val before = if (index > 0) QUEST_X[(index - 1) % QUEST_X.size] else frac
    val after = if (index < count - 1) QUEST_X[(index + 1) % QUEST_X.size] else frac
    val track = Fresh.QuestTrack
    val mint = Fresh.QuestMint
    val god = GodTasks.isGod(item)
    RowMenuBox(
        item, act,
        Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 12.dp).drawBehind {
            val node = QUEST_NODE.toPx()
            fun x(f: Float) = f * (size.width - node) + node / 2
            val cx = x(frac)
            val cy = size.height / 2
            val trail = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
            // Each half is an S-curve that leaves and arrives straight down, so rows join smoothly.
            if (index > 0) {
                val top = (x(before) + cx) / 2
                val path = Path().apply { moveTo(top, 0f); cubicTo(top, cy / 2, cx, cy / 2, cx, cy) }
                drawPath(path, if (behindDone) mint else track, style = trail)
            }
            if (index < count - 1) {
                val bottom = (cx + x(after)) / 2
                val path = Path().apply { moveTo(cx, cy); cubicTo(cx, cy * 1.5f, bottom, cy * 1.5f, bottom, size.height) }
                drawPath(path, if (item.done) mint else track, style = trail)
            }
        }
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val labelLeft = frac > 0.5f
            if (labelLeft) {
                QuestLabel(item, sub, god, TextAlign.End, Modifier.weight(frac))
            } else {
                Spacer(Modifier.weight(frac))
            }
            Box(
                Modifier.size(QUEST_NODE).scale(popScale(item.done || current)).clip(CircleShape)
                    .then(
                        when {
                            item.done -> Modifier.background(mint)
                            god -> Modifier.background(Fresh.QuestGold)
                            current -> Modifier.background(Fresh.QuestNight).border(3.dp, Fresh.QuestGold, CircleShape)
                            else -> Modifier.background(Fresh.QuestNight).border(3.dp, Fresh.QuestDim.copy(alpha = 0.55f), CircleShape)
                        }
                    )
                    .clickable(onClickLabel = if (item.done) "Mark not done" else "Mark done", role = Role.Checkbox) { act.toggle(item) },
                contentAlignment = Alignment.Center
            ) {
                when {
                    item.done -> Icon(Icons.Rounded.Check, null, tint = Fresh.QuestNight, modifier = Modifier.size(26.dp))
                    god -> Text(GOD_LAMP, fontSize = 22.sp)
                    current -> Text("GO", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Fresh.QuestGold)
                    else -> Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Fresh.QuestDim)
                }
            }
            if (labelLeft) {
                Spacer(Modifier.weight(1f - frac))
            } else {
                QuestLabel(item, sub, god, TextAlign.Start, Modifier.weight(1f - frac))
            }
        }
    }
}

@Composable
private fun QuestLabel(item: Item, sub: String, god: Boolean, align: TextAlign, modifier: Modifier) {
    Column(modifier.padding(horizontal = 10.dp)) {
        Text(
            item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = align, modifier = Modifier.fillMaxWidth(),
            color = if (item.done) Fresh.QuestDim else Fresh.QuestText,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(
            if (god && !item.done) "Daily quest  ·  $sub" else sub,
            fontSize = 12.sp, textAlign = align, modifier = Modifier.fillMaxWidth(),
            color = if (god && !item.done) Fresh.QuestGold else Fresh.QuestDim, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------- 3. Day Dial

/**
 * Fitness rings: four arcs, Morning at the top right going clockwise, each filling as its tasks
 * close. A dot rides the ring at the current time on Now, and late tasks from other parts of
 * the day are always listed first so none hides behind an arc.
 */
@Composable
private fun DialPage(d: PageData, act: RowActions) {
    val page = d.page
    val today = d.today
    val bySlot = remember(d.shown) { d.shown.groupBy { TimeOfDay.of(it.timeOfDay) } }
    var pickedName by remember(page) {
        val start = if (page == DayPage.TODAY) slotAt(LocalTime.now().hour)
        else TimeOfDay.entries.firstOrNull { !bySlot[it].isNullOrEmpty() } ?: TimeOfDay.MORNING
        mutableStateOf(start.name)
    }
    val picked = TimeOfDay.of(pickedName)
    val inSlot = bySlot[picked] ?: emptyList()
    val rows = remember(d.tasks, picked) { d.tasks.filter { TimeOfDay.of(it.timeOfDay) == picked }.sortedBy { it.done } }
    val lateElsewhere = remember(d.tasks, picked, today) { d.tasks.filter { it.isLate(today) && TimeOfDay.of(it.timeOfDay) != picked } }
    val slotThoughts = remember(d.thoughts, picked) { d.thoughts.filter { TimeOfDay.of(it.timeOfDay) == picked } }
    val doneAll = d.shown.count { it.done }
    val late = d.shown.count { it.isLate(today) }
    val colors = TimeOfDay.entries.map { timeColor(it) }
    val parts = TimeOfDay.entries.map { slot ->
        val list = bySlot[slot] ?: emptyList()
        animateFloatAsState(if (list.isEmpty()) 0f else list.count { it.done }.toFloat() / list.size, label = slot.name).value
    }
    val track = Fresh.TextDim.copy(alpha = 0.22f)
    val hand = Fresh.Text
    val handCore = Fresh.GlassStrong
    val nowAngle = if (page == DayPage.TODAY) LocalTime.now().let { dialAngle(it.hour, it.minute) } else null
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "dial") {
            Box(Modifier.size(228.dp), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(onTap = { p ->
                            val angle = Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble()))
                            val fromTop = (angle + 90.0 + 360.0) % 360.0
                            pickedName = TimeOfDay.entries[(fromTop / 90.0).toInt().coerceIn(0, 3)].name
                        })
                    }
                ) {
                    val stroke = 16.dp.toPx()
                    val inset = stroke
                    val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
                    val corner = Offset(inset, inset)
                    TimeOfDay.entries.forEachIndexed { i, slot ->
                        val start = -90f + i * 90f + 6f
                        val on = slot == picked
                        val w = if (on) stroke * 1.4f else stroke
                        drawArc(if (on) colors[i].copy(alpha = 0.30f) else track, start, 78f, false, corner, arc, style = Stroke(w, cap = StrokeCap.Round))
                        if (parts[i] > 0.001f) drawArc(colors[i], start, 78f * parts[i], false, corner, arc, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    if (nowAngle != null) {
                        val rad = Math.toRadians((nowAngle - 90f).toDouble())
                        val r = arc.width / 2
                        val at = Offset(size.width / 2 + r * cos(rad).toFloat(), size.height / 2 + r * sin(rad).toFloat())
                        drawCircle(hand, 8.dp.toPx(), at)
                        drawCircle(handCore, 4.dp.toPx(), at)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$doneAll / ${d.shown.size}", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Fresh.Text)
                    Text(
                        if (late > 0) "$late late" else "done",
                        style = MaterialTheme.typography.labelLarge, color = if (late > 0) Fresh.Coral else Fresh.TextDim
                    )
                }
            }
        }
        item(key = "slots") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimeOfDay.entries.forEachIndexed { i, slot ->
                    val list = bySlot[slot] ?: emptyList()
                    val on = slot == picked
                    val pill = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.weight(1f).heightIn(min = 48.dp).glass(pill, strong = true)
                            .then(if (on) Modifier.border(2.dp, colors[i], pill) else Modifier)
                            .clickable(role = Role.Tab) { pickedName = slot.name }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(slot.label, style = MaterialTheme.typography.labelMedium, color = if (on) Fresh.Text else Fresh.TextDim, maxLines = 1)
                        Text(
                            "${list.count { it.done }}/${list.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (list.isEmpty()) Fresh.TextDim else colors[i]
                        )
                    }
                }
            }
        }
        items(lateElsewhere, key = { it.id }) { item ->
            PlainRow(item, "${lateText(item.dueDate, today)}  ·  ${TimeOfDay.of(item.timeOfDay).label}  ·  ${item.context}", Fresh.Coral, act)
        }
        thoughtCards(slotThoughts, act, 0.dp)
        if (inSlot.isEmpty() && lateElsewhere.isEmpty()) {
            item(key = "none") {
                Text(
                    "Nothing in the ${picked.label.lowercase()}. Tap another part of the ring.",
                    style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim, modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        }
        items(rows, key = { it.id }) { item ->
            val isLate = item.isLate(today)
            val sub = buildList {
                if (isLate) add(lateText(item.dueDate, today)) else if (page == DayPage.LATER) add(layDate(item.dueDate))
                add(if (GodTasks.isGod(item)) "God task" else item.context)
                if (item.person != "Me") add(item.person)
            }.joinToString("  ·  ")
            PlainRow(item, sub, if (isLate) Fresh.Coral else Fresh.TextDim, act)
        }
        finale(AppLayout.DIAL, d, Fresh.Text, Fresh.TextDim)
        upcomingRows(d.up, Fresh.TextDim, act)
    }
}

// ---------------------------------------------------------------- 4. Journal Page

private val JOURNAL_MARGIN = 46.dp

/** Caveat Bold (SIL Open Font License, licenses/Caveat-OFL.txt), bundled so the page looks handwritten on every phone. */
private val HAND = FontFamily(Font(R.font.caveat, FontWeight.Bold))

/** Journaling: a ruled page. The day's thought opens it; bullets are open, crosses are done. */
@Composable
private fun JournalPage(d: PageData, act: RowActions) {
    val today = d.today
    val done = d.shown.count { it.done }
    val open = d.shown.size - done
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp).clip(RoundedCornerShape(6.dp)).background(Fresh.Paper),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(d.thoughts, key = { it.id }) { item -> JournalRow(item, today, act, epigraph = true) }
        d.sections.forEach { section ->
            item(key = section.key) {
                Box(Modifier.fillMaxWidth().height(40.dp).journalLines(), contentAlignment = Alignment.BottomStart) {
                    Text(
                        section.label, fontFamily = HAND, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                        color = if (section.late) Fresh.PaperMargin else Fresh.PaperInk,
                        modifier = Modifier.padding(start = JOURNAL_MARGIN + 10.dp, bottom = 2.dp)
                    )
                }
            }
            items(section.items, key = { it.id }) { item -> JournalRow(item, today, act, epigraph = false) }
        }
        item(key = "tally") {
            Text(
                "$done crossed off, $open to go.",
                fontFamily = HAND, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Fresh.PaperInk,
                modifier = Modifier.padding(start = JOURNAL_MARGIN + 10.dp, top = 14.dp, end = 12.dp)
            )
        }
        finale(AppLayout.JOURNAL, d, Fresh.PaperInk, Fresh.TextDim, HAND)
        upcomingRows(d.up, Fresh.PaperInk, act)
    }
}

/** The page's ruling: a hairline under the row and the red margin line down the left. */
@Composable
private fun Modifier.journalLines(): Modifier {
    val rule = Fresh.PaperRule
    val margin = Fresh.PaperMargin
    return drawBehind {
        drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        drawLine(margin, Offset(JOURNAL_MARGIN.toPx(), 0f), Offset(JOURNAL_MARGIN.toPx(), size.height), 1.dp.toPx())
    }
}

@Composable
private fun JournalRow(item: Item, today: Long, act: RowActions, epigraph: Boolean) {
    val late = item.isLate(today)
    val god = GodTasks.isGod(item)
    RowMenuBox(item, act, Modifier.fillMaxWidth().journalLines()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TickTarget(item, act, JOURNAL_MARGIN) {
                Text(
                    when {
                        item.done -> "×"
                        late -> "!"
                        god || epigraph -> THOUGHT_MARK
                        else -> "•"
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.scale(popScale(item.done)),
                    color = when {
                        item.done -> Fresh.TextDim
                        late -> Fresh.PaperMargin
                        god -> Fresh.Gold
                        else -> Fresh.Text
                    }
                )
            }
            Column(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
                TitleText(item, if (epigraph) 16f else 17f, family = FontFamily.Serif, style = if (epigraph) FontStyle.Italic else null)
                if (epigraph && item.notes.isNotBlank()) {
                    Text(item.notes, fontFamily = FontFamily.Serif, fontSize = 13.sp, color = Fresh.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!epigraph) {
                Text(
                    when {
                        late -> lateText(item.dueDate, today)
                        god -> "God task"
                        else -> item.context.lowercase()
                    },
                    fontFamily = HAND, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = if (late) Fresh.PaperMargin else Fresh.TextDim,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 5. Metro Line

private val METRO_RAIL = 56.dp

/**
 * Maps and transit: one line down the page. Blocks are stations, tasks are stops, and each stop's
 * stretch of rail and badge take its context's colour, like the lines on a transit map.
 */
@Composable
private fun MetroPage(d: PageData, act: RowActions) {
    val sections = d.sections
    val today = d.today
    val nowSlot = slotAt(LocalTime.now().hour)
    // "You are here" sits before the first station at or after the current time of day (Now page only).
    val here = if (d.page != DayPage.TODAY) -1 else
        sections.indexOfFirst { it.slot != null && it.slot.ordinal >= nowSlot.ordinal }.let { if (it < 0) sections.size else it }
    val left = d.shown.count { !it.done }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp).glass(RoundedCornerShape(16.dp), strong = true),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        thoughtCards(d.thoughts, act, 10.dp)
        sections.forEachIndexed { index, section ->
            val passed = here >= 0 && index < here
            if (index == here) item(key = "here") { MetroHere(left) }
            item(key = section.key) {
                MetroRail(if (passed) Fresh.Text else Fresh.TextDim.copy(alpha = 0.35f), Modifier.height(44.dp), dot = {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Fresh.GlassStrong).border(4.dp, if (section.late) Fresh.Coral else Fresh.Text, CircleShape))
                }) {
                    Text(section.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Fresh.Text)
                    Spacer(Modifier.width(8.dp))
                    val n = section.items.count { !it.done }
                    Text(
                        when {
                            n == 0 -> "all done"
                            n == 1 -> "1 stop"
                            else -> "$n stops"
                        },
                        style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim
                    )
                }
            }
            items(section.items, key = { it.id }) { item -> MetroStop(item, today, act) }
        }
        if (here == sections.size) item(key = "here") { MetroHere(left) }
        finale(AppLayout.METRO, d, Fresh.Text, Fresh.TextDim)
        upcomingRows(d.up, Fresh.TextDim, act)
    }
}

/** One row on the line: the rail drawn behind a dot in a fixed left column, then the row's content. */
@Composable
private fun MetroRail(rail: Color, modifier: Modifier = Modifier, dot: @Composable () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().drawBehind {
            val x = METRO_RAIL.toPx() / 2
            drawLine(rail, Offset(x, 0f), Offset(x, size.height), 6.dp.toPx())
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(METRO_RAIL), contentAlignment = Alignment.Center) { dot() }
        content()
    }
}

@Composable
private fun MetroHere(left: Int) {
    MetroRail(Fresh.Text, Modifier.height(32.dp), dot = {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Fresh.GlassStrong).border(5.dp, Fresh.Indigo, CircleShape))
    }) {
        Text(
            "You are here  ·  ${LocalTime.now().format(LAY_CLOCK)}  ·  " + if (left == 1) "1 stop left" else "$left stops left",
            style = MaterialTheme.typography.labelLarge, color = Fresh.Indigo, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetroStop(item: Item, today: Long, act: RowActions) {
    val late = item.isLate(today)
    val line = contextColor(item)
    RowMenuBox(item, act, Modifier.fillMaxWidth()) {
        // A stop already made fades its stretch of rail; the ones ahead keep the line's full colour.
        MetroRail(if (item.done) line.copy(alpha = 0.35f) else line, Modifier.heightIn(min = 52.dp), dot = {
            TickTarget(item, act, METRO_RAIL) {
                val ring = if (late) Fresh.Coral else Fresh.Text
                Box(
                    Modifier.size(14.dp).scale(popScale(item.done)).clip(CircleShape)
                        .background(if (item.done) ring else Fresh.GlassStrong).border(3.dp, ring, CircleShape)
                )
            }
        }) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                TitleText(item, 14f)
                Text(
                    when {
                        item.done -> "Done"
                        late -> "Delayed ${today - item.dueDate} " + if (today - item.dueDate == 1L) "day" else "days"
                        GodTasks.isGod(item) -> "God task line"
                        else -> "${item.context} line" + if (item.person != "Me") "  ·  ${item.person}" else ""
                    },
                    style = MaterialTheme.typography.labelMedium, color = if (late) Fresh.Coral else Fresh.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier.padding(start = 8.dp, end = 14.dp).widthIn(min = 26.dp).height(22.dp)
                    .clip(RoundedCornerShape(5.dp)).background(line).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.context.trim().take(1).uppercase().ifEmpty { "-" },
                    fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Fresh.GlassStrong
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

/**
 * ⋮ → Settings → Look → Layout: pick for every page at once, or for Now, Near and Next one by
 * one. Each choice has a small picture of itself and applies as soon as it is tapped.
 */
@Composable
internal fun LayoutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    // null = all pages.
    var scopeName by remember { mutableStateOf<String?>(null) }
    val scope = DayPage.entries.firstOrNull { it.name == scopeName }
    val current = if (scope != null) LayoutPref.of(scope)
    else LayoutPref.of(DayPage.TODAY).takeIf { all -> DayPage.entries.all { LayoutPref.of(it) == all } }
    val segment = RoundedCornerShape(50)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Layout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "How the Now, Near and Next pages are drawn. Everything else stays where it is.",
                    style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(38.dp).well(segment).padding(3.dp)) {
                    (listOf<DayPage?>(null) + DayPage.entries).forEach { option ->
                        val on = option == scope
                        Box(
                            Modifier.weight(1f).height(32.dp).clip(segment)
                                .background(if (on) Fresh.Indigo else Color.Transparent)
                                .clickable(role = Role.Tab) { scopeName = option?.name },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                option?.label ?: "All pages", style = MaterialTheme.typography.labelMedium,
                                color = if (on) Color.White else Fresh.Text, maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                AppLayout.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(role = Role.RadioButton) { LayoutPref.set(context, scope, option) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == option, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        LayoutThumb(option)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.titleSmall, color = Fresh.Text)
                            Text(option.blurb, style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** A thumbnail of a layout, drawn from a few shapes in its own colours. */
@Composable
private fun LayoutThumb(layout: AppLayout) {
    val shape = RoundedCornerShape(8.dp)
    val ground = when (layout) {
        AppLayout.QUEST -> Fresh.QuestNight
        AppLayout.JOURNAL -> Fresh.Paper
        else -> Fresh.Canvas
    }
    val card = Fresh.GlassStrong
    val ink = Fresh.TextDim
    val dark = Fresh.Text
    val accent = Fresh.Indigo
    val gold = Fresh.QuestGold
    val mint = Fresh.QuestMint
    val trackColor = Fresh.QuestTrack
    val rule = Fresh.PaperRule
    val margin = Fresh.PaperMargin
    val paperInk = Fresh.PaperInk
    val slots = TimeOfDay.entries.map { timeColor(it) }
    val lines = listOf(Fresh.Indigo, Fresh.Coral, Fresh.Teal, Fresh.Amber)
    Canvas(Modifier.size(40.dp, 52.dp).clip(shape).background(ground).border(1.dp, Fresh.Rim, shape)) {
        val w = size.width
        val h = size.height
        val thin = 1.5.dp.toPx()
        when (layout) {
            AppLayout.CLASSIC -> repeat(3) { i ->
                drawRoundRect(card, Offset(w * 0.12f, h * (0.10f + i * 0.29f)), Size(w * 0.76f, h * 0.22f), CornerRadius(h * 0.06f))
                drawCircle(accent, w * 0.06f, Offset(w * 0.24f, h * (0.21f + i * 0.29f)))
            }
            AppLayout.LEDGER -> {
                drawRect(accent, Offset(0f, 0f), Size(w, h * 0.18f))
                repeat(5) { i ->
                    val y = h * (0.32f + i * 0.14f)
                    drawLine(dark, Offset(w * 0.12f, y), Offset(w * 0.55f, y), thin)
                    drawLine(ink, Offset(w * 0.68f, y), Offset(w * 0.88f, y), thin)
                }
            }
            AppLayout.QUEST -> {
                val pts = listOf(Offset(w * 0.28f, h * 0.14f), Offset(w * 0.70f, h * 0.38f), Offset(w * 0.30f, h * 0.62f), Offset(w * 0.68f, h * 0.86f))
                for (i in 0 until pts.size - 1) drawLine(if (i == 0) mint else trackColor, pts[i], pts[i + 1], 3.dp.toPx(), StrokeCap.Round)
                pts.forEachIndexed { i, p ->
                    drawCircle(
                        when (i) {
                            0 -> mint
                            pts.lastIndex -> gold
                            else -> trackColor
                        },
                        w * 0.11f, p
                    )
                }
                drawCircle(gold, w * 0.11f, pts[1], style = Stroke(thin))
            }
            AppLayout.DIAL -> {
                val stroke = 4.dp.toPx()
                val d = w * 0.68f
                val corner = Offset((w - d) / 2, (h - d) / 2)
                slots.forEachIndexed { i, c ->
                    drawArc(if (i < 2) c else c.copy(alpha = 0.35f), -90f + i * 90f + 8f, 74f, false, corner, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            AppLayout.JOURNAL -> {
                repeat(5) { i ->
                    val y = h * (0.22f + i * 0.15f)
                    drawLine(rule, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                    drawLine(paperInk, Offset(w * 0.36f, y - h * 0.05f), Offset(w * (0.62f + (i % 2) * 0.2f), y - h * 0.05f), thin)
                }
                drawLine(margin, Offset(w * 0.26f, 0f), Offset(w * 0.26f, h), 1.dp.toPx())
            }
            AppLayout.METRO -> {
                val x = w * 0.28f
                repeat(4) { i ->
                    val y0 = h * (0.06f + i * 0.22f)
                    drawLine(lines[i], Offset(x, y0), Offset(x, y0 + h * 0.22f), 3.dp.toPx())
                }
                repeat(4) { i ->
                    val y = h * (0.16f + i * 0.22f)
                    drawCircle(card, w * 0.09f, Offset(x, y))
                    drawCircle(dark, w * 0.09f, Offset(x, y), style = Stroke(thin))
                    drawLine(ink, Offset(w * 0.48f, y), Offset(w * 0.86f, y), thin)
                }
            }
        }
    }
}
