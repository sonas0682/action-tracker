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
- **Slim top bar**: Actions (opens first) and Suggested tabs, a one-line date/late/today strip, and a small group-by dropdown, so the list gets almost all of the screen in portrait and landscape.
- **Group by** date, person (Me, Nathiya, Vibu, Dharshu, or anyone), or context (Work, Family, Kids/School).
- **No duplicates**: every Gmail message ID / calendar event ID is recorded the first time it is seen and never evaluated again, even if you dismiss the suggestion.
- **Background sync** every ~2 hours with a notification when new suggestions arrive.
- **Daily due-date reminder** (default 8:00 AM, change it or turn it off from the ⋮ menu): one notification listing open actions due today or overdue. The lock screen shows only the count.

- **Home-screen widget** ("Due today"): open actions due today or overdue. Tick one to mark it done, tap anything else to open the app. Long-press the home screen → Widgets → Action Tracker to add it.

- **Smart titles (Gemini Nano, on-device)**: when you open the app, Gmail suggestions are rewritten into a clean one-line title by Gemini Nano's summarizer. The rules still decide what is an action; nothing leaves the phone. The notes get a short three-bullet summary of the email, plus the original subject; tap a suggestion to read them. Gmail suggestions and open actions from before this update get a summary once, on the next sync with the app open (the email is fetched again from Gmail; titles are left as they are). Needs a Nano-capable phone (the S25 Ultra is supported) and emails of at least ~400 characters; otherwise the rule-based title stays. Google's general Prompt API, which could extract actions directly, doesn't support the S25 Ultra yet.
- **Encrypted Google Drive backup** (⋮ → Google Drive backup): a daily backup to the app's hidden Drive folder, encrypted on the phone with your backup passphrase (AES-256-GCM, PBKDF2 key). Restore on a new phone with the same passphrase. Google can't read it, and a forgotten passphrase can't be recovered.

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

Kotlin 2 · Jetpack Compose (Material 3) · Room + SQLCipher · AndroidX Biometric · WorkManager · Google Identity Authorization API + Gmail/Calendar REST.

## Look and feel

Light "Fresh" theme: airy off-white canvas with slowly drifting pastel glows,
an indigo → sky → teal accent gradient, white glass cards, big expressive headings, a
summary header (late / today / open / inbox), a sliding gradient tab switcher and a glowing New button.
