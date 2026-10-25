package com.sonas.actiontracker.ui

import com.sonas.actiontracker.sync.Features

/** The Reminders row's one-line summary under Settings, e.g. "8 AM digest · 4 pop-ups a day". */
fun remindersSummary(digest: Boolean, popups: Int): String {
    val pops = if (popups == 1) "1 pop-up a day" else "$popups pop-ups a day"
    return when {
        digest && popups > 0 -> "8 AM digest · $pops"
        digest -> "8 AM digest only"
        popups > 0 -> pops
        else -> "Off"
    }
}

/** The God task row's summary: "Random · 1 weekday, 3 weekend", or "Off". */
fun godTaskSummary(themeLabel: String, off: Boolean, weekday: Int, weekend: Int): String {
    if (off || (weekday == 0 && weekend == 0)) return "Off"
    fun n(count: Int) = if (count == 0) "none" else "$count"
    return "$themeLabel · ${n(weekday)} weekday, ${n(weekend)} weekend"
}

/** The Drive backup row's summary: "Off", "On · not backed up yet" or "On · backed up 2 h ago". */
fun backupSummary(enabled: Boolean, lastBackupAt: Long?, now: Long): String {
    if (!enabled) return "Off"
    val last = lastBackupAt ?: return "On · not backed up yet"
    val minutes = ((now - last) / 60_000).coerceAtLeast(0)
    val ago = when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 48 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
    return "On · backed up $ago"
}

/** The Permissions & features row's summary: "Everything on", or how many run basic or are off. */
fun featuresSummary(states: List<Features.State>): String {
    val basic = states.count { it == Features.State.BASIC }
    val off = states.count { it == Features.State.OFF }
    return listOfNotNull(
        "$basic basic".takeIf { basic > 0 },
        "$off off".takeIf { off > 0 }
    ).joinToString(" · ").ifEmpty { "Everything on" }
}
