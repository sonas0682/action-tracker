package com.sonas.actiontracker.ui

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN

/** One row of the Contexts list: a context with its open and done counts. */
data class ContextSummary(val name: String, val open: Int, val done: Int)

/** Context names match ignoring case and surrounding spaces, as the edit sheet's dropdown does. */
fun sameContext(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

/** Every action, open or done, filed under [context]. */
fun List<Item>.inContext(context: String): List<Item> = filter { sameContext(it.context, context) }

/**
 * The Contexts list: every context used by an action, busiest first (most open, then most done),
 * then by name. [extra] adds contexts with no actions yet (one just typed in) at zero.
 * Spelling follows the most recent action, so "work" and "Work" are one row.
 */
fun contextSummaries(actions: List<Item>, extra: List<String> = emptyList()): List<ContextSummary> {
    val byKey = actions.filter { it.context.isNotBlank() }
        .groupBy { it.context.trim().lowercase() }
        .map { (_, list) ->
            ContextSummary(
                name = list.maxBy { it.createdAt }.context.trim(),
                open = list.count { it.status == STATUS_OPEN },
                done = list.count { it.status == STATUS_DONE }
            )
        }
    val missing = extra.map { it.trim() }.filter { e -> e.isNotEmpty() && byKey.none { sameContext(it.name, e) } }
        .distinctBy { it.lowercase() }
        .map { ContextSummary(it, 0, 0) }
    return (byKey + missing).sortedWith(
        compareByDescending<ContextSummary> { it.open }.thenByDescending { it.done }.thenBy { it.name.lowercase() }
    )
}
