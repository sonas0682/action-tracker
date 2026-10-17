package com.sonas.actiontracker.widget

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sonas.actiontracker.data.Db
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Invisible screen behind the widget's refresh button and checkboxes. It marks the
 * ticked action done, redraws the widget, then uses its moment in the foreground to
 * let Gemini Nano re-summarize today's list (Nano refuses background callers), and
 * closes itself. It is a 1-pixel window that lets touches through, so the home
 * screen stays usable while it runs.
 */
class WidgetRefreshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(1, 1)
        window.setGravity(Gravity.TOP or Gravity.START)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        val doneId = intent.getLongExtra(EXTRA_DONE_ID, 0L)
        lifecycleScope.launch {
            if (doneId > 0) Db.get(this@WidgetRefreshActivity).dao().markDone(doneId)
            runCatching { DueWidget.refresh(this@WidgetRefreshActivity) }
            val updated = withTimeoutOrNull(NANO_TIMEOUT_MS) { WidgetSummary.summarize(this@WidgetRefreshActivity) }
            if (updated == true) runCatching { DueWidget.refresh(this@WidgetRefreshActivity) }
            finish()
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        /** Intent extra (and widget action parameter) holding the id of the action to mark done. */
        const val EXTRA_DONE_ID = "done_id"
        private const val NANO_TIMEOUT_MS = 8_000L
    }
}
