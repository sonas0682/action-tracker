package com.sonas.actiontracker

import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.TimeOfDay
import com.sonas.actiontracker.sync.SOURCE_GOD
import com.sonas.actiontracker.ui.AppLayout
import com.sonas.actiontracker.ui.DayPage
import com.sonas.actiontracker.ui.Group
import com.sonas.actiontracker.ui.LedgerFilter
import com.sonas.actiontracker.ui.allDone
import com.sonas.actiontracker.ui.badges
import com.sonas.actiontracker.ui.canFavorite
import com.sonas.actiontracker.ui.canMoveToTomorrow
import com.sonas.actiontracker.ui.dialParts
import com.sonas.actiontracker.ui.digestHeading
import com.sonas.actiontracker.ui.layoutStatLines
import com.sonas.actiontracker.ui.longestStreak
import com.sonas.actiontracker.ui.reflectionDue
import com.sonas.actiontracker.ui.slotHeading
import com.sonas.actiontracker.ui.widgetTitle
import com.sonas.actiontracker.data.Thoughts
import com.sonas.actiontracker.sync.GodTasks
import com.sonas.actiontracker.data.GodTheme
import com.sonas.actiontracker.sync.GoogleAuth
import org.junit.Assert.assertNull
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random
import com.sonas.actiontracker.ui.dialAngle
import com.sonas.actiontracker.ui.finaleLine
import com.sonas.actiontracker.ui.layoutSummary
import com.sonas.actiontracker.ui.ledgerFilter
import com.sonas.actiontracker.ui.ledgerSections
import com.sonas.actiontracker.ui.Quest
import com.sonas.actiontracker.ui.contextCode
import com.sonas.actiontracker.ui.lateText
import com.sonas.actiontracker.ui.layoutSections
import com.sonas.actiontracker.ui.personCode
import com.sonas.actiontracker.ui.slotAt
import com.sonas.actiontracker.ui.splitUpcoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class LayoutLogicTest {
    private val today = 20_000L
    private var nextId = 1L

    private fun item(
        title: String, due: Long = today, time: TimeOfDay = TimeOfDay.EVENING, done: Boolean = false,
        source: String = "MANUAL", seriesId: Long? = null, completedAt: Long? = null, person: String = "Me", context: String = "Work"
    ) = Item(
        id = nextId++, title = title, person = person, context = context, dueDate = due, status = if (done) STATUS_DONE else STATUS_OPEN,
        source = source, timeOfDay = time.name, seriesId = seriesId, completedAt = completedAt
    )

    @Test fun unknownLayoutNameFallsBackToClassic() {
        assertEquals(AppLayout.CLASSIC, AppLayout.of(null))
        assertEquals(AppLayout.CLASSIC, AppLayout.of("GONE"))
        assertEquals(AppLayout.METRO, AppLayout.of("METRO"))
    }

    @Test fun todayReadsLateThenMorningToNight() {
        val sections = layoutSections(
            listOf(
                item("night", time = TimeOfDay.NIGHT),
                item("old", due = today - 2),
                item("done morning", time = TimeOfDay.MORNING, done = true),
                item("morning", time = TimeOfDay.MORNING)
            ),
            DayPage.TODAY, today
        ) { "day $it" }
        assertEquals(listOf("Late", "Morning", "Night"), sections.map { it.label })
        assertTrue(sections[0].late)
        assertEquals(listOf("morning", "done morning"), sections[1].items.map { it.title })
        assertEquals(TimeOfDay.NIGHT, sections[2].slot)
    }

    @Test fun doneOverdueActionIsNotLate() {
        val sections = layoutSections(listOf(item("paid", due = today - 1, done = true)), DayPage.TODAY, today) { "" }
        assertEquals(listOf("Evening"), sections.map { it.label })
    }

    @Test fun nextPageIsOneBlockPerDate() {
        val sections = layoutSections(
            listOf(item("b", due = today + 5), item("a2", due = today + 2, time = TimeOfDay.NIGHT), item("a1", due = today + 2, time = TimeOfDay.MORNING)),
            DayPage.LATER, today
        ) { "day ${it - today}" }
        assertEquals(listOf("day 2", "day 5"), sections.map { it.label })
        assertEquals(listOf("a1", "a2"), sections[0].items.map { it.title })
    }

    @Test fun farRepeatsFoldUnderUpcoming() {
        val near = item("near repeat", due = today + 7, seriesId = 1)
        val far = item("far repeat", due = today + 8, seriesId = 1)
        val oneOff = item("one-off", due = today + 30)
        val (upcoming, shown) = splitUpcoming(listOf(near, far, oneOff), today)
        assertEquals(listOf(far), upcoming)
        assertEquals(listOf(near, oneOff), shown)
    }

    @Test fun clockHoursMapToSlots() {
        assertEquals(TimeOfDay.MORNING, slotAt(6))
        assertEquals(TimeOfDay.AFTERNOON, slotAt(12))
        assertEquals(TimeOfDay.EVENING, slotAt(17))
        assertEquals(TimeOfDay.NIGHT, slotAt(21))
    }

    @Test fun codesAndLateText() {
        assertEquals("ME", personCode("Me"))
        assertEquals("KID", personCode("Kiddo"))
        assertEquals("KIDS/SCH", contextCode("Kids/School"))
        assertEquals("1 day late", lateText(today - 1, today))
        assertEquals("3 days late", lateText(today - 3, today))
    }

    @Test fun xpCountsOnlyFinishedWork() {
        val items = listOf(item("a", done = true), item("b"), item("g", done = true, source = SOURCE_GOD))
        assertEquals(Quest.TASK_XP + Quest.GOD_XP, Quest.totalXp(items))
    }

    @Test fun levelsGetLonger() {
        assertEquals(1, Quest.levelOf(0))
        assertEquals(1, Quest.levelOf(99))
        assertEquals(2, Quest.levelOf(100))
        assertEquals(3, Quest.levelOf(300))
        assertEquals(3, Quest.levelOf(599))
        assertEquals("First Step", Quest.title(1))
        assertEquals("Mountain Mover", Quest.title(99))
    }

    @Test fun streakSurvivesUntilTodayIsOver() {
        assertEquals(0, Quest.streak(emptySet(), today))
        assertEquals(3, Quest.streak(setOf(today, today - 1, today - 2, today - 4), today))
        assertEquals(2, Quest.streak(setOf(today - 1, today - 2), today))
        assertEquals(0, Quest.streak(setOf(today - 2), today))
    }

    @Test fun doneDaysUseTheCompletionDate() {
        val dayMs = 86_400_000L
        val items = listOf(
            item("a", done = true, completedAt = today * dayMs + 1000),
            item("b", completedAt = (today - 1) * dayMs),
            item("c", done = true)
        )
        assertEquals(setOf(today), Quest.doneDays(items, ZoneOffset.UTC))
    }

    @Test fun ledgerRegroupsByPersonContextAndDate() {
        val items = listOf(
            item("b", person = "Zed", context = "Home", due = today + 3),
            item("a", person = "Me", context = "Work", due = today + 2),
            item("c", person = "Me", context = "Home", due = today + 2, done = true)
        )
        assertEquals(listOf("Me", "Zed"), ledgerSections(items, Group.PERSON, DayPage.LATER, today) { "" }.map { it.label })
        val byContext = ledgerSections(items, Group.CONTEXT, DayPage.LATER, today) { "" }
        assertEquals(listOf("Home", "Work"), byContext.map { it.label })
        assertEquals(listOf("b", "c"), byContext[0].items.map { it.title })
        assertEquals(listOf("day 2", "day 3"), ledgerSections(items, Group.DATE, DayPage.LATER, today) { "day ${it - today}" }.map { it.label })
        assertEquals(listOf("Evening"), ledgerSections(items, Group.TIME, DayPage.LATER, today) { "" }.map { it.label })
    }

    @Test fun ledgerFilterNarrowsTheTable() {
        val items = listOf(item("late", due = today - 1), item("open"), item("done", done = true))
        assertEquals(3, items.ledgerFilter(null, today).size)
        assertEquals(listOf("late", "open"), items.ledgerFilter(LedgerFilter.OPEN, today).map { it.title })
        assertEquals(listOf("late"), items.ledgerFilter(LedgerFilter.LATE, today).map { it.title })
        assertEquals(listOf("done"), items.ledgerFilter(LedgerFilter.DONE, today).map { it.title })
    }

    @Test fun dialClockGoesRoundOnceADay() {
        assertEquals(0f, dialAngle(3, 0), 0.01f)
        assertEquals(0f, dialAngle(6, 0), 0.01f)
        assertEquals(45f, dialAngle(9, 0), 0.01f)
        assertEquals(90f, dialAngle(12, 0), 0.01f)
        assertEquals(180f, dialAngle(17, 0), 0.01f)
        assertEquals(270f, dialAngle(21, 0), 0.01f)
        assertEquals(315f, dialAngle(22, 30), 0.01f)
    }

    @Test fun allDoneNeedsSomethingFinished() {
        assertFalse(allDone(emptyList()))
        assertFalse(allDone(listOf(item("a", done = true), item("b"))))
        assertTrue(allDone(listOf(item("a", done = true))))
        assertEquals(finaleLine(today), finaleLine(today))
        assertTrue(finaleLine(-3).isNotEmpty())
    }

    @Test fun layoutSummaryNamesPagesOnlyWhenTheyDiffer() {
        assertEquals("Ledger on every page", layoutSummary(AppLayout.LEDGER, AppLayout.LEDGER, AppLayout.LEDGER))
        assertEquals("Now Quest Path · Near Classic · Next Ledger", layoutSummary(AppLayout.QUEST, AppLayout.CLASSIC, AppLayout.LEDGER))
    }

    @Test fun xpTodayCountsOnlyTodaysFinishes() {
        val dayMs = 86_400_000L
        val items = listOf(
            item("a", done = true, completedAt = today * dayMs + 5),
            item("g", done = true, source = SOURCE_GOD, completedAt = today * dayMs + 9),
            item("old", done = true, completedAt = (today - 1) * dayMs)
        )
        assertEquals(Quest.TASK_XP + Quest.GOD_XP, Quest.xpOn(items, today, ZoneOffset.UTC))
    }

    private val dayMs = 86_400_000L

    @Test fun remindersSpeakInTheLayoutsVoice() {
        assertNull(slotHeading(AppLayout.CLASSIC, TimeOfDay.EVENING, 3, 60))
        assertEquals("🌇 3 stops left on the evening line", slotHeading(AppLayout.METRO, TimeOfDay.EVENING, 3, 60))
        assertEquals("🌇 1 evening step left · 20 XP waiting", slotHeading(AppLayout.QUEST, TimeOfDay.EVENING, 1, 20))
        assertNull(digestHeading(AppLayout.CLASSIC, 5, 1))
        assertEquals("Today's quest: 5 steps, 1 late", digestHeading(AppLayout.QUEST, 5, 1))
        assertEquals("Today's line: 1 stop", digestHeading(AppLayout.METRO, 1, 0))
    }

    @Test fun widgetTitleFollowsTheLayout() {
        val open = listOf(item("night", time = TimeOfDay.NIGHT), item("late", due = today - 1, time = TimeOfDay.AFTERNOON))
        val done = listOf(item("a", done = true, time = TimeOfDay.AFTERNOON, completedAt = today * dayMs + 5))
        assertEquals("Actions", widgetTitle(AppLayout.CLASSIC, open, done, today, ZoneOffset.UTC))
        assertEquals("Open 2 · Late 1 · Done 1", widgetTitle(AppLayout.LEDGER, open, done, today, ZoneOffset.UTC))
        assertEquals("Level 1 · 1 day streak", widgetTitle(AppLayout.QUEST, open, done, today, ZoneOffset.UTC))
        assertEquals("1 / 3 done", widgetTitle(AppLayout.DIAL, open, done, today, ZoneOffset.UTC))
        assertEquals("Next stop: late", widgetTitle(AppLayout.METRO, open, done, today, ZoneOffset.UTC))
        assertEquals("End of the line", widgetTitle(AppLayout.METRO, emptyList(), done, today, ZoneOffset.UTC))
        assertEquals(listOf(0f, 0.5f, 0f, 0f), dialParts(open, done, today, ZoneOffset.UTC))
    }

    @Test fun longestStreakFindsTheBestRun() {
        assertEquals(0, longestStreak(emptySet()))
        assertEquals(3, longestStreak(setOf(1L, 2L, 3L, 7L, 8L)))
    }

    @Test fun badgesAreEarnedFromHistory() {
        val none = badges(emptyList(), today, ZoneOffset.UTC)
        assertTrue(none.none { it.earned })
        val week = (0L..6L).map { item("d$it", due = today - it, done = true, completedAt = (today - it) * dayMs + 1) }
        val earned = badges(week, today, ZoneOffset.UTC).filter { it.earned }.map { it.name }
        assertEquals(listOf("First Step", "Week of Fire", "On-Time Week"), earned)
        val withLate = badges(week + item("late", due = today - 1), today, ZoneOffset.UTC).filter { it.earned }.map { it.name }
        assertEquals(listOf("First Step", "Week of Fire"), withLate)
    }

    @Test fun monthCardSpeaksInTheLayout() {
        val month = YearMonth.from(LocalDate.ofEpochDay(today))
        val items = listOf(item("a", done = true, completedAt = today * dayMs + 1), item("b"))
        assertNull(layoutStatLines(AppLayout.CLASSIC, items, month, today, emptySet(), ZoneOffset.UTC))
        val ledger = layoutStatLines(AppLayout.LEDGER, items, month, today, emptySet(), ZoneOffset.UTC)!!
        assertEquals("MONTH STATEMENT", ledger.first)
        assertEquals(listOf("OPENED   2", "CLOSED   1", "CARRIED  0", "RATE     50%"), ledger.second)
        val journal = layoutStatLines(AppLayout.JOURNAL, items, month, today, setOf(today), ZoneOffset.UTC)!!
        assertEquals(listOf("1 evenings written", "1 lines crossed off"), journal.second)
    }

    @Test fun longPressOffersMoveAndFavoriteOnlyWhereTheyFit() {
        assertTrue(canMoveToTomorrow(item("a"), today))
        assertTrue(canMoveToTomorrow(item("late", due = today - 2), today))
        assertFalse(canMoveToTomorrow(item("tomorrow", due = today + 1), today))
        assertFalse(canMoveToTomorrow(item("done", done = true), today))
        assertFalse(canMoveToTomorrow(item("god", source = SOURCE_GOD), today))
        assertTrue(canFavorite(item("god", source = SOURCE_GOD)))
        assertTrue(canFavorite(item("thought", source = "THOUGHT")))
        assertFalse(canFavorite(item("plain")))
    }

    @Test fun reflectionIsAskedInTheEveningUntilWritten() {
        assertTrue(reflectionDue(true, 19, false))
        assertFalse(reflectionDue(true, 18, false))
        assertFalse(reflectionDue(true, 21, true))
        assertFalse(reflectionDue(false, 21, false))
    }

    @Test fun signInWarningComesOnceOnTheLastDay() {
        val granted = 1_000L
        assertFalse(GoogleAuth.signInWarningDue(0L, 0L, 10 * dayMs))
        assertFalse(GoogleAuth.signInWarningDue(granted, 0L, granted + 5 * dayMs))
        assertTrue(GoogleAuth.signInWarningDue(granted, 0L, granted + 6 * dayMs))
        assertFalse(GoogleAuth.signInWarningDue(granted, granted, granted + 6 * dayMs))
    }

    @Test fun favoriteThoughtsComeBackButNeverTwiceRunning() {
        assertEquals(setOf(0), Thoughts.indexesOf(listOf(Thoughts.all[0].text, "not a thought")))
        val random = Random(42)
        val picks = (1..200).map { Thoughts.next(5, random, favorites = setOf(5, 9)) }
        assertFalse(5 in picks)
        assertTrue(picks.count { it == 9 } > 20)
    }

    @Test fun favoriteGodTasksMayRepeatSooner() {
        val theme = GodTheme.entries.first()
        // Every task was used 20 days ago: none is fresh, unless it is a favourite (14-day wait).
        val used = theme.tasks.indices.associate { GodTasks.key(theme, it) to today - 20 }
        val favorite = GodTasks.key(theme, 3)
        assertEquals(3, GodTasks.pickIndex(theme, used, today, Random(1), setOf(favorite)))
        val row = item(theme.tasks[3].replace("{name}", "Me"), source = SOURCE_GOD).copy(sourceRef = theme.name)
        assertEquals(favorite, GodTasks.keyOf(row))
        assertNull(GodTasks.keyOf(row.copy(title = "edited by hand")))
    }
}
