# Action Tracker

A personal action-item tracker for Android (built for a Samsung S25 Ultra).

It scans your Gmail inbox and Google Calendar for things that need doing, **proposes** them in a
*Suggested* inbox, and only adds them to your list after you confirm. Everything is stored
encrypted on the phone.

## v1 features

- **Optional fingerprint lock** (off by default; turn it on from the ⋮ menu). BiometricPrompt with device PIN fallback, re-locks after 30 s in the background. Screenshots and the recents preview are always blocked.
- **Encrypted on-device storage**: Room + SQLCipher (AES-256). The passphrase is wrapped by a hardware-backed Android Keystore key. Backups are disabled.
- **Sources**: Gmail (inbox, excluding promotions/social, last 2 days), Google Calendar (next 7 days), and custom items (+ button).
- **Confirm before adding**: Gmail/Calendar items land in *Suggested* as compact rows; tap ✓ to add, ✕ to dismiss, the row to expand and edit, or "Add all".
- **Default due date is tomorrow** when the source has no date. A date in the text (e.g. "by Friday", "Oct 20", "2026-10-20", "tomorrow") overrides it. Editable any time.
- **Slim top bar**: Actions (opens first) and Suggested tabs, a compact one-line stats strip (late, today, open, inbox), and a small group-by dropdown, so the list gets almost all of the screen in portrait and landscape.
- **Swipe to delete**: swipe an action left or right to delete it, with Undo in the snackbar.
- **Group by** date, person (Me, Nathiya, Vibu, Dharshu, or anyone), or context (Work, Family, Kids/School).
- **No duplicates**: every Gmail message ID / calendar event ID is recorded the first time it is seen and never evaluated again, even if you dismiss the suggestion.
- **Background sync** every ~2 hours with a notification when new suggestions arrive.
- **Morning digest** (8 AM, on/off under ⋮ → Reminders): a summary of open actions due today or overdue plus a notification per task (up to 5) with Done and Snooze 30 min. The lock screen shows only the count.
- **Time of day**: every action has a Morning / Afternoon / Evening / Night slot (work contexts default to Afternoon, everything else to Evening). Group the list by Time to see them.
- **Time-of-day reminders** (⋮ → Reminders): a heads-up notification per slot (12 PM, 4 PM, 7 PM and 10 PM by default) listing that slot's open actions due today or overdue, plus a notification per task (up to 5) with Done and Snooze 30 min.
- **Watch-friendly notifications**: phone notifications mirror to a paired watch (built with a Galaxy Watch 3 in mind), so Done and Snooze work from the wrist. A silent "Add a task" notification has a reply box: dictate a task on the phone or watch and it is added with the voice-capture rules (default due tomorrow). Turn it off under ⋮ → Reminders.
- **Repeating actions**: set Repeat (daily, weekdays, weekly, monthly, yearly) in the edit sheet, or say it ("every Monday"). Ticking one done, from the app, widget or a reminder, queues the next date; deleting one skips to the next.
- **Voice capture**: long-press + and say the task ("remind me to sign Vibu's permission slip by Friday"); the title (minus "remind me to", filler and date words), due date, time of day, person, context and repeat are picked out of the sentence, and on-device Gemini Nano tidies the title. People are matched against everyone already used, including common mis-hearings ("Vibhu", "Darshu", "Dharsini"); when several are named, the one the task is for wins ("ask Nathiya to call Vibu's teacher" goes to Nathiya). Context falls back to that person's usual context.
- **Photo to task (on-device AI)**: tap +, then the camera icon in the New action header to take a photo or pick a screenshot, or share any image into Action Tracker. ML Kit reads the text, ML Kit entity extraction finds the dates and times (simple date rules until its small model has downloaded once), and Gemini Nano writes the title and a three-bullet summary when there is enough text. A deadline date ("return by Oct 24") wins over other dates; person and context use the voice-capture rules. Review the Photo draft, then Save. The photo is read on the phone and never stored or uploaded.
- **Calendar and stats**: the calendar icon in the top bar opens a month view of due and done actions plus completion stats.
- **Crash report**: if the app crashes, the next launch shows the stack trace so it can be shared.

