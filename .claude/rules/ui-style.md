---
paths:
  - "app/src/main/java/com/sonas/actiontracker/ui/**"
  - "app/src/main/java/com/sonas/actiontracker/widget/**"
  - "app/src/main/res/**"
---

# UI style (Sona's preferences)

App
- Light "Fresh" theme (`ui/Theme.kt`): off-white canvas, white glass cards, indigo/sky/teal
  accents. Not a traditional Material look, and not dark.
- Thumb-first (since 2026-10-18; reshaped 2026-10-20 at Sona's request): the slim top bar is a
  page header, a small caption over a title: today's/tomorrow's full date, "Later", "Suggested", or
  the month on Calendar and Stats with ‹ › arrows. No stats pills on top. The bottom dock has only
  two rows: [group-by chip (or "All N" on Suggested) | type-to-capture bar | ⋮ menu], then the tabs.
  Sync lives in the ⋮ menu (and pull-to-refresh), not as a dock icon. Icons never shrink or get
  covered. On wide/landscape windows the dock is one row: tabs, chip, type bar, menu. While the
  keyboard is up only the type bar shows.
- Calendar and Stats (`ui/Insights.kt` `InsightsPage`) are the last two pages of the same pager,
  sharing the header's month.
- Three zones, visibly separate: the header and the bottom dock are solid white `panel()`s
  (rounded inner corners, soft indigo shadow, running under the system bars); the list between
  them sits low on a sunken `Fresh.Recess` tint over the aurora, with solid white rows. Controls on a panel use `well()` (canvas tint + rim), and the
  stat pills are tinted in their own colour, so pills, controls and list rows never look alike.
- No tall headlines or stat tiles. Task tabs always show their count, dimmed at zero, so nothing
  reflows on switch.
- The Actions list is anchored to the bottom (`reverseLayout`): the first section's first row sits
  right above the dock, later sections and Done float up. Each section emits rows, then its header.
- The dock's one tab row is Today | Tomorrow | Later | Suggested (label over count) plus icon-only
  Calendar and Stats tabs, so six fit a phone. Today opens on launch; overdue and anything finished today show on Today. The pages sit
  in a `HorizontalPager`: swipe sideways anywhere (or tap a tab) to move between them; Back goes to Today.
  Each day page has its own grouping from the chip: Today and Tomorrow default to Time, Later to
  Date (Sona's choice 2026-10-20); Person and Context are the alternatives.
- Calendar's day list is one list (no time-of-day sub-headers), each row with its time-of-day icon
  on the right.
- Suggested is a swipe deck of cards: right adds, left skips, up edits first; buttons below do the
  same; Undo in the snackbar.
- The type bar parses with `VoiceDraft.fromRules` and shows chips live. Empty, it offers the mic
  and an icon-only `+` (tap: edit sheet, long-press: voice capture); there is no floating button.
  No per-row delete icon: long-press a row for Delete, with Undo in the snackbar (Sona chose this
  2026-10-20 so sideways swipes always change pages). Suggested cards keep their own swipes.
- The edit sheet is compact (at most 3/4 of the screen). Person and Context are typeable
  dropdowns of every value already used, so nothing is retyped; no chip rows under them.
  Due/Time and Repeat/Ends sit side by side as dropdown pills.
- Rows stay white (solid `glass(strong = true)` in the list, so the recess tint never shows
  through); don't put tinted layers behind translucent rows.

Widget (`widget/DueWidget.kt`)
- Today/Tomorrow tabs, single-line card rows grouped under time-of-day headers, person tag on
  every row (including "Me"). Fixed light colors to match the app.
- No summary text and no refresh button; Sona removed both. It updates on its own.

Resources
- Escape apostrophes in `strings.xml` (`\'`), or the build fails.
- Prefer `material-icons-core` icons or a small drawable; the extended icon set isn't a dependency.
