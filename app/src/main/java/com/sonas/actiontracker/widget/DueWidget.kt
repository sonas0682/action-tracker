package com.sonas.actiontracker.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.R
import com.sonas.actiontracker.data.Actions
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.Backup
import com.sonas.actiontracker.sync.GOD_LAMP
import com.sonas.actiontracker.sync.GodTasks
import com.sonas.actiontracker.ui.ThemeMode
import com.sonas.actiontracker.ui.ThemePref
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: Now (today, overdue included) and Near (tomorrow) tabs with their counts,
 * and the selected day's open actions as cards under Morning / Afternoon / Evening /
 * Night headers. Tap a tab to switch days, tap the
 * circle to mark an action done, tap anything else to open the app.
 */
class DueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = Db.get(context).dao()
        val today = LocalDate.now().toEpochDay()
        val initial = dao.openDueBy(today + 1)
        provideContent {
            // Room's Flow keeps the widget live while its session is running.
            val itemsFlow = remember { dao.openDueByFlow(today + 1) }
            val dayFlow = remember { DayChoice.flow(context) }
            val items by itemsFlow.collectAsState(initial)
            val showTomorrow by dayFlow.collectAsState(DayChoice.showTomorrow(context))
            val themeFlow = remember { ThemeChoice.flow(context) }
            val theme by themeFlow.collectAsState(ThemePref.read(context))
            CompositionLocalProvider(LocalThemeMode provides theme) {
                Content(items, today, showTomorrow)
            }
        }
    }

    companion object {
        /** Redraws every placed widget, e.g. after an action is added, edited or completed in the app. */
        suspend fun refresh(context: Context) = DueWidget().updateAll(context)
    }
}

class DueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DueWidget()
}

/** Which day the widget lists, kept in plain SharedPreferences so the widget needs no DataStore. */
private object DayChoice {
    private const val PREFS = "widget"
    private const val KEY = "show_tomorrow"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun showTomorrow(context: Context) = prefs(context).getBoolean(KEY, false)

    fun set(context: Context, tomorrow: Boolean) = prefs(context).edit().putBoolean(KEY, tomorrow).apply()

    fun flow(context: Context): Flow<Boolean> = callbackFlow {
        val p = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY) trySend(sp.getBoolean(KEY, false))
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
/** The app's Theme setting ("ui" prefs, see [ThemePref]), watched so the widget recolours with the app. */
private object ThemeChoice {
    private const val KEY = "theme"

    fun flow(context: Context): Flow<ThemeMode> = callbackFlow {
        val p = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(ThemePref.read(context))
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

private val ItemIdKey = ActionParameters.Key<Long>("item_id")
private val TomorrowParam = ActionParameters.Key<Boolean>("tomorrow")

class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemIdKey] ?: return
        Actions.complete(Db.get(context), id)
        DueWidget.refresh(context)
        // Same follow-up as a tick in the app or on a reminder: queue the Drive backup.
        runCatching { Backup.backUpSoon(context) }
    }
}

/** Switches this widget between the Today and Tomorrow lists. */
class SelectDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tomorrow = parameters[TomorrowParam] ?: false
        DayChoice.set(context, tomorrow)
        DueWidget.refresh(context)
    }
}

/** One widget colour: its light and dark "Fresh" shade (ui/Theme.kt). */
private class Shade(val day: Color, val night: Color)

/** The app's "Fresh" palette in both shades, so the widget matches the app's Theme setting. */
private object W {
    val Card = Shade(Color(0xFFF6F7FC), Color(0xFF0F1120))
    val Tile = Shade(Color(0xFFFFFFFF), Color(0xFF1D2035))
    val Indigo = Shade(Color(0xFF5B5FEF), Color(0xFF7C80FF))
    val Teal = Shade(Color(0xFF12B5A6), Color(0xFF2CC7B8))
    val Coral = Shade(Color(0xFFE5484D), Color(0xFFFF6B6E))
    val IndigoSoft = Shade(Color(0xFFE1E2FF), Color(0xFF2E3170))
    val TealSoft = Shade(Color(0xFFD3F5F0), Color(0xFF0F4A44))
    val CoralSoft = Shade(Color(0xFFFFE3E1), Color(0xFF5A1B1E))
    val Grey = Shade(Color(0xFFECEDF5), Color(0xFF2A2D44))
    /** God task tile: the app's saffron wash. */
    val GoldWash = Shade(Color(0xFFFFF4DB), Color(0xFF33280F))
    val Text = Shade(Color(0xFF1B1D33), Color(0xFFE9EAF6))
    val TextDim = Shade(Color(0xFF6A6E8C), Color(0xFF9EA2C2))
    val White = Shade(Color(0xFFFFFFFF), Color(0xFFFFFFFF))
    val WhiteDim = Shade(Color(0xD9FFFFFF), Color(0xD9FFFFFF))
}

/** The app's Light / Dark / System choice; System lets the launcher pick by Android's dark mode. */
private val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

