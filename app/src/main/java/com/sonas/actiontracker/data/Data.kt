package com.sonas.actiontracker.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val STATUS_SUGGESTED = "SUGGESTED"
const val STATUS_OPEN = "OPEN"
const val STATUS_DONE = "DONE"
/** One occurrence of a repeating action deleted on its own; kept hidden so the series doesn't re-add that date. */
const val STATUS_SKIPPED = "SKIPPED"

/** When in the day an action is meant to happen; stored by name in [Item.timeOfDay]. */
enum class TimeOfDay(val label: String, val emoji: String) {
    MORNING("Morning", "🌅"), AFTERNOON("Afternoon", "☀️"), EVENING("Evening", "🌇"), NIGHT("Night", "🌙");

    companion object {
        private val workContext = Regex("""(?i)\b(work|office)\b""")

        /** Work contexts ("Work", "Office", "Work/Client") default to Afternoon, everything else to Evening. */
        fun defaultFor(context: String): TimeOfDay = if (workContext.containsMatchIn(context)) AFTERNOON else EVENING

        fun of(name: String): TimeOfDay = entries.firstOrNull { it.name == name } ?: EVENING
    }
}

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val person: String = "Me",
    val context: String = "Work",
    /** Due date as epoch day (LocalDate.toEpochDay). */
    val dueDate: Long,
    val status: String,
    /** GMAIL, CALENDAR, CUSTOM, VOICE, PHOTO, SHARE, GOD (a daily God task) or THOUGHT (the Thought of the day). */
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Full email text waiting for an on-device Gemini Nano title pass. Cleared once
     * the pass has run, so each suggestion is summarized at most once.
     */
    val body: String = "",
    /** When the action was ticked done (epoch millis); null while open, and for items done before v3. */
    val completedAt: Long? = null,
    /** A [TimeOfDay] name; new actions take the default for their context until changed. */
    @ColumnInfo(defaultValue = "EVENING")
    val timeOfDay: String = TimeOfDay.defaultFor(context).name,
    /** A [Recurrence] name; NONE for one-off actions. */
    @ColumnInfo(defaultValue = "NONE")
    val recurrence: String = Recurrence.NONE.name,
    /** Series this occurrence belongs to (the id of its first row); null for one-off actions. */
    val seriesId: Long? = null,
    /** First due date of the series (epoch day). Dates are counted from it, so "monthly on the 31st" never drifts. */
    val seriesStart: Long? = null,
    /** Last day an occurrence may fall on (epoch day): "Ends on" directly, or worked out from "Ends after N times". */
    val endDate: Long? = null,
    /** "Ends after N times" (the first occurrence counts); null for "Never" or "On a date". */
    val endCount: Int? = null,
    /** Gmail message id of a GMAIL suggestion, so tapping it opens the email; null for other sources and mail synced before v7. */
    val sourceRef: String? = null
) {
    companion object {
        fun blank() = Item(
            title = "",
            dueDate = LocalDate.now().plusDays(1).toEpochDay(),
            status = STATUS_OPEN,
            source = "CUSTOM"
        )
    }
}

