---
name: add-room-migration
description: Add or change a field on the Room database (Item / Processed) in Action Tracker with a safe version bump, migration and backup support. Use for any schema change in data/Data.kt.
---

# Add a Room migration

Follow this whenever an entity in `app/src/main/java/com/sonas/actiontracker/data/Data.kt`
changes. Skipping a step either crashes the app at launch or silently drops the field from
Drive backups.

1. **Start from the latest `main`.** `git fetch origin main` and branch from `origin/main`. Read
   the current `DB_VERSION` there; if another open branch also bumps it, get that merged first and
   rebase onto it so two branches never claim the same version.
2. **Change the entity.** Give the new field a Kotlin default, and for a non-null column add
   `@ColumnInfo(defaultValue = "...")` with the same value (see `timeOfDay`, `recurrence`).
3. **Bump `DB_VERSION`** by exactly one.
4. **Write the migration** in `Db.Companion`, named `MIGRATION_<old>_<new>`, with a KDoc line
   saying what it adds. Typical body:
   ```kotlin
   internal val MIGRATION_5_6 = object : Migration(5, 6) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE items ADD COLUMN example TEXT NOT NULL DEFAULT ''")
       }
   }
   ```
   The SQL type, nullability and default must match what Room generates for the field, or Room
   fails validation at launch. Backfill with a query loop if existing rows need a computed value
   (see `MIGRATION_3_4`).
5. **Register it** by appending to `.addMigrations(...)` in `Db.build`. The PostToolUse hook
   `.claude/hooks/check-db-migration.sh` flags a version without a registered migration.
6. **Backups**: in `sync/Backup.kt`, add the field to `export` and read it in `itemFrom` with
   `opt*` and a fallback, so older backups (without the field) and newer ones both restore.
7. **Callers**: update anything that copies items field by field (e.g. `Actions.next` in
   `data/Recurrence.kt` resets per-occurrence fields) and the UI that edits the field.
8. **Tests**: put any pure logic (defaults, parsing) in a plain function and cover it in
   `app/src/test`. Migrations themselves run only on a device.
9. **Verify** through the "Build APK" workflow on the PR (no Android SDK in cloud sessions). In
   the PR, state the new DB version and that APKs from older branches must not be installed over it.