- **Home-screen widget**: Today (overdue included) and Tomorrow tabs with their counts; tap a tab to see that day's open actions as compact one-line cards, with late-days, person and context tags next to the title. Tap the circle to mark an action done; the widget also updates on its own whenever you add, edit, complete or delete an action in the app. Tap anything else to open the app. Long-press the home screen → Widgets → Action Tracker to add it; it's resizable. Note that action titles are visible on the home screen even when the fingerprint lock is on.

- **Smart titles (Gemini Nano, on-device)**: when you open the app, Gmail suggestions are rewritten into a clean one-line title by Gemini Nano's summarizer. The rules still decide what is an action; nothing leaves the phone. The notes get a short three-bullet summary of the email, plus the original subject; tap a suggestion to read them. Gmail suggestions and open actions from before this update get a summary once, on the next sync with the app open (the email is fetched again from Gmail; titles are left as they are). Needs a Nano-capable phone (the S25 Ultra is supported) and emails of at least ~400 characters; otherwise the rule-based title stays. Google's general Prompt API, which could extract actions directly, doesn't support the S25 Ultra yet.
- **Encrypted Google Drive backup** (⋮ → Google Drive backup): backs up to the app's hidden Drive folder a couple of minutes after any change and once a day, encrypted on the phone with your backup passphrase (AES-256-GCM, PBKDF2 key). After a reinstall or on a new phone, the app offers to look for a backup on first launch; sign in with the same Google account and enter the passphrase. Backups carry the database schema version and restore across app versions. Google can't read it, and a forgotten passphrase can't be recovered.

## One-time Google setup (required for Gmail + Calendar)

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and enable the **Gmail API**, **Google Calendar API** and **Google Drive API** (Drive is only for the optional backup).
2. OAuth consent screen: user type **External**, leave publishing status as **Testing**, add scopes
   `.../auth/gmail.readonly`, `.../auth/calendar.readonly` and `.../auth/drive.appdata`, and add **your own Gmail address as a test user**.
3. Credentials → Create OAuth client ID → **Android**:
   - Package name: `com.sonas.actiontracker`
   - SHA-1: the fingerprint of the key that signs your APK (see below).

> In Testing mode Google expires the grant every **7 days**; the app will notify you to sign in again. `gmail.readonly` is a restricted scope, so publishing the app widely would require Google's verification. That isn't needed for personal use.

## Building

**GitHub Actions (no local setup):** push to `main`, open the *Build APK* workflow run, and download the `action-tracker-debug-apk` artifact. Sideload it onto the phone.

For a stable signing key (so the Google OAuth SHA-1 never changes), create a keystore once and add four repo secrets:

```bash
keytool -genkeypair -v -keystore release.jks -alias tracker -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # paste as the KEYSTORE_BASE64 secret
keytool -list -v -keystore release.jks -alias tracker   # copy the SHA-1 into the OAuth client
```

Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never commit the keystore (it is git-ignored).
Without these secrets the workflow signs with a throwaway debug key, and the SHA-1 changes on every build, so Google sign-in will fail.

**Android Studio:** open the folder, let it sync (it generates the Gradle wrapper), and run on the phone. Get the debug SHA-1 with `./gradlew signingReport`.

## Stack

Kotlin 2 · Jetpack Compose (Material 3) · Room + SQLCipher · AndroidX Biometric · WorkManager + AlarmManager · Glance widget · ML Kit GenAI (Gemini Nano) · ML Kit text recognition + entity extraction · Google Identity Authorization API + Gmail/Calendar/Drive REST.

## Look and feel

Light "Fresh" theme: airy off-white canvas with slowly drifting pastel glows, an
indigo → sky → teal accent gradient and white glass cards. One slim top bar keeps the list
front and centre, with an icon-only + button.

## Working on the code

`CLAUDE.md` describes the code layout, build commands and the rules every change follows
(database migrations, privacy, UI style); `.claude/` holds the matching rules, skills and hooks
used by Claude Code.
