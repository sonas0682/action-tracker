# Action Tracker

Action Tracker is an Android to-do app for the things you must act on. It finds tasks in
your Gmail and Google Calendar, suggests them to you, and adds them to your list only when you
say yes. You can also add tasks by typing, speaking, taking a photo or sharing text from
another app. Your tasks are stored encrypted on your phone.

## What you need

- An Android phone or tablet running **Android 11 or newer**. Any brand works.
- A Google account, but only if you want suggestions from Gmail and Calendar or a Drive backup.
  The app works fully without one.
- For the smart AI wording: a phone that supports Gemini Nano (for example recent Pixel and
  Galaxy flagships). On other phones the app uses its built-in rules instead; see
  [AI and basic mode](#ai-and-basic-mode).

## Install

- **Google Play**: coming soon (see [Publishing on Google Play](#publishing-on-google-play)).
- **APK**: open the latest green *Build APK* run under the repo's **Actions** tab, download
  `action-tracker-debug-apk`, and open it on the phone. It installs over the earlier APK and
  keeps your tasks.

## Permissions

The app asks for each permission only when a feature needs it. If you say no, the rest of the
app keeps working. You can see what is on, off or in basic mode at any time under
**⋮ → Permissions & features**, and the **Fix** button there takes you to the right setting.

| Permission | What it is used for | If you don't allow it |
| --- | --- | --- |
| Notifications | Time-of-day reminders, the 8 AM morning digest, "new suggestions" and "sign in again" alerts | No reminders or alerts. You only see tasks when you open the app. |
| Alarms & reminders (Android 12+) | Makes reminders pop up at the exact time | Reminders still come, but Android may delay them by a few minutes. |
| Microphone | Voice capture (long-press +) | No voice capture. Typing, photos and sharing still work. |
| Run at startup | Sets the reminders again after the phone restarts | Granted automatically; nothing to do. |
| Internet | Talks to Gmail, Calendar and Drive; downloads the small on-device AI models once | Granted automatically. With no connection, sync and backup wait until you are online. |

Voice capture also needs a speech recognition service on the phone (Google's is built into
most phones). If the phone has none, the Permissions & features screen says so.

The fingerprint lock (⋮ menu, off by default) needs a fingerprint, face or PIN set up in Android
settings. If none is set up, the app tells you instead of turning the lock on.

## Google account access

Google asks you about each of these separately, and you can untick any of them on Google's
consent screen. The app then switches that source off and keeps using the others.

| Access | Asked for when | What it is used for | If you don't allow it |
| --- | --- | --- | --- |
| Gmail (read only) | You tap Sync the first time | Suggests tasks from emails of the last 2 days (not promotions or social) | No email suggestions. Calendar still works. |
| Google Calendar (read only) | You tap Sync the first time | Suggests tasks from events in the next 7 days | No calendar suggestions. Gmail still works. |
| Google Drive (app folder only) | You turn on ⋮ → Google Drive backup | An encrypted backup in a hidden app folder; the app can't see your other Drive files | No backup. Your tasks live only on this phone. |

You can switch Gmail and Calendar on or off at any time under ⋮ → Permissions & features.
Switching them off stops the app using them. To remove access completely, go to your Google
Account → Security → Third-party apps.

The app never sends email, changes your calendar, or reads your other Drive files.

## AI and basic mode

All AI runs on the phone. Nothing is sent to an AI service.

| Feature | With on-device AI | In basic mode (AI not available) |
| --- | --- | --- |
| Email titles and summaries | Gemini Nano writes a clean one-line title and a three-bullet summary | The title comes from the email subject and built-in rules |
| Voice capture | Gemini Nano tidies the spoken words | The built-in rules pick out the title, date, person and repeat |
| Photo and shared text | Gemini Nano titles and summarizes long text | The most task-like sentence becomes the title |
| God task | Picked from the built-in list (Gemini Nano can't write new ones on current phones: free-form generation isn't offered on the S25 Ultra, and Nano can't run in the morning while the app is closed) | Same |
| Dates in photos and shared text | ML Kit's date model finds dates and times | Simple date rules ("Friday", "Oct 24", "tomorrow") until the small model downloads once |

Basic mode is not an error: every feature still works, the wording is just simpler. Gemini
Nano only runs while the app is open, and only on phones that support it.

## Features

- **Thumb-first layout**: the top bar names the page you're on: today's or tomorrow's date,
  Next, Suggested, or the month on Calendar and Stats (with arrows to change it). Everything you
  tap is in two rows at the bottom: group-by (or "All N" on Suggested), the type bar and the ⋮ menu
  (with Sync now); then the Now | Near | Next | Suggested | Calendar | Stats tabs. Lists read
  top to bottom (Morning, Afternoon, Evening, Night), starting under the top bar.
- **Type a task**: type a sentence in the bottom bar, for example "ask Vibu about the science fair
  Friday evening". Chips show the person, due date, time of day and context as you type (same rules
  as voice capture); tap ✓ or Send to add it. Tap + for the full form.
- **Suggested inbox**: tasks found in Gmail and Calendar wait here as a stack of cards. Swipe right
  to add, left to skip, up to edit first (or use the buttons under the cards), with Undo; "All N"
  left of the type bar takes them all. The same email or event is never suggested twice.
- **Due dates**: a date in the text ("by Friday", "Oct 20") is used; otherwise the task is due tomorrow.
- **Time of day**: each task is Morning, Afternoon, Evening or Night.
- **Now / Near / Next pages**: the app opens on Now (overdue plus due today, and anything finished today). Swipe sideways, or tap the bottom tabs, to move to Near (due tomorrow), Next (everything after tomorrow, including Upcoming repeats), Suggested, Calendar and Stats; each task tab shows its count. Back returns to Now.
- **Group** each page by date, time of day, person or context (Work, Family, Kids/School). Now and
  Near start grouped by time of day, Next by date; each page remembers its own choice.
- **Repeating tasks**: daily, weekdays, weekly, monthly or yearly, with an optional end. Ticking
  one off queues the next.
- **Reminders** (⋮ → Reminders): an 8 AM digest and a pop-up at 12, 4, 7 and 10 PM listing what
  is due. Each reminder is one notification with Snooze 30 min (and Done when it lists one task).
  Notifications also show on a paired watch.
- **God task** (⋮ → God task): every morning at 6 AM a short task (under 15 minutes) lands in
  today's Evening on a gold card with a 🪔 lamp: one on weekdays, three on weekends. Themes are
  Gratitude, Leadership, Networking, Health and Family (Family tasks rotate Nathiya, Vibu and
  Dharshu); pick one or leave it on Random (the default; also used on weekends), or turn it Off.
  Long-press one for **Another God task** (same theme, Undo brings the old one back). One left
  undone moves to the next day and counts toward that day's number.
  Tasks come from a built-in list of 30 per theme and don't repeat within 60 days.
- **Thought of the day**: a small quote card (soft indigo-to-teal wash, faint quote mark) pinned at the bottom of Now, just above the bottom bar,
  from the Stoics, the Bhagavad Gita, the Thirukkural, Rumi or Peter Drucker; a different one on
  every launch.
- **Voice capture**: long-press + and say the task, for example "remind me to sign Vibu's
  permission slip by Friday".
- **Photo to task**: tap +, then the camera icon, or share a photo or screenshot to the app.
  The text is read on the phone; the photo is never stored or uploaded.
- **Share to task**: share text from any app (a school message, a web page) to Action Tracker.
- **Theme** (⋮ → Theme): System default (follows Android's dark mode), Light or Dark. The widget
  follows the same choice.
- **Home-screen widget**: Now (today) and Near (tomorrow) tabs; tick tasks off from the home screen. Task
  titles are visible there even when the app lock is on.
- **Calendar and stats**: the last two tabs show a month view and how many tasks you finished.
- **Long-press to delete** (sideways swipes change pages), with Undo.
- **Fingerprint lock** (optional) and **screenshot blocking**.
- **Encrypted Google Drive backup**, restored on a new phone with your backup passphrase.
- **Crash report**: after a crash, the next launch shows the error so it can be shared.

Works in portrait and landscape on phones, tablets and foldables.

## Privacy

- Tasks are stored in an encrypted database (SQLCipher, AES-256) with a key kept in the
  phone's secure hardware. Android's own cloud backup is turned off for the app.
- The only copy that leaves the phone is the optional Drive backup, encrypted on the phone
  with your passphrase (AES-256-GCM). Google can't read it, and a forgotten passphrase can't be
  recovered.
- Gmail and Calendar are read only to suggest tasks. Email text stays on the phone.
- Voice, photos and AI are processed on the phone.
- Full policy: [PRIVACY.md](PRIVACY.md).

---

## For developers

### Build

There is no Gradle wrapper; CI uses Gradle 8.11.1 and JDK 17. Requires the Android SDK
(compile and target SDK 36, min SDK 30).

```bash
gradle :app:testDebugUnitTest --no-daemon   # unit tests
gradle :app:assembleDebug --no-daemon       # APK for sideloading
gradle :app:bundleRelease --no-daemon       # app bundle (.aab) for Google Play
```

GitHub Actions (*Build APK*) runs all three on every push to `main` and every pull request,
and uploads `action-tracker-debug-apk` and `action-tracker-release-aab`.

### Signing key

Google sign-in on Android is tied to the package name plus the SHA-1 of the signing key, so
builds must use one stable key. Create it once and add four repo secrets:

```bash
keytool -genkeypair -v -keystore release.jks -alias tracker -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks                                  # KEYSTORE_BASE64
keytool -list -v -keystore release.jks -alias tracker   # SHA-1 for the OAuth client
```

Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never commit the
keystore (it is git-ignored). Without them CI signs with a throwaway key and sign-in fails.

### Google Cloud setup

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and enable the
   **Gmail API**, **Google Calendar API** and **Google Drive API**.
2. OAuth consent screen: user type **External**; add the scopes `.../auth/gmail.readonly`,
   `.../auth/calendar.readonly` and `.../auth/drive.appdata`.
3. Credentials → Create OAuth client ID → **Android**, package `com.sonas.actiontracker`, with
   the SHA-1 of the CI key. For Google Play, add a second Android client with the **Play app
   signing** SHA-1 (Play Console → Test and release → App integrity).

While the consent screen is in **Testing**, only the test users you add (up to 100) can sign
in, and they must sign in again every 7 days.

### Publishing on Google Play

1. Upload `action-tracker-release-aab` from a green run on `main` (version code = run number,
   so each upload is higher). Use Play App Signing and add its SHA-1 to an OAuth client (above).
2. Privacy policy URL: link to [PRIVACY.md](PRIVACY.md) on GitHub.
3. Data safety form: email, calendar and task data are processed on the device and not shared;
   the Drive backup is encrypted by the user.
4. **Gmail verification**: `gmail.readonly` is a restricted scope. Before the app can leave
   Testing for everyone, Google must verify it, including a yearly third-party security
   assessment. Until then, publish to a closed testing track for your test users. Calendar and
   Drive app-folder access need only the standard verification.
5. Reminders use the user-granted "Alarms & reminders" permission, which Play allows for any
   app; the restricted `USE_EXACT_ALARM` is not used.

### Code and rules

`CLAUDE.md` describes the code layout, build commands and the rules every change follows
(database migrations, privacy, UI style). `.claude/` holds the matching rules, skills and hooks.

Stack: Kotlin 2, Jetpack Compose (Material 3), Room + SQLCipher, WorkManager + AlarmManager,
Glance widget, ML Kit GenAI (Gemini Nano), ML Kit text recognition and entity extraction,
Google Identity Authorization API with the Gmail, Calendar and Drive REST APIs.