@Composable
private fun c(shade: Shade): ColorProvider = when (LocalThemeMode.current) {
    ThemeMode.LIGHT -> ColorProvider(shade.day)
    ThemeMode.DARK -> ColorProvider(shade.night)
    ThemeMode.SYSTEM -> DayNightColor(day = shade.day, night = shade.night)
}

private val DayLabel = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
private fun Content(items: List<Item>, epochDay: Long, showTomorrow: Boolean) {
    val open = actionStartActivity<MainActivity>()
    val today = items.filter { it.dueDate <= epochDay }
    val tomorrow = items.filter { it.dueDate == epochDay + 1 }
    val overdue = today.count { it.dueDate < epochDay }
    val shown = if (showTomorrow) tomorrow else today
    val accent = if (showTomorrow) W.Teal else W.Indigo
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(c(W.Card))
            .padding(12.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Actions",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = c(W.Text), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                LocalDate.ofEpochDay(if (showTomorrow) epochDay + 1 else epochDay).format(DayLabel),
                style = TextStyle(color = c(W.TextDim), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            DayTab("Now", today.size, if (overdue > 0) "$overdue overdue" else null, W.Indigo, !showTomorrow, false, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            DayTab("Near", tomorrow.size, null, W.Teal, showTomorrow, true, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(8.dp))
        if (shown.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(open),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (showTomorrow) "Nothing lined up for tomorrow" else "All clear for today ✨",
                    style = TextStyle(color = c(W.TextDim), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
            }
        } else {
            val rows = shown.groupBy { TimeOfDay.of(it.timeOfDay) }.toSortedMap().flatMap { (time, list) ->
                listOf<Any>(time) + list
            }
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                // Headers get negative ids so they never collide with item ids.
                items(rows, itemId = { if (it is Item) it.id else -1L - (it as TimeOfDay).ordinal }) { row ->
                    if (row is Item) ActionCard(row, epochDay, accent) else TimeHeader(row as TimeOfDay, accent)
                }
            }
        }
    }
}

@Composable
private fun DayTab(
    label: String, count: Int, sub: String?, accent: Shade,
    selected: Boolean, tomorrow: Boolean, modifier: GlanceModifier
) {
    val fg = if (selected) W.White else accent
    Column(
        modifier = modifier.cornerRadius(16.dp)
            .background(c(if (selected) accent else W.Tile))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionRunCallback<SelectDayAction>(actionParametersOf(TomorrowParam to tomorrow)))
    ) {
        Text(
            label.uppercase(),
            style = TextStyle(color = c(if (selected) W.WhiteDim else W.TextDim), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$count", style = TextStyle(color = c(fg), fontSize = 26.sp, fontWeight = FontWeight.Bold))
            if (sub != null) {
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    sub, maxLines = 1,
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                    style = TextStyle(color = c(if (selected) W.White else W.Coral), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
private fun TimeHeader(time: TimeOfDay, accent: Shade) {
    val icon = when (time) {
        TimeOfDay.MORNING -> R.drawable.ic_time_morning
        TimeOfDay.EVENING -> R.drawable.ic_time_evening
        else -> null
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sunrise / sunset as tinted icons; Afternoon and Night keep their sun and moon emoji.
        if (icon != null) {
            Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(c(accent)), modifier = GlanceModifier.size(12.dp))
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(
            if (icon != null) time.label.uppercase() else "${time.emoji}  ${time.label.uppercase()}",
            style = TextStyle(color = c(accent), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun ActionCard(item: Item, epochDay: Long, accent: Shade) {
    val overdueDays = epochDay - item.dueDate
    val tint = if (overdueDays > 0) W.Coral else accent
    val god = GodTasks.isGod(item)
    // One line per action: tick circle, title (gives way first), then late / person / context tags.
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(c(if (god) W.GoldWash else W.Tile))
                .padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_circle),
                contentDescription = "Mark done",
                colorFilter = ColorFilter.tint(c(tint)),
                modifier = GlanceModifier.size(30.dp).padding(6.dp)
                    .clickable(actionRunCallback<MarkDoneAction>(actionParametersOf(ItemIdKey to item.id)))
            )
            Row(
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (god) "$GOD_LAMP ${item.title}" else item.title, maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = c(W.Text), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
                if (overdueDays > 0) {
                    Spacer(GlanceModifier.width(4.dp))
                    Chip("${overdueDays}d late", W.CoralSoft, W.Coral)
                }
                // Every action shows its person, "Me" included, so the rows line up.
                Spacer(GlanceModifier.width(4.dp))
                Chip(item.person, if (accent == W.Teal) W.TealSoft else W.IndigoSoft, accent)
                Spacer(GlanceModifier.width(4.dp))
                Chip(item.context, W.Grey, W.TextDim)
            }
        }
    }
}

@Composable
private fun Chip(text: String, bg: Shade, fg: Shade) {
    Text(
        text, maxLines = 1,
        modifier = GlanceModifier.cornerRadius(8.dp).background(c(bg)).padding(horizontal = 6.dp, vertical = 2.dp),
        style = TextStyle(color = c(fg), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    )
}
