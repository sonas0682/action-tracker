---
paths:
  - "app/src/main/java/com/sonas/actiontracker/data/**"
  - "app/src/main/java/com/sonas/actiontracker/sync/Backup.kt"
---

# Room schema and migrations

- The database is Room over SQLCipher (`tracker.db`), version `DB_VERSION` in `data/Data.kt`.
  History: v2 `body`, v3 `completedAt`, v4 `timeOfDay`, v5 `recurrence`, v6 `seriesId` / `seriesStart` / `endDate` / `endCount`, v7 `sourceRef` (Gmail message id).
- `exportSchema = false`, so there are no schema JSON files; the migrations in `Db` are the
  only record of the schema history. Don't delete or rewrite an existing migration.
- Any change to `Item` or `Processed` (new column, rename, type, new table) needs all of:
  1. `DB_VERSION` bumped by one.
  2. A `MIGRATION_<old>_<new>` object in `Db.Companion`, added to `addMigrations(...)`.
  3. New columns `NOT NULL` with a SQL default that matches the Kotlin default
     (`@ColumnInfo(defaultValue = ...)` on the field), so existing rows stay valid.
  4. `Backup.export` writing the field and `Backup.itemFrom` reading it leniently with a
     fallback, so backups from older and newer app versions still restore.
- Never use `fallbackToDestructiveMigration`: the database holds Sona's real tasks.
- A build with a lower `DB_VERSION` than the database on the phone crashes at launch
  ("migration from N to N-1"). Base schema work on the latest `main`. If two branches both need
  a schema change, merge one first and rebase the other onto it so the versions don't collide.
- The DB passphrase is random, wrapped by an Android Keystore key (`DbKey`), and not tied to
  biometric auth; don't change that, or turning the fingerprint lock on/off would lock the data.
