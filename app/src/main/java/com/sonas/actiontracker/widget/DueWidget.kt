package com.sonas.actiontracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sonas.actiontracker.MainActivity
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import java.time.LocalDate

/**
 * Home-screen widget: open actions due today or overdue. Tick the box to mark one
 * done; tap anywhere else to open the (fingerprint-locked) app.
 */
class DueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = Db.get(context).dao()
        val today = LocalDate.now().toEpochDay()
        val initial = dao.openDueBy(today)
        provideContent {
            // Room's Flow keeps the widget live while its session is running.
            val items by dao.openDueByFlow(today).collectAsState(initial)
            GlanceTheme { Content(items, today) }
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

private val ItemIdKey = ActionParameters.Key<Long>("item_id")

class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemIdKey] ?: return
        Db.get(context).dao().markDone(id)
        DueWidget.refresh(context)
    }
}

@Composable
private fun Content(items: List<Item>, today: Long) {
    val colors = GlanceTheme.colors
    val open = actionStartActivity<MainActivity>()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(colors.widgetBackground)
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(open),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Due today",
                style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                if (items.isEmpty()) "" else "${items.size}",
                style = TextStyle(color = colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
        if (items.isEmpty()) {
            Text(
                "All clear. Nothing due today.",
                modifier = GlanceModifier.padding(top = 8.dp).clickable(open),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.padding(top = 4.dp)) {
                items(items, itemId = { it.id }) { item -> ItemRow(item, today) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, today: Long) {
    val colors = GlanceTheme.colors
    val overdue = item.dueDate < today
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<MarkDoneAction>(actionParametersOf(ItemIdKey to item.id))
        )
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(item.title, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 14.sp))
            val meta = listOfNotNull(
                if (overdue) "Overdue" else null,
                item.person.takeIf { it != "Me" }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta, maxLines = 1,
                    style = TextStyle(color = if (overdue) colors.error else colors.onSurfaceVariant, fontSize = 12.sp)
                )
            }
        }
    }
}
