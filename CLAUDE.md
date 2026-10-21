# Action Tracker

Personal Android action-item tracker for one user (Sona, Samsung Galaxy S25 Ultra).
Kotlin 2 + Jetpack Compose (Material 3), single `:app` module, package `com.sonas.actiontracker`,
minSdk 30 (Android 11), target/compile SDK 36 (Google Play requirement). Feature overview and Google Cloud setup are in `README.md`.

## Build and test

There is no Gradle wrapper in the repo; CI uses Gradle 8.11.1 with JDK 17 (AGP 8.10).

```bash
gradle :app:testDebugUnitTest --no-daemon   # JVM unit tests (app/src/test)
gradle :app:assembleDebug --no-daemon       # debug APK -> app/build/outputs/apk/debug/
gradle :app:bundleRelease --no-daemon       # Play bundle -> app/build/outputs/bundle/release/
```

- Both need the Android SDK. Cloud sessions can't download it (dl.google.com is blocked), so
  there the GitHub Actions run is the build check: don't claim a change compiles until it is green.
- `.github/workflows/build.yml` ("Build APK") runs on every push to `main`, every PR and manual
  dispatch: unit tests, `assembleDebug` and `bundleRelease`, uploading the `action-tracker-debug-apk`
  and `action-tracker-release-aab` artifacts. The bundle's versionCode is the run number
  (`VERSION_CODE`); the debug APK stays at versionCode 1 so it installs over earlier APKs.
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
| `data/Recurrence.kt` | `Recurrence` rules, `Series` (repeating rows kept 3 months ahead; scoped save / delete / undo) and `Actions` (complete / reopen) |
| `sync/Google.kt`, `Extractor.kt`, `Sync.kt` | Gmail + Calendar REST (each source switchable; declined scopes switch it off), rule-based extraction, `SyncWorker` (~2 h) and notifications |
| `sync/Features.kt`, `ui/Permissions.kt` | ⋮ → Permissions & features: On / Basic / Off per permission, Google source and on-device model, with Fix buttons |
| `sync/SmartTitles.kt` | Gemini Nano summarization: email titles and three-bullet summaries |
| `sync/VoiceDraft.kt` | Spoken sentence to draft `Item` (rules + Nano proofreading) |
| `sync/PhotoDraft.kt` | Photo, shared image or shared text to draft `Item` (ML Kit OCR + entity extraction, date rules, Nano summary) |
| `sync/GodTasks.kt`, `data/GodTaskLibrary.kt` | Daily God task (source `GOD`, Evening, 1 weekday / 3 weekend, carry-over, 60-day no-repeat), 30 tasks per theme |
| `data/Thoughts.kt` | Thought of the day lines (bottom of Now, new one per launch) |
| `sync/Reminders.kt` | 8 AM daily digest; `ReminderWorker` runs every 15 min |
| `sync/SlotReminders.kt` | Time-of-day pop-ups (12/4/7/10 PM) via AlarmManager |
| `sync/TaskNotifications.kt` | One notification per reminder listing its tasks (Snooze 30 min; Done only when it lists one task); no per-task notifications |
| `sync/Backup.kt` | Encrypted Drive appDataFolder backup (PBKDF2 + AES-256-GCM), JSON export/import |
| `ui/Screens.kt`, `MainVM.kt` | Main screen (page-header top bar, two-row bottom dock: group-by + type bar + ⋮, then tabs; Now / Near / Next (today / tomorrow / later) / Suggested / Calendar / Stats pages in a swipeable pager, top-aligned lists, Suggested swipe deck), edit sheet, long-press delete |
| `ui/Insights.kt` | Calendar and Stats pages (`InsightsPage`) |
| `ui/Voice.kt`, `Photo.kt`, `BackupUi.kt`, `Theme.kt` | Listen sheet, photo button + draft note, backup dialog, light "Fresh" palette + `glass()` |
| `widget/DueWidget.kt` | Glance Today/Tomorrow widget |

## Hard rules

- **Database**: schema is at `DB_VERSION` in `data/Data.kt` (currently 6). Any change to an
  entity needs a version bump, a `Migration` registered in `addMigrations`, and matching
  `Backup` export/import fields. An older build over a newer database crashes at launch, so
  never hand out an APK built from a branch with a lower `DB_VERSION` than `main`. Details in
  `.claude/rules/database-migrations.md`; procedure in the `add-room-migration` skill.
- **Mark done** goes through `Actions.complete` / `Actions.reopen` (deletes through `Series.delete`)
  in `data/Recurrence.kt`, never `dao.markDone` directly, so repeating actions queue their next
  occurrence the same way from the list, widget and reminder notifications.
- **Gemini Nano** (ML Kit GenAI) runs only while the app is in the foreground; never call it
  from WorkManager or a receiver. The general Prompt API does not support the S25 Ultra, so don't
  build features on it; summarization and proofreading are available.
- **Privacy**: everything stays on the phone or in encrypted backups. See
  `.claude/rules/privacy-and-secrets.md`.
- **Permissions**: the app must work on any Android 11+ device with any permission denied or
  Nano missing. A new permission, scope or model needs a row in `Features` and in the README
  permissions tables saying what stops working without it. No `USE_EXACT_ALARM` (Play-restricted).
- **UI**: follow `.claude/rules/ui-style.md` (thumb-first bottom dock, slim top bar, icon-only +, long-press delete with Undo).

## Workflow

- Branch new work from the latest `main` and target `main`. One PR per change; don't stack PRs
  on each other (stacked branches drifted apart in DB version and caused a launch crash).
- Open a draft PR, let "Build APK" go green, then share the APK artifact link and say whether
  that APK is safe to install over the current app (see the `ship-apk` skill).
- Keep `README.md` in step with user-visible features.
- `.claude/hooks/check-db-migration.sh` runs after edits to `data/Data.kt` and flags a
  `DB_VERSION` without a registered migration.
