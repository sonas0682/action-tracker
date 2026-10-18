# Action Tracker

Personal Android action-item tracker for one user (Sona, Samsung Galaxy S25 Ultra).
Kotlin 2 + Jetpack Compose (Material 3), single `:app` module, package `com.sonas.actiontracker`,
minSdk 30, target/compile SDK 35. Feature overview and Google Cloud setup are in `README.md`.

## Build and test

There is no Gradle wrapper in the repo; CI uses Gradle 8.9 with JDK 17.

```bash
gradle :app:testDebugUnitTest --no-daemon   # JVM unit tests (app/src/test)
gradle :app:assembleDebug --no-daemon       # debug APK -> app/build/outputs/apk/debug/
```

- Both need the Android SDK. Cloud sessions can't download it (dl.google.com is blocked), so
  there the GitHub Actions run is the build check: don't claim a change compiles until it is green.
- `.github/workflows/build.yml` ("Build APK") runs on every push to `main`, every PR and manual
  dispatch: unit tests, then `assembleDebug`, uploading the `action-tracker-debug-apk` artifact.
- The debug APK is signed with the stable key from repo secrets (`KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Its SHA-1 is registered on the Google OAuth
  Android client, so Gmail/Calendar/Drive sign-in only works with CI-signed builds.
- Unit tests are plain JUnit 4 on pure Kotlin (see `RecurrenceTest.kt`). Keep date/rule logic in
  plain functions so it stays testable without a device.

## Code map (`app/src/main/java/com/sonas/actiontracker/`)

| Path | What lives there |
| --- | --- |
| `App.kt` | Application; saves the last crash trace to `files/last_crash.txt` |
| `MainActivity.kt` | Fingerprint lock (pref `lock_enabled`, off by default), Google auth hand-off, crash-trace screen (`showLastCrash`) |
| `data/Data.kt` | Room `Db` (SQLCipher), `Item`/`Processed` entities, `ItemDao`, `DB_VERSION`, all migrations, `DbKey` (Keystore-wrapped passphrase), `TimeOfDay` |
| `data/Recurrence.kt` | `Recurrence` rules and `Actions` (complete / reopen / deleteOccurrence) |
| `sync/Google.kt`, `Extractor.kt`, `Sync.kt` | Gmail + Calendar REST, rule-based extraction, `SyncWorker` (~2 h) and notifications |
| `sync/SmartTitles.kt` | Gemini Nano summarization: email titles and three-bullet summaries |
| `sync/VoiceDraft.kt` | Spoken sentence to draft `Item` (rules + Nano proofreading) |
| `sync/PhotoDraft.kt` | Photo / shared image to draft `Item` (ML Kit OCR + entity extraction, date rules, Nano summary) |
| `sync/Reminders.kt` | 8 AM daily digest; `ReminderWorker` runs every 15 min |
| `sync/SlotReminders.kt` | Time-of-day pop-ups (12/4/7/10 PM) via AlarmManager |
| `sync/TaskNotifications.kt` | Grouped per-task reminder notifications (Done / Snooze 30 min) and the "Add a task" reply-box notification, watch-friendly |
| `sync/Backup.kt` | Encrypted Drive appDataFolder backup (PBKDF2 + AES-256-GCM), JSON export/import |
| `ui/Screens.kt`, `MainVM.kt` | Main screen, top bar, lists, edit sheet, swipe-to-delete |
| `ui/Insights.kt` | Calendar + stats view |
| `ui/Voice.kt`, `Photo.kt`, `BackupUi.kt`, `Theme.kt` | Listen sheet, photo button + draft note, backup dialog, light "Fresh" palette + `glass()` |
| `widget/DueWidget.kt` | Glance Today/Tomorrow widget |

## Hard rules

- **Database**: schema is at `DB_VERSION` in `data/Data.kt` (currently 5). Any change to an
  entity needs a version bump, a `Migration` registered in `addMigrations`, and matching
  `Backup` export/import fields. An older build over a newer database crashes at launch, so
  never hand out an APK built from a branch with a lower `DB_VERSION` than `main`. Details in
  `.claude/rules/database-migrations.md`; procedure in the `add-room-migration` skill.
- **Mark done** goes through `Actions.complete` / `Actions.reopen` / `Actions.deleteOccurrence`
  in `data/Recurrence.kt`, never `dao.markDone` directly, so repeating actions queue their next
  occurrence the same way from the list, widget and reminder notifications.
- **Gemini Nano** (ML Kit GenAI) runs only while the app is in the foreground; never call it
  from WorkManager or a receiver. The general Prompt API does not support the S25 Ultra, so don't
  build features on it; summarization and proofreading are available.
- **Privacy**: everything stays on the phone or in encrypted backups. See
  `.claude/rules/privacy-and-secrets.md`.
- **UI**: follow `.claude/rules/ui-style.md` (slim top bar, icon-only +, swipe delete with Undo).

## Workflow

- Branch new work from the latest `main` and target `main`. One PR per change; don't stack PRs
  on each other (stacked branches drifted apart in DB version and caused a launch crash).
- Open a draft PR, let "Build APK" go green, then share the APK artifact link and say whether
  that APK is safe to install over the current app (see the `ship-apk` skill).
- Keep `README.md` in step with user-visible features.
- `.claude/hooks/check-db-migration.sh` runs after edits to `data/Data.kt` and flags a
  `DB_VERSION` without a registered migration.
