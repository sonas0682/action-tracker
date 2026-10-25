package com.sonas.actiontracker

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.ui.ContextSummary
import com.sonas.actiontracker.ui.contextSummaries
import com.sonas.actiontracker.ui.inContext
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextPagesTest {
    private fun item(context: String, status: String = STATUS_OPEN, createdAt: Long = 0) =
        Item(title = "t", context = context, dueDate = 20_000, status = status, source = "CUSTOM", createdAt = createdAt)

    @Test fun pageHoldsOpenAndDoneIgnoringCase() {
        val actions = listOf(item("Certification"), item("certification ", STATUS_DONE), item("Work"))
        assertEquals(2, actions.inContext("Certification").size)
    }

    @Test fun busiestContextFirstAndSpellingFollowsLatest() {
        val actions = listOf(
            item("work", createdAt = 1), item("Work", createdAt = 2), item("Work", STATUS_DONE),
            item("Family", STATUS_DONE), item("Family", STATUS_DONE), item("Kids", STATUS_DONE)
        )
        assertEquals(
            listOf(ContextSummary("Work", 2, 1), ContextSummary("Family", 0, 2), ContextSummary("Kids", 0, 1)),
            contextSummaries(actions)
        )
    }

    @Test fun typedContextShowsAtZeroOnce() {
        val summaries = contextSummaries(listOf(item("Work")), extra = listOf(" Certification", "work", "certification"))
        assertEquals(listOf(ContextSummary("Work", 1, 0), ContextSummary("Certification", 0, 0)), summaries)
    }
}
