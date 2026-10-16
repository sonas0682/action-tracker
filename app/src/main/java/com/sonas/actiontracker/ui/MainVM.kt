package com.sonas.actiontracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.sync.SmartTitles
import com.sonas.actiontracker.sync.SyncEngine
import com.sonas.actiontracker.widget.DueWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainVM(app: Application) : AndroidViewModel(app) {
    private val dao = Db.get(app).dao()

    val suggested = dao.suggested().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val actions = dao.actions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var syncing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** Confirms a suggestion: it becomes a regular open action. */
    fun confirm(item: Item) = write { dao.update(item.copy(status = STATUS_OPEN)) }

    /** Dismisses a suggestion. The source stays in `processed`, so it never comes back. */
    fun dismiss(item: Item) = viewModelScope.launch { dao.delete(item) }

    fun save(item: Item) = write {
        if (item.id == 0L) dao.insert(item) else dao.update(item)
    }

    fun toggleDone(item: Item) = write {
        dao.update(item.copy(status = if (item.status == STATUS_DONE) STATUS_OPEN else STATUS_DONE))
    }

    fun delete(item: Item) = write { dao.delete(item) }

    /** Runs a database change, then redraws the home-screen widget so it never shows stale actions. */
    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        block()
        runCatching { DueWidget.refresh(getApplication()) }
    }

    /** Runs the on-device Gemini Nano title pass over new Gmail suggestions. Needs the app in the foreground. */
    fun smartTitles() = viewModelScope.launch { SmartTitles.run(getApplication()) }

    fun sync(token: String) {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            message = try {
                val n = SyncEngine.run(getApplication(), token)
                if (n > 0) "$n new suggestion(s) to review" else "Nothing new"
            } catch (e: Exception) {
                "Sync failed: ${e.message}"
            }
            syncing = false
            SmartTitles.run(getApplication())
        }
    }
}
