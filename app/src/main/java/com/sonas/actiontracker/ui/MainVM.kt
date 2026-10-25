package com.sonas.actiontracker.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonas.actiontracker.data.Actions
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Series
import com.sonas.actiontracker.data.SeriesScope
import com.sonas.actiontracker.data.Snapshot
import com.sonas.actiontracker.data.Thought
import com.sonas.actiontracker.data.Thoughts
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.sync.Backup
import com.sonas.actiontracker.sync.GodTasks
import com.sonas.actiontracker.sync.GoogleAuth
import com.sonas.actiontracker.sync.SmartTitles
import com.sonas.actiontracker.sync.SyncEngine
import com.sonas.actiontracker.widget.DueWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainVM(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val dao = db.dao()

    val suggested = dao.suggested().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val actions = dao.actions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Thought of the day at the bottom of Now: a different one on every launch. */
    val thought: Thought = app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).let { prefs ->
        val i = Thoughts.next(prefs.getInt("last_thought", -1))
        prefs.edit().putInt("last_thought", i).apply()
        Thoughts.all[i]
    }

    var syncing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    /** A photo shared into the app, waiting to be read into a draft action. */
    var sharedPhoto by mutableStateOf<Uri?>(null)
    /** Text shared into the app (subject first), waiting to be drafted into an action. */
    var sharedText by mutableStateOf<String?>(null)

    // Repeating actions are kept three months ahead; this adds whatever came into range since the last run.
    init {
        viewModelScope.launch {
            // A failure here only delays the fill until the daily SeriesWorker; it must not crash the start.
            runCatching { Series.topUpAll(db) }
            runCatching { DueWidget.refresh(getApplication()) }
        }
    }

    /** Adds this morning's God tasks on app open if the 15-minute reminder check hasn't yet. */
    fun godTasks() = viewModelScope.launch { addGodTasks() }

    /** Saves the ⋮ → God task theme; turning it on adds today's right away (after 6 AM). */
    /** Saves the Light / Dark / System choice; the app recolours at once, the widget on its redraw. */
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        ThemePref.set(getApplication(), mode)
        runCatching { DueWidget.refresh(getApplication()) }
    }

    fun setGodTheme(choice: String) = viewModelScope.launch {
        GodTasks.setChoice(getApplication(), choice)
        addGodTasks()
    }

    /** Saves the ⋮ → God task counts and applies today's at once (adds more, or clears them at 0). */
    fun setGodCounts(weekday: Int, weekend: Int) = viewModelScope.launch {
        GodTasks.setCounts(getApplication(), weekday, weekend)
        if (runCatching { GodTasks.ensureToday(getApplication(), force = true) }.getOrDefault(false)) afterWrite()
    }

    /** Long-press "Another God task": swaps it for a new one from the same theme. Returns the new row. */
    suspend fun replaceGodTask(item: Item): Item? =
        GodTasks.replace(getApplication(), item).also { if (it != null) afterWrite() }

    private suspend fun addGodTasks() {
        if (runCatching { GodTasks.ensureToday(getApplication()) }.getOrDefault(false)) afterWrite()
    }

    /** Confirms a suggestion: it becomes a regular open action. */
    fun confirm(item: Item) = write {
        dao.update(item.copy(status = STATUS_OPEN))
        Series.topUpAll(db)
    }

    /** Confirms every suggestion currently on screen in one go. */
    fun confirmAll(items: List<Item>) = write {
        items.forEach { dao.update(it.copy(status = STATUS_OPEN)) }
        Series.topUpAll(db)
    }

    /** Dismisses a suggestion. The source stays in `processed`, so it never comes back. */
    fun dismiss(item: Item) = write { dao.delete(item) }

    /** Undo for a swiped suggestion: puts the row back exactly as it was (same id, still suggested). */
    fun restore(item: Item) = write { dao.upsert(listOf(item)) }

    /**
     * Saves the edit sheet. A new repeating action is filled three months ahead; for an
     * occurrence of a series, [scope] picks which occurrences change. Returns what Undo needs.
     */
    suspend fun save(original: Item, edited: Item, scope: SeriesScope = SeriesScope.THIS): Snapshot? =
        Series.save(db, original, edited, scope).also { afterWrite() }

    fun toggleDone(item: Item) = write {
        if (item.status == STATUS_DONE) Actions.reopen(db, item.id) else Actions.complete(db, item.id)
    }

    /** Swipe-delete; for an occurrence of a series, [scope] picks which occurrences go. Returns what Undo needs. */
    suspend fun delete(item: Item, scope: SeriesScope = SeriesScope.THIS): Snapshot =
        Series.delete(db, item, scope).also { afterWrite() }

    /** Undo for a delete or a scoped edit. */
    fun undo(snapshot: Snapshot) = write { Series.undo(db, snapshot) }

    /**
     * Runs a database change, then redraws the home-screen widget so it never shows
     * stale actions, and queues the automatic Drive backup.
     */
    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "Couldn't save that change: ${e.message}"
            return@launch
        }
        afterWrite()
    }

    private suspend fun afterWrite() {
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
    fun smartTitles() = viewModelScope.launch { runCatching { SmartTitles.run(getApplication()) } }

    /** [granted] is the scopes Google returned; [declined] names any source the user didn't allow. */
    fun sync(token: String, granted: Collection<String>, declined: List<String> = emptyList()) {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            val result = try {
                val n = SyncEngine.run(getApplication(), token, granted)
                if (n > 0) "$n new suggestion(s) to review" else "Nothing new"
            } catch (e: Exception) {
                "Sync failed: ${e.message}"
            }
            message = if (declined.isEmpty()) result
            else "$result. ${declined.joinToString(" and ")} not allowed, so " +
                (if (declined.size == 2) "suggestions are" else "its suggestions are") +
                " off. Turn back on under ⋮ → Permissions & features."
            syncing = false
            // Nano passes are best effort: a failure keeps the rule-based titles and retries next time.
            runCatching { SmartTitles.run(getApplication()) }
            if (GoogleAuth.GMAIL_SCOPE !in granted) return@launch
            val backfilled = runCatching { SmartTitles.backfill(getApplication(), token) }.getOrDefault(0)
            if (backfilled > 0) message = "Added email summaries to $backfilled earlier item(s)"
        }
    }
}
