package com.sonas.actiontracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.GOD_LAMP
import com.sonas.actiontracker.sync.GodTasks
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PILL_SHAPE = RoundedCornerShape(50)
private val CARD = RoundedCornerShape(18.dp)
private val CELL = RoundedCornerShape(10.dp)
internal val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d")
private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
private const val CHART_DAYS = 14

/** Day the action was ticked done. Items done before completion times were stored count on their due date. */
private fun Item.doneDay(): Long? = when {
    status != STATUS_DONE -> null
    completedAt != null -> Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    else -> dueDate
}

internal fun YearMonth.toIndex() = year * 12 + monthValue - 1
internal fun monthOf(index: Int): YearMonth = YearMonth.of(index / 12, index % 12 + 1)

/**
 * The Calendar or Stats page of the main pager. The month (switched from the page header) and the
 * picked day live in the main screen so both pages and the header share them.
 */
@Composable
fun InsightsPage(
    stats: Boolean,
    actions: List<Item>,
    month: YearMonth,
    selected: Long,
    onSelect: (Long) -> Unit,
    onToggle: (Item) -> Unit,
    onEdit: (Item) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            if (stats) StatsTab(actions, month) else CalendarTab(actions, month, selected, onSelect, onToggle, onEdit)
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------- Calendar

@Composable
private fun CalendarTab(
    actions: List<Item>,
    month: YearMonth,
    selected: Long,
    onSelect: (Long) -> Unit,
    onToggle: (Item) -> Unit,
    onEdit: (Item) -> Unit
) {
    val today = LocalDate.now().toEpochDay()
    val byDay = remember(actions) { actions.groupBy { it.dueDate } }
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sunday-first grid
    val days = month.lengthOfMonth()
    // Picking a day recomposes this tab; the month totals only change with the list, month or day.
    val inMonth = remember(actions, month) { actions.filter { YearMonth.from(LocalDate.ofEpochDay(it.dueDate)) == month } }
    val doneInMonth = remember(inMonth) { inMonth.count { it.status == STATUS_DONE } }
    val backlog = remember(actions, today) { actions.count { it.status == STATUS_OPEN && it.dueDate < today } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SummaryPill("$doneInMonth/${inMonth.size}", "done", Fresh.Green, Modifier.weight(1f))
        SummaryPill("${inMonth.size - doneInMonth}", "open", Fresh.Text, Modifier.weight(1f))
        SummaryPill("$backlog", "backlog", if (backlog > 0) Fresh.Coral else Fresh.Text, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().glass(CARD).padding(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Text(
                    it, style = MaterialTheme.typography.labelSmall, color = Fresh.TextDim,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        val rows = (lead + days + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).padding(2.dp)) {
                        if (dayNum in 1..days) {
                            val epoch = month.atDay(dayNum).toEpochDay()
                            val items = byDay[epoch].orEmpty()
                            DayCell(
                                dayNum,
                                done = items.count { it.status == STATUS_DONE },
                                total = items.size,
                                isToday = epoch == today,
                                isSelected = epoch == selected,
                                backlog = epoch < today && items.any { it.status == STATUS_OPEN },
                                onClick = { onSelect(epoch) }
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(Fresh.Green.copy(alpha = 0.35f)); Text(" done", style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim)
        Spacer(Modifier.width(14.dp))
        LegendDot(Fresh.Coral); Text(" backlog", style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim)
    }

    val dayItems = byDay[selected].orEmpty().sortedBy { it.status == STATUS_DONE }
    Text(
        LocalDate.ofEpochDay(selected).format(DAY_FORMAT).uppercase() +
            "   ${dayItems.count { it.status == STATUS_DONE }}/${dayItems.size}",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
        color = Fresh.Indigo,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
    )
    if (dayItems.isEmpty()) {
        Text(
            "Nothing due this day.",
            style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
    // One list for the day, open first and Morning → Night; each row shows its time-of-day icon.
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        dayItems.sortedBy { TimeOfDay.of(it.timeOfDay).ordinal }.sortedBy { it.status == STATUS_DONE }.forEach {
            DayRow(it, today, onToggle = { onToggle(it) }, onEdit = { onEdit(it) })
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    done: Int,
    total: Int,
    isToday: Boolean,
    isSelected: Boolean,
    backlog: Boolean,
    onClick: () -> Unit
) {
    val fill by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(500), label = "fill")
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.9f).clip(CELL)
            .background(if (isSelected) Fresh.Indigo.copy(alpha = 0.10f) else Color.Transparent)
            .then(
                when {
                    isSelected -> Modifier.border(1.5.dp, Fresh.Indigo, CELL)
                    isToday -> Modifier.border(1.dp, Fresh.Sky, CELL)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {
        if (fill > 0f) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fill)
                    .background(Fresh.Green.copy(alpha = 0.22f))
            )
        }
        Column(Modifier.fillMaxSize().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    backlog -> Fresh.Coral
                    isToday -> Fresh.Sky
                    else -> Fresh.Text
                }
            )
            Spacer(Modifier.weight(1f))
            if (total > 0) {
                Text(
                    "$done/$total",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                    color = if (done == total) Fresh.Green else Fresh.TextDim,
                    maxLines = 1
                )
            }
        }
        if (backlog) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp).clip(CircleShape).background(Fresh.Coral))
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

/** Compact one-line row for the selected day, time-of-day icon on the right: tap the circle to tick, tap the row to edit. */
@Composable
private fun DayRow(item: Item, today: Long, onToggle: () -> Unit, onEdit: () -> Unit) {
    val isDone = item.status == STATUS_DONE
    Row(
        Modifier.fillMaxWidth().glass(CARD).clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (isDone) Fresh.Green else Color.Transparent)
                .border(1.5.dp, if (isDone) Fresh.Green else Fresh.TextDim, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) Icon(Icons.Rounded.Check, "Mark open", tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (GodTasks.isGod(item)) "$GOD_LAMP ${item.title}" else item.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isDone) Fresh.TextDim else Fresh.Text,
            textDecoration = if (isDone) TextDecoration.LineThrough else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        val late = !isDone && item.dueDate < today
        Text(
            if (late) "${today - item.dueDate}d late" else item.person,
            style = MaterialTheme.typography.labelMedium,
            color = if (late) Fresh.Coral else Fresh.TextDim,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        TimeIcon(TimeOfDay.of(item.timeOfDay), 18.dp)
    }
}

@Composable
private fun SummaryPill(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.height(32.dp).glass(PILL_SHAPE).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Fresh.TextDim, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Stats

private class Stats(
    val monthDone: Int,
    val monthTotal: Int,
    val streak: Int,
    val thisWeek: Int,
    val lastWeek: Int,
    val backlog: Int,
    val aging: List<Int>,
    /** This month's (done, total) per time of day, Morning first. */
    val byTime: List<Triple<TimeOfDay, Int, Int>>,
    /** Completions per day for the last [CHART_DAYS] days, oldest first. */
    val daily: List<Pair<Long, Int>>
)

private fun stats(actions: List<Item>, month: YearMonth): Stats {
    val today = LocalDate.now().toEpochDay()
    val perDay = actions.mapNotNull { it.doneDay() }.groupingBy { it }.eachCount()
    val inMonth = actions.filter { YearMonth.from(LocalDate.ofEpochDay(it.dueDate)) == month }
    // A streak survives until the end of today, so start from yesterday if nothing is done yet today.
    var day = if ((perDay[today] ?: 0) > 0) today else today - 1
    var streak = 0
    while ((perDay[day] ?: 0) > 0) { streak++; day-- }
    val overdue = actions.filter { it.status == STATUS_OPEN && it.dueDate < today }.map { today - it.dueDate }
    return Stats(
        monthDone = inMonth.count { it.status == STATUS_DONE },
        monthTotal = inMonth.size,
        streak = streak,
        thisWeek = (0..6).sumOf { perDay[today - it] ?: 0 },
        lastWeek = (7..13).sumOf { perDay[today - it] ?: 0 },
        backlog = overdue.size,
        aging = listOf(overdue.count { it <= 3 }, overdue.count { it in 4..7 }, overdue.count { it >= 8 }),
        byTime = TimeOfDay.entries.map { t ->
            val slot = inMonth.filter { TimeOfDay.of(it.timeOfDay) == t }
            Triple(t, slot.count { it.status == STATUS_DONE }, slot.size)
        },
        daily = (CHART_DAYS - 1 downTo 0).map { (today - it) to (perDay[today - it] ?: 0) }
    )
}

private fun cheer(s: Stats): String {
    val rate = if (s.monthTotal == 0) 0 else s.monthDone * 100 / s.monthTotal
    return when {
        s.monthTotal == 0 -> "Add a few actions and your progress will show up here."
        s.streak >= 3 -> "${s.streak} days in a row. Keep the chain going!"
        rate >= 80 -> "Brilliant month so far. You're on top of things."
        s.thisWeek > s.lastWeek && s.lastWeek > 0 -> "You're ahead of last week. Nice momentum."
        s.backlog == 0 -> "No backlog. Everything is on track."
        s.backlog <= 3 -> "Just ${s.backlog} in the backlog. Clear one today."
        else -> "One small win today gets things moving again."
    }
}

@Composable
private fun StatsTab(actions: List<Item>, month: YearMonth) {
    val s = remember(actions, month) { stats(actions, month) }
    val rate = if (s.monthTotal == 0) 0f else s.monthDone.toFloat() / s.monthTotal

    Row(Modifier.fillMaxWidth().glass(CARD).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(rate, Modifier.size(84.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("${s.monthDone} of ${s.monthTotal} done", style = MaterialTheme.typography.titleMedium)
            Text(
                "in ${month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())}",
                style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim
            )
            Spacer(Modifier.height(6.dp))
            Text(cheer(s), style = MaterialTheme.typography.bodyMedium, color = Fresh.Indigo)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SummaryPill("${s.streak}d", "streak", Fresh.Amber, Modifier.weight(1f))
        SummaryPill("${s.thisWeek}", "this wk", Fresh.Green, Modifier.weight(1f))
        SummaryPill("${s.backlog}", "backlog", if (s.backlog > 0) Fresh.Coral else Fresh.Text, Modifier.weight(1f))
    }

    SectionLabel("Completed, last $CHART_DAYS days")
    DailyBars(s.daily)
    if (s.lastWeek > 0 || s.thisWeek > 0) {
        val diff = s.thisWeek - s.lastWeek
        Text(
            when {
                diff > 0 -> "$diff more than the week before."
                diff < 0 -> "${-diff} fewer than the week before."
                else -> "Same pace as the week before."
            },
            style = MaterialTheme.typography.bodyMedium, color = Fresh.TextDim,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )
    }

    SectionLabel("By time of day, this month")
    TimeBreakdown(s.byTime)

    SectionLabel("Backlog by age")
    BacklogAging(s.aging)
}

/** One line per time of day: icon, label, done/total and a thin bar of the done share. */
@Composable
private fun TimeBreakdown(rows: List<Triple<TimeOfDay, Int, Int>>) {
    Column(
        Modifier.fillMaxWidth().glass(CARD).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { (time, done, total) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { TimeIcon(time, 18.dp) }
                Spacer(Modifier.width(8.dp))
                Text(time.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(76.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(PILL_SHAPE).background(Fresh.Rim)) {
                    if (total > 0 && done > 0) {
                        Box(Modifier.fillMaxWidth(done.toFloat() / total).fillMaxHeight().clip(PILL_SHAPE).background(timeColor(time)))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text("$done/$total", style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim, modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
        color = Fresh.Indigo,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun ProgressRing(fraction: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(fraction, tween(800), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Fresh.Rim, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Fresh.Teal, Fresh.Green, Fresh.Teal)),
                -90f, 360f * shown, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text("${(shown * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DailyBars(daily: List<Pair<Long, Int>>) {
    val max = (daily.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val today = LocalDate.now().toEpochDay()
    Column(Modifier.fillMaxWidth().glass(CARD).padding(horizontal = 10.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom) {
            daily.forEach { (day, n) ->
                val h by animateFloatAsState(n.toFloat() / max, tween(600), label = "bar")
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (n > 0) {
                        Text("$n", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = Fresh.TextDim)
                    }
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(h.coerceAtLeast(0.02f) * 0.82f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (day == today) Fresh.Indigo else Fresh.Teal.copy(alpha = 0.75f))
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            daily.forEach { (day, _) ->
                Text(
                    LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                    color = if (day == today) Fresh.Indigo else Fresh.TextDim,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BacklogAging(aging: List<Int>) {
    val labels = listOf("1–3 days", "4–7 days", "8+ days")
    val colors = listOf(Fresh.Amber, Fresh.Coral, Color(0xFFB4232A))
    val total = aging.sum()
    Column(Modifier.fillMaxWidth().glass(CARD).padding(14.dp)) {
        if (total == 0) {
            Text("Nothing overdue. Great job staying on track!", style = MaterialTheme.typography.bodyMedium, color = Fresh.Green)
            return@Column
        }
        Row(Modifier.fillMaxWidth().height(12.dp).clip(PILL_SHAPE).background(Fresh.Rim)) {
            aging.forEachIndexed { i, n ->
                if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(colors[i]))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            aging.forEachIndexed { i, n ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(colors[i])
                    Text(" $n", style = MaterialTheme.typography.titleSmall, color = Fresh.Text)
                    Text("  ${labels[i]}", style = MaterialTheme.typography.labelMedium, color = Fresh.TextDim, maxLines = 1)
                }
            }
        }
    }
}
