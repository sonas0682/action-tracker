package com.sonas.actiontracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.R
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: counts for today (including overdue) and tomorrow, today's open
 * actions with a tick box each, and a short Gemini Nano summary of today at the bottom.
 * The refresh button and tick boxes go through [WidgetRefreshActivity] so Nano can run
 * in the foreground; tapping anything else opens the app.
 */
class DueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = Db.get(context).dao()
        val today = LocalDate.now().toEpochDay()
        val initial = dao.openDueBy(today + 1)
        val initialSummary = WidgetSummary.cached(context)
        provideContent {
            // Room's Flow and the summary cache keep the widget live while its session runs.
            val items by dao.openDueByFlow(today + 1).collectAsState(initial)
            val summary by WidgetSummary.flow(context).collectAsState(initialSummary)
            Content(items, summary, today)
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

private val DoneIdKey = ActionParameters.Key<Long>(WidgetRefreshActivity.EXTRA_DONE_ID)

/** The app's light "Fresh" palette (ui/Theme.kt), fixed so the widget matches the app. */
private object W {
    val Card = ColorProvider(Color(0xFFF6F7FC))
    val Tile = Color(0xFFFFFFFF)
    val Indigo = ColorProvider(Color(0xFF5B5FEF))
    val Teal = ColorProvider(Color(0xFF12B5A6))
    val Coral = ColorProvider(Color(0xFFE5484D))
    val Text = ColorProvider(Color(0xFF1B1D33))
    val TextDim = ColorProvider(Color(0xFF6A6E8C))
    val SummaryBg = Color(0xFFE1E2FF)
}

private val DayLabel = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
private fun Content(items: List<Item>, cached: WidgetSummary.Cached?, epochDay: Long) {
    val open = actionStartActivity<MainActivity>()
    val today = items.filter { it.dueDate <= epochDay }
    val tomorrow = items.count { it.dueDate == epochDay + 1 }
    val overdue = today.count { it.dueDate < epochDay }
    val fresh = cached?.takeIf { it.fingerprint == WidgetSummary.fingerprint(today, epochDay) }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(22.dp)
            .background(W.Card)
            .padding(12.dp)
    ) {
        // Header: date on the left, refresh on the right.
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                LocalDate.ofEpochDay(epochDay).format(DayLabel),
                modifier = GlanceModifier.defaultWeight().clickable(open),
                style = TextStyle(color = W.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(W.Indigo),
                modifier = GlanceModifier.size(28.dp).padding(2.dp)
                    .clickable(actionStartActivity<WidgetRefreshActivity>())
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        // Stats: today (with overdue) and tomorrow.
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            StatTile("Today", today.size, if (overdue > 0) "$overdue overdue" else null, W.Indigo, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            StatTile("Tomorrow", tomorrow, null, W.Teal, GlanceModifier.defaultWeight())
        }
        if (today.isNotEmpty()) {
            LazyColumn(modifier = GlanceModifier.defaultWeight().padding(top = 4.dp)) {
                items(today, itemId = { it.id }) { item -> ItemRow(item, epochDay) }
            }
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        // Summary: Gemini Nano when it matches today's list, rule-based otherwise.
        Column(
            modifier = GlanceModifier.fillMaxWidth().cornerRadius(14.dp).background(W.SummaryBg)
                .padding(horizontal = 10.dp, vertical = 8.dp).clickable(open)
        ) {
            Text(
                if (fresh != null) "✦ Today, by Gemini Nano" else "✦ Today",
                style = TextStyle(color = W.Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                fresh?.text ?: WidgetSummary.fallback(today, tomorrow, epochDay),
                maxLines = 3,
                style = TextStyle(color = W.Text, fontSize = 12.sp)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, count: Int, sub: String?, accent: ColorProvider, modifier: GlanceModifier) {
    Column(
        modifier = modifier.cornerRadius(14.dp).background(W.Tile)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(label.uppercase(), style = TextStyle(color = W.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$count", style = TextStyle(color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold))
            if (sub != null) {
                Spacer(GlanceModifier.width(6.dp))
                Text(sub, maxLines = 1, style = TextStyle(color = W.Coral, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, epochDay: Long) {
    val overdue = item.dueDate < epochDay
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(
            checked = false,
            onCheckedChange = actionStartActivity<WidgetRefreshActivity>(actionParametersOf(DoneIdKey to item.id)),
            colors = CheckboxDefaults.colors(checkedColor = W.Indigo, uncheckedColor = W.TextDim)
        )
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(item.title, maxLines = 1, style = TextStyle(color = W.Text, fontSize = 13.sp))
            val meta = listOfNotNull(
                if (overdue) "Overdue" else null,
                item.person.takeIf { it != "Me" }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta, maxLines = 1,
                    style = TextStyle(color = if (overdue) W.Coral else W.TextDim, fontSize = 11.sp)
                )
            }
        }
    }
}
