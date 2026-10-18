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
- Thumb-first (since 2026-10-18): the slim top bar holds only the Late/Today/Open/Inbox pills
  (44dp tall, big numbers). Everything tappable is in the bottom dock: a tools row (left:
  contextual chip, group-by on Actions or "Add all" on Suggested; middle: today's date; right:
  calendar/stats, sync, ⋮ under the right thumb), then the type-to-capture bar, then the
  Actions | Suggested tabs. Icons never shrink or get covered. On wide/landscape windows the dock
  is one row: tabs, type bar, tools. While the keyboard is up only the type bar shows.
- Calendar and Stats (`ui/Insights.kt`) mirror it: the same stats top bar, then content; bottom dock with the month
  switcher left and close right (where the calendar icon sits on the main screen), then the
  Calendar | Stats tabs.
- Three zones, visibly separate: the stats bar and the bottom dock are solid white `panel()`s
  (rounded inner corners, soft indigo shadow, running under the system bars); the list between
  them stays on the aurora canvas. Controls on a panel use `well()` (canvas tint + rim), and the
  stat pills are tinted in their own colour, so pills, controls and list rows never look alike.
- No tall headlines or stat tiles. Both tabs always show a count badge, dimmed at zero, so nothing
  reflows on switch.
- The Actions list is anchored to the bottom (`reverseLayout`): the first section's first row sits
  right above the dock, later sections and Done float up. Each section emits rows, then its header.
- Actions is the first/default tab, Suggested second. Default grouping is Date; Time, Person
  and Context are the alternatives.
- Suggested is a swipe deck of cards: right adds, left skips, up edits first; buttons below do the
  same; Undo in the snackbar.
- The type bar parses with `VoiceDraft.fromRules` and shows chips live. Empty, it offers the mic
  and an icon-only `+` (tap: edit sheet, long-press: voice capture); there is no floating button.
  No per-row delete icon: swipe a row left or right to delete, with Undo in the snackbar.
- The edit sheet is compact (at most 3/4 of the screen). Person and Context are typeable
  dropdowns of every value already used, so nothing is retyped; no chip rows under them.
  Due/Time and Repeat/Ends sit side by side as dropdown pills.
- Rows stay white; don't put tinted layers behind translucent rows.

Widget (`widget/DueWidget.kt`)
- Today/Tomorrow tabs, single-line card rows grouped under time-of-day headers, person tag on
  every row (including "Me"). Fixed light colors to match the app.
- No summary text and no refresh button; Sona removed both. It updates on its own.

Resources
- Escape apostrophes in `strings.xml` (`\'`), or the build fails.
- Prefer `material-icons-core` icons or a small drawable; the extended icon set isn't a dependency.