/** Every Gmail message / calendar event ever evaluated, so it is never processed twice. */
@Entity(tableName = "processed")
data class Processed(
    @PrimaryKey val sourceKey: String,
    val at: Long
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE status = 'SUGGESTED' ORDER BY createdAt DESC")
    fun suggested(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE status IN ('OPEN', 'DONE') ORDER BY dueDate, createdAt")
    fun actions(): Flow<List<Item>>

    /** Open actions due on or before [epochDay] (today plus anything overdue), oldest first. */
    @Query("SELECT * FROM items WHERE status = 'OPEN' AND dueDate <= :epochDay ORDER BY dueDate, createdAt")
    suspend fun openDueBy(epochDay: Long): List<Item>

    @Query("SELECT * FROM items WHERE status = 'OPEN' AND dueDate <= :epochDay ORDER BY dueDate, createdAt")
    fun openDueByFlow(epochDay: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE status = 'SUGGESTED' AND body != ''")
    suspend fun awaitingSmartTitle(): List<Item>

    /** Gmail suggestions and open actions that predate the email summary. */
    @Query("SELECT * FROM items WHERE source = 'GMAIL' AND status IN ('SUGGESTED', 'OPEN') AND body = '' AND notes NOT LIKE 'Summary:%'")
    suspend fun awaitingSummary(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): Item?

    /** Every row of a repeating series, skipped ones included, earliest first. */
    @Query("SELECT * FROM items WHERE seriesId = :seriesId ORDER BY dueDate, id")
    suspend fun series(seriesId: Long): List<Item>

    /** Unfinished God tasks from before [epochDay]; they carry over instead of going overdue. */
    @Query("SELECT * FROM items WHERE source = 'GOD' AND status = 'OPEN' AND dueDate < :epochDay")
    suspend fun openGodBefore(epochDay: Long): List<Item>

    /** God tasks on [epochDay], open or done; they count toward the day's number. */
    @Query("SELECT COUNT(*) FROM items WHERE source = 'GOD' AND dueDate = :epochDay AND status IN ('OPEN', 'DONE')")
    suspend fun godCountOn(epochDay: Long): Int

    /** Unread Thoughts of the day from before [epochDay]; today's replaces them. */
    @Query("DELETE FROM items WHERE source = 'THOUGHT' AND status = 'OPEN' AND dueDate < :epochDay")
    suspend fun deleteOpenThoughtsBefore(epochDay: Long): Int

    /** Every unread Thought of the day (when the feature is switched off). */
    @Query("DELETE FROM items WHERE source = 'THOUGHT' AND status = 'OPEN'")
    suspend fun deleteOpenThoughts(): Int

    /** Thought rows on [epochDay] in any state; a deleted one leaves none, but the day is already recorded. */
    @Query("SELECT COUNT(*) FROM items WHERE source = 'THOUGHT' AND dueDate = :epochDay")
    suspend fun thoughtCountOn(epochDay: Long): Int

    @Query("SELECT DISTINCT seriesId FROM items WHERE seriesId IS NOT NULL")
    suspend fun seriesIds(): List<Long>

    /**
     * Turns open repeating actions from before series existed (v5 and v5 backups) into series of
     * their own, and makes their done rows plain history.
     */
    @Query("UPDATE items SET seriesId = id, seriesStart = dueDate WHERE recurrence != 'NONE' AND status = 'OPEN' AND seriesId IS NULL")
    suspend fun adoptLegacySeries()

    @Query("UPDATE items SET recurrence = 'NONE' WHERE recurrence != 'NONE' AND seriesId IS NULL AND status = 'DONE'")
    suspend fun clearLooseRecurrence()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<Item>)

    @Query("DELETE FROM items WHERE seriesId IN (:seriesIds)")
    suspend fun deleteSeries(seriesIds: Collection<Long>)

    @Query("SELECT * FROM items")
    suspend fun allItems(): List<Item>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Query("SELECT * FROM processed")
    suspend fun allProcessed(): List<Processed>

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM processed")
    suspend fun deleteAllProcessed()

    @Query("UPDATE items SET status = 'DONE', completedAt = :at WHERE id = :id")
    suspend fun markDone(id: Long, at: Long = System.currentTimeMillis())

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT COUNT(*) FROM processed WHERE sourceKey = :key")
    suspend fun seen(key: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markProcessed(p: Processed)
}

/** Room schema version; also written into Drive backups so restores know where they came from. */
const val DB_VERSION = 7

@Database(entities = [Item::class, Processed::class], version = DB_VERSION, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): ItemDao

    companion object {
        @Volatile private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): Db {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(DbKey.passphrase(context))
            return Room.databaseBuilder(context, Db::class.java, "tracker.db")
                .openHelperFactory(factory)
                .addMigrations(*MIGRATIONS)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN body TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds the completion time. Existing done items keep null and are counted by due date. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN completedAt INTEGER")
            }
        }

        /** Every upgrade step, oldest first; also used by the upgrade tests. */
        internal val MIGRATIONS: Array<Migration> by lazy {
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        }

        /** Adds the time of day, backfilled with the same work-context rule new actions use. */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN timeOfDay TEXT NOT NULL DEFAULT 'EVENING'")
                val afternoon = mutableListOf<Long>()
                db.query("SELECT id, context FROM items").use { c ->
                    while (c.moveToNext()) {
                        if (TimeOfDay.defaultFor(c.getString(1) ?: "") == TimeOfDay.AFTERNOON) afternoon += c.getLong(0)
                    }
                }
                afternoon.forEach {
                    db.execSQL("UPDATE items SET timeOfDay = 'AFTERNOON' WHERE id = ?", arrayOf<Any>(it))
                }
            }
        }

        /**
         * Series and end rules. Each open repeating action becomes its own series (its next
         * occurrences are filled in at the next app start); done rows of the old
         * one-at-a-time repeats stay as plain history.
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN seriesId INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN seriesStart INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN endDate INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN endCount INTEGER")
                db.execSQL("UPDATE items SET seriesId = id, seriesStart = dueDate WHERE recurrence != 'NONE' AND status = 'OPEN'")
                db.execSQL("UPDATE items SET recurrence = 'NONE' WHERE recurrence != 'NONE' AND seriesId IS NULL AND status = 'DONE'")
            }
        }

        /** Adds the Gmail message id; existing suggestions keep null and open the Gmail app's inbox instead. */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN sourceRef TEXT")
            }
        }

        /** Adds the repeat rule; every existing action stays one-off. */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
            }
        }
    }
}

/**
 * The SQLCipher passphrase is a random 32-byte value, encrypted with a
 * hardware-backed Android Keystore AES key and stored only in its wrapped form.
 */
object DbKey {
    private const val ALIAS = "action_tracker_db_key"
    private const val PREFS = "secure_prefs"

    fun passphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = keystoreKey()
        val stored = prefs.getString("k", null)
        val iv = prefs.getString("iv", null)
        if (stored != null && iv != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            return cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP))
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(raw)
        prefs.edit()
            .putString("k", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return raw
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            gen.generateKey()
        }
        return ks.getKey(ALIAS, null) as SecretKey
    }
}
