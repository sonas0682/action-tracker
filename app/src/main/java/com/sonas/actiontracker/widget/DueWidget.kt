package com.sonas.actiontracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.R
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: Today (overdue included) and Tomorrow tabs with their counts,
 * and the selected day's open actions as cards. Tap a tab to switch days, tap the
 * circle to mark an action done, tap anything else to open the app.
 */
class DueWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = Db.get(context).dao()
        val today = LocalDate.now().toEpochDay()
        val initial = dao.openDueBy(today + 1)
        provideContent {
            // Room's Flow keeps the widget live while its session is running.
            val items by dao.openDueByFlow(today + 1).collectAsState(initial)
            val showTomorrow = currentState<Preferences>()[ShowTomorrowKey] ?: false
            Content(items, today, showTomorrow)
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

private val ShowTomorrowKey = booleanPreferencesKey("show_tomorrow")
private val ItemIdKey = ActionParameters.Key<Long>("item_id")
private val TomorrowParam = ActionParameters.Key<Boolean>("tomorrow")

class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemIdKey] ?: return
        Db.get(context).dao().markDone(id)
        DueWidget.refresh(context)
    }
}

/** Switches this widget between the Today and Tomorrow lists. */
class SelectDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tomorrow = parameters[TomorrowParam] ?: false
        updateAppWidgetState(context, glanceId) { it[ShowTomorrowKey] = tomorrow }
        DueWidget().update(context, glanceId)
    }
}

/** The app's light "Fresh" palette (ui/Theme.kt), fixed so the widget matches the app. */
private object W {
    val Card = Color(0xFFF6F7FC)
    val Tile = Color(0xFFFFFFFF)
    val Indigo = Color(0xFF5B5FEF)
    val Teal = Color(0xFF12B5A6)
    val Coral = Color(0xFFE5484D)
    val IndigoSoft = Color(0xFFE1E2FF)
    val TealSoft = Color(0xFFD3F5F0)
    val CoralSoft = Color(0xFFFFE3E1)
    val Grey = Color(0xFFECEDF5)
    val Text = Color(0xFF1B1D33)
    val TextDim = Color(0xFF6A6E8C)
    val White = Color(0xFFFFFFFF)
    val WhiteDim = Color(0xD9FFFFFF)
}

private fun c(color: Color) = ColorProvider(color)

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
            .background(W.Card)
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
            DayTab("Today", today.size, if (overdue > 0) "$overdue overdue" else null, W.Indigo, !showTomorrow, false, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            DayTab("Tomorrow", tomorrow.size, null, W.Teal, showTomorrow, true, GlanceModifier.defaultWeight())
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
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(shown, itemId = { it.id }) { item -> ActionCard(item, epochDay, accent) }
            }
        }
    }
}

@Composable
private fun DayTab(
    label: String, count: Int, sub: String?, accent: Color,
    selected: Boolean, tomorrow: Boolean, modifier: GlanceModifier
) {
    val fg = if (selected) W.White else accent
    Column(
        modifier = modifier.cornerRadius(16.dp)
            .background(if (selected) accent else W.Tile)
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
private fun ActionCard(item: Item, epochDay: Long, accent: Color) {
    val overdueDays = epochDay - item.dueDate
    val tint = if (overdueDays > 0) W.Coral else accent
    // One line per action: tick circle, title (gives way first), then late / person / context tags.
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(W.Tile)
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
                    item.title, maxLines = 1,
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
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text, maxLines = 1,
        modifier = GlanceModifier.cornerRadius(8.dp).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
        style = TextStyle(color = c(fg), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    )
}
