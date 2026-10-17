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
- One slim top bar; the list gets almost all of the screen in portrait and landscape. No tall
  headlines or stat tiles; stats stay as the compact Late/Today/Open/Inbox pills.
- Actions is the first/default tab, Suggested second. Default grouping is Date; Time, Person
  and Context are the alternatives.
- The add button is an icon-only `+` (long-press opens voice capture). No per-row delete icon:
  swipe left or right deletes, with Undo in the snackbar.
- The edit sheet offers every person/context already used as chips so nothing is retyped.
- Rows stay white; don't put tinted layers behind translucent rows.

Widget (`widget/DueWidget.kt`)
- Today/Tomorrow tabs, single-line card rows grouped under time-of-day headers, person tag on
  every row (including "Me"). Fixed light colors to match the app.
- No summary text and no refresh button; Sona removed both. It updates on its own.

Resources
- Escape apostrophes in `strings.xml` (`\'`), or the build fails.
- Prefer `material-icons-core` icons or a small drawable; the extended icon set isn't a dependency.
