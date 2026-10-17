package com.sonas.actiontracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.sync.Backup
import com.sonas.actiontracker.sync.SmartTitles
import com.sonas.actiontracker.sync.SyncEngine
import com.sonas.actiontracker.widget.DueWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainVM(app: Application) : AndroidViewModel(app) {
    private val dao = Db.get(app).dao()

    val suggested = dao.suggested().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val actions = dao.actions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var syncing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** Confirms a suggestion: it becomes a regular open action. */
    fun confirm(item: Item) = write { dao.update(item.copy(status = STATUS_OPEN)) }

    /** Confirms every suggestion currently on screen in one go. */
    fun confirmAll(items: List<Item>) = write { items.forEach { dao.update(it.copy(status = STATUS_OPEN)) } }

    /** Dismisses a suggestion. The source stays in `processed`, so it never comes back. */
    fun dismiss(item: Item) = write { dao.delete(item) }

    fun save(item: Item) = write {
        if (item.id == 0L) dao.insert(item) else dao.update(item)
    }

    fun toggleDone(item: Item) = write {
        val done = item.status != STATUS_DONE
        dao.update(
            item.copy(
                status = if (done) STATUS_DONE else STATUS_OPEN,
                completedAt = if (done) System.currentTimeMillis() else null
            )
        )
    }

    fun delete(item: Item) = write { dao.delete(item) }

    /** Undo for a swipe-delete: puts the row back with its original id. */
    fun restore(item: Item) = write { dao.insert(item) }

    /**
     * Runs a database change, then redraws the home-screen widget so it never shows
     * stale actions, and queues the automatic Drive backup.
     */
    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        block()
        runCatching { DueWidget.refresh(getApplication()) }
        runCatching { Backup.backUpSoon(getApplication()) }
    }

    /** What we know about the backup already in Google Drive. */
    sealed interface RemoteBackup {
        data object Unknown : RemoteBackup
        data object None : RemoteBackup
        data class Found(val modifiedAt: Long?) : RemoteBackup
    }

    var backupStatus by mutableStateOf(Backup.status(app))
        private set
    var backupBusy by mutableStateOf(false)
        private set
    /** Bumped after every successful backup or restore, so the dialog can move on. */
    var backupSuccesses by mutableIntStateOf(0)
        private set
    var remoteBackup by mutableStateOf<RemoteBackup>(RemoteBackup.Unknown)
        private set

    fun refreshBackupStatus() {
        backupStatus = Backup.status(getApplication())
    }

    /** Checks whether this Google account already has a backup (fresh install, new phone). */
    fun findBackup(token: String) = backupJob(null, "Couldn't check Google Drive") {
        remoteBackup = Backup.findRemote(token)?.let { RemoteBackup.Found(it.modifiedAt) } ?: RemoteBackup.None
        false
    }

    /**
     * Turns on Drive backup with [passphrase] and makes the first backup. If Drive
     * already holds a backup (say from an old phone) and [replace] is false, stops
     * and reports it instead, so it is never overwritten by accident.
     */
    fun enableBackup(passphrase: String, token: String, replace: Boolean) = backupJob("Backed up to Google Drive") {
        if (!replace) {
            val remote = Backup.findRemote(token)
            if (remote != null) {
                remoteBackup = RemoteBackup.Found(remote.modifiedAt)
                return@backupJob false
            }
        }
        withContext(Dispatchers.Default) { Backup.enable(getApplication(), passphrase.toCharArray()) }
        Backup.backUp(getApplication(), token)
        true
    }

    fun backUpNow(token: String) = backupJob("Backed up to Google Drive") { Backup.backUp(getApplication(), token); true }

    /** Replaces everything on this phone with the Drive backup. */
    fun restoreBackup(passphrase: String, token: String) = backupJob(null, "Restore failed") {
        val n = Backup.restore(getApplication(), token, passphrase.toCharArray())
        runCatching { DueWidget.refresh(getApplication()) }
        message = "Restored $n action(s) from Google Drive"
        true
    }

    fun disableBackup() {
        Backup.disable(getApplication())
        backupStatus = Backup.status(getApplication())
    }

    fun lastBackupLabel(): String = backupStatus.lastBackupAt
        ?.let { "Last backup: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
        ?: "No backup yet"

    /** Runs a Drive job; [block] returns true when it finished the step (the dialog then moves on). */
    private fun backupJob(done: String?, failed: String = "Backup failed", block: suspend () -> Boolean) {
        if (backupBusy) return
        viewModelScope.launch {
            backupBusy = true
            try {
                if (block()) {
                    done?.let { message = it }
                    backupSuccesses++
                }
            } catch (e: Exception) {
                message = "$failed: ${e.message}"
            }
            backupStatus = Backup.status(getApplication())
            backupBusy = false
        }
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
            val backfilled = SmartTitles.backfill(getApplication(), token)
            if (backfilled > 0) message = "Added email summaries to $backfilled earlier item(s)"
        }
    }
}
