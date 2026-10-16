package com.sonas.actiontracker.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
    /** GMAIL, CALENDAR or CUSTOM. */
    val source: String,
    val createdAt: Long = System.currentTimeMillis()
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

    @Query("UPDATE items SET status = 'DONE' WHERE id = :id")
    suspend fun markDone(id: Long)

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

@Database(entities = [Item::class, Processed::class], version = 1, exportSchema = false)
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
                .build()
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
