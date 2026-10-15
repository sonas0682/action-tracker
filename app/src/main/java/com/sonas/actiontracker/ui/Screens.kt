package com.sonas.actiontracker.ui

import android.app.DatePickerDialog
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.STATUS_SUGGESTED
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Group(val label: String) { DATE("Date"), PERSON("Person"), CONTEXT("Context") }

private val PEOPLE = listOf("Me", "Nathiya", "Vibu", "Dharshu")
private val CONTEXTS = listOf("Work", "Family", "Kids/School")

private fun fullDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d"))

private fun dateLabel(epochDay: Long): String {
    val diff = epochDay - LocalDate.now().toEpochDay()
    return when {
        diff < 0 -> "Overdue"
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        else -> fullDate(epochDay)
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Action Tracker is locked", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 16.dp)) { Text("Unlock with fingerprint") }
    }
}

@Composable
fun AppScreen(vm: MainVM, onSync: () -> Unit) {
    val suggested by vm.suggested.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var group by remember { mutableStateOf(Group.DATE) }
    var editing by remember { mutableStateOf<Item?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Item.blank() }) { Text("+") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Action Tracker", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onSync, enabled = !vm.syncing) {
                    Text(if (vm.syncing) "Syncing…" else "Sync")
                }
            }
            vm.message?.let {
                Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Suggested (${suggested.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Actions") })
            }
            if (tab == 0) {
                SuggestedList(suggested, vm, onEdit = { editing = it })
            } else {
                ActionsList(actions, group, onGroup = { group = it }, vm = vm, onEdit = { editing = it })
            }
        }
    }

    editing?.let { item ->
        EditDialog(item, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null })
    }
}

@Composable
private fun SuggestedList(items: List<Item>, vm: MainVM, onEdit: (Item) -> Unit) {
    if (items.isEmpty()) {
        Text(
            "No suggestions. Tap Sync to scan Gmail and Calendar.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.notes.isNotBlank()) {
                        Text(item.notes, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "${item.source.lowercase()} · due ${fullDate(item.dueDate)} · ${item.person} · ${item.context}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.confirm(item) }) { Text("Add") }
                        TextButton(onClick = { onEdit(item) }) { Text("Edit") }
                        TextButton(onClick = { vm.dismiss(item) }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsList(
    actions: List<Item>,
    group: Group,
    onGroup: (Group) -> Unit,
    vm: MainVM,
    onEdit: (Item) -> Unit
) {
    val open = actions.filter { it.status == STATUS_OPEN }
    val done = actions.filter { it.status == STATUS_DONE }
    val grouped: List<Pair<String, List<Item>>> = when (group) {
        Group.DATE -> open.groupBy { dateLabel(it.dueDate) }.toList()
        Group.PERSON -> open.groupBy { it.person }.toList().sortedBy { it.first }
        Group.CONTEXT -> open.groupBy { it.context }.toList().sortedBy { it.first }
    } + if (done.isNotEmpty()) listOf("Done" to done) else emptyList()

    Column {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Group by", style = MaterialTheme.typography.labelMedium)
            Group.entries.forEach {
                FilterChip(selected = group == it, onClick = { onGroup(it) }, label = { Text(it.label) })
            }
        }
        if (grouped.isEmpty()) {
            Text("No actions yet. Tap + to add one.", Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
            grouped.forEach { (label, list) ->
                item(key = "h-$label") {
                    Text(
                        label,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(list, key = { it.id }) { item ->
                    val isDone = item.status == STATUS_DONE
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isDone, onCheckedChange = { vm.toggleDone(item) })
                        Column(Modifier.weight(1f).clickable { onEdit(item) }.padding(vertical = 6.dp)) {
                            Text(
                                item.title,
                                textDecoration = if (isDone) TextDecoration.LineThrough else null
                            )
                            Text(
                                "${fullDate(item.dueDate)} · ${item.person} · ${item.context}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { vm.delete(item) }) { Text("✕") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditDialog(item: Item, onDismiss: () -> Unit, onSave: (Item) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(item.title) }
    var person by remember { mutableStateOf(item.person) }
    var context by remember { mutableStateOf(item.context) }
    var due by remember { mutableLongStateOf(item.dueDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.id == 0L) "New action" else "Edit action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Action") })
                OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Person") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PEOPLE.forEach { FilterChip(selected = person == it, onClick = { person = it }, label = { Text(it) }) }
                }
                OutlinedTextField(value = context, onValueChange = { context = it }, label = { Text("Context") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CONTEXTS.forEach { FilterChip(selected = context == it, onClick = { context = it }, label = { Text(it) }) }
                }
                TextButton(onClick = {
                    val d = LocalDate.ofEpochDay(due)
                    DatePickerDialog(
                        ctx,
                        { _, y, m, day -> due = LocalDate.of(y, m + 1, day).toEpochDay() },
                        d.year, d.monthValue - 1, d.dayOfMonth
                    ).show()
                }) { Text("Due: ${fullDate(due)}") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        item.copy(
                            title = title.trim(),
                            person = person.trim().ifEmpty { "Me" },
                            context = context.trim().ifEmpty { "Work" },
                            dueDate = due
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
