# Action Tracker

A personal, fingerprint-locked action-item tracker for Android (built for a Samsung S25 Ultra).

It scans your Gmail inbox and Google Calendar for things that need doing, **proposes** them in a
*Suggested* inbox, and only adds them to your list after you confirm. Everything is stored
encrypted on the phone.

## v1 features

- **Fingerprint lock** (BiometricPrompt; device PIN as fallback). Re-locks after 30 s in the background; screenshots and the recents preview are blocked.
- **Encrypted on-device storage**: Room + SQLCipher (AES-256). The passphrase is wrapped by a hardware-backed Android Keystore key. Backups are disabled.
- **Sources**: Gmail (inbox, excluding promotions/social, last 2 days), Google Calendar (next 7 days), and custom items (+ button).
- **Confirm before adding**: Gmail/Calendar items land in *Suggested*; tap Add, Edit or Dismiss.
- **Default due date is tomorrow** when the source has no date. A date in the text (e.g. "by Friday", "Oct 20", "2026-10-20", "tomorrow") overrides it. Editable any time.
- **Group by** date, person (Me, Nathiya, Vibu, Dharshu, or anyone), or context (Work, Family, Kids/School).
- **No duplicates**: every Gmail message ID / calendar event ID is recorded the first time it is seen and never evaluated again, even if you dismiss the suggestion.
- **Background sync** every ~2 hours with a notification when new suggestions arrive.

Planned for v2: home-screen widget, due-date reminders, Google Drive encrypted backup, on-device Gemini Nano for ambiguous emails (v1 uses rules only).

## One-time Google setup (required for Gmail + Calendar)

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and enable the **Gmail API** and **Google Calendar API**.
2. OAuth consent screen: user type **External**, leave publishing status as **Testing**, add scopes
   `.../auth/gmail.readonly` and `.../auth/calendar.readonly`, and add **your own Gmail address as a test user**.
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
