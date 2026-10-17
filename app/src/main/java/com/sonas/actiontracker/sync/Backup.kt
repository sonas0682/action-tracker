package com.sonas.actiontracker.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.sonas.actiontracker.data.DB_VERSION
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Processed
import com.sonas.actiontracker.data.Recurrence
import com.sonas.actiontracker.data.Series
import com.sonas.actiontracker.data.STATUS_DONE
import com.sonas.actiontracker.data.STATUS_OPEN
import com.sonas.actiontracker.data.STATUS_SKIPPED
import com.sonas.actiontracker.data.STATUS_SUGGESTED
import com.sonas.actiontracker.data.TimeOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup to the app's hidden Google Drive folder (appDataFolder: only
 * this app can see it; it doesn't show in Drive's file list).
 *
 * The file is AES-256-GCM encrypted with a key derived from a backup passphrase
 * (PBKDF2-SHA256), so Google never sees the contents and a new phone can restore
 * it with the passphrase alone. On this phone the derived key is kept wrapped by
 * the Android Keystore so backups can run in the background without asking.
 *
 * Backups run a couple of minutes after any change (debounced) and once a day.
 *
 * File layout: "ATB1" | salt (16) | iv (12) | ciphertext of the JSON export.
 * The JSON carries the database schema version it was written from; import reads
 * every field leniently, so a backup from an older or newer app version restores
 * whatever fields this version knows and never fails on a version difference.
 */
object Backup {
    private const val FILE_NAME = "action-tracker-backup.bin"
    private const val MAGIC = "ATB1"
    private const val ITERATIONS = 210_000
    private const val PREFS = "backup"
    private const val WRAP_ALIAS = "action_tracker_backup_wrap"
    private const val WORK = "drive_backup"
    private const val WORK_SOON = "drive_backup_soon"
    /** Quiet period after the last change before the automatic backup runs. */
    private const val DEBOUNCE_MINUTES = 2L
    /** Version of the JSON export format (independent of the database schema). */
    private const val FORMAT = 2
    private const val DRIVE = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    private val scope = Scope("https://www.googleapis.com/auth/drive.appdata")

    data class Status(val enabled: Boolean, val lastBackupAt: Long?)

    fun status(context: Context): Status {
        val p = prefs(context)
        return Status(p.contains("key"), p.getLong("last", 0L).takeIf { it > 0 })
    }

    /** Separate from the Gmail/Calendar grant, so declining Drive never breaks sync. */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context)
            .authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(scope)).build())
            .await()

    /** The backup already in Google Drive, if any. */
    data class Remote(val modifiedAt: Long?)

    /** Turns on backups with [passphrase] and starts the daily job. */
    fun enable(context: Context, passphrase: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        storeKey(context, salt, derive(passphrase, salt))
        schedule(context)
    }

    fun disable(context: Context) {
        prefs(context).edit().remove("key").remove("key_iv").remove("salt").apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_SOON)
    }

    /**
     * Backs up a couple of minutes after the latest change. Each call restarts the
     * wait, so a burst of edits makes one upload. Does nothing while backup is off.
     */
    fun backUpSoon(context: Context) {
        if (!status(context).enabled) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_SOON, ExistingWorkPolicy.REPLACE, request)
    }

    /** Looks for an existing backup in this Google account's Drive. */
    suspend fun findRemote(token: String): Remote? = withContext(Dispatchers.IO) {
        findFileMeta(token)?.let { Remote(it.second) }
    }

    /** Daily backup whenever there is a network connection. Safe to call on every launch. */
    fun schedule(context: Context) {
        if (!status(context).enabled) return
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Encrypts every action and processed-source record and uploads it, replacing the previous backup. */
    suspend fun backUp(context: Context, token: String) = withContext(Dispatchers.IO) {
        val (salt, key) = loadKey(context) ?: throw IllegalStateException("Backup isn't set up")
        val blob = encrypt(export(context).toString().toByteArray(Charsets.UTF_8), salt, key)
        val existing = findFileMeta(token)?.first
        if (existing == null) createFile(token, blob) else updateFile(token, existing, blob)
        prefs(context).edit().putLong("last", System.currentTimeMillis()).apply()
    }

    /**
     * Downloads the backup, decrypts it with [passphrase] and replaces everything on
     * this phone with it. Also turns on backups with the same passphrase.
     * Returns the number of actions restored. Nothing on the phone changes unless
     * the whole backup reads cleanly.
     */
    suspend fun restore(context: Context, token: String, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        val id = findFileMeta(token)?.first ?: throw IOException("No backup found in your Google Drive")
        val blob = download(token, id)
        if (blob.size < 33 || String(blob, 0, 4, Charsets.US_ASCII) != MAGIC) throw IOException("Not an Action Tracker backup")
        val salt = blob.copyOfRange(4, 20)
        val key = derive(passphrase, salt)
        val json = try {
            decrypt(blob, key)
        } catch (e: Exception) {
            throw IOException("Wrong passphrase")
        }
        val root = try {
            JSONObject(String(json, Charsets.UTF_8))
        } catch (e: JSONException) {
            throw IOException("The backup file is damaged")
        }
        val count = import(context, root)
        storeKey(context, salt, key)
        schedule(context)
        count
    }

    // ------------------------------------------------------------ export / import

    private suspend fun export(context: Context): JSONObject {
        val dao = Db.get(context).dao()
        val items = JSONArray()
        dao.allItems().forEach { i ->
            items.put(
                JSONObject()
                    .put("id", i.id).put("title", i.title).put("notes", i.notes).put("person", i.person)
                    .put("context", i.context).put("dueDate", i.dueDate).put("status", i.status)
                    .put("source", i.source).put("createdAt", i.createdAt).put("body", i.body)
                    .put("timeOfDay", i.timeOfDay).put("recurrence", i.recurrence)
                    .apply {
                        i.completedAt?.let { put("completedAt", it) }
                        i.seriesId?.let { put("seriesId", it) }
                        i.seriesStart?.let { put("seriesStart", it) }
                        i.endDate?.let { put("endDate", it) }
                        i.endCount?.let { put("endCount", it) }
                    }
            )
        }
        val processed = JSONArray()
        dao.allProcessed().forEach { processed.put(JSONObject().put("key", it.sourceKey).put("at", it.at)) }
        return JSONObject()
            .put("version", FORMAT).put("schema", DB_VERSION).put("exportedAt", System.currentTimeMillis())
            .put("items", items).put("processed", processed)
    }

    /**
     * Replaces all local data with [root]. Every field is optional except the title:
     * fields added after the backup was made take their defaults, fields from a newer
     * app version are ignored, and a row that can't be read is skipped, not fatal.
     */
    private suspend fun import(context: Context, root: JSONObject): Int {
        // Ids are kept so series links survive; a repeated or missing id just gets a new one.
        val items = (root.optJSONArray("items") ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::itemFrom) }
        }.let { list ->
            val seen = HashSet<Long>()
            list.map { if (it.id == 0L || seen.add(it.id)) it else it.copy(id = 0) }
        }
        val processed = (root.optJSONArray("processed") ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { n ->
                val o = arr.optJSONObject(n) ?: return@mapNotNull null
                val key = o.optString("key").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Processed(key, o.optLong("at", System.currentTimeMillis()))
            }
        }
        val db = Db.get(context)
        val dao = db.dao()
        db.withTransaction {
            dao.deleteAllItems()
            dao.deleteAllProcessed()
            // Rows with their own id first, so a new id handed out never takes one of theirs.
            items.sortedBy { it.id == 0L }.forEach { dao.insert(it) }
            processed.forEach { dao.markProcessed(it) }
            // Backups from before series (v5) hold one open row per repeating action; fill them ahead.
            Series.topUpAll(db)
        }
        return items.size
    }

    private fun itemFrom(o: JSONObject): Item? {
        val title = o.optString("title").takeIf { it.isNotBlank() } ?: return null
        val ctx = o.optString("context", "Work")
        val status = o.optString("status").takeIf { it in setOf(STATUS_SUGGESTED, STATUS_OPEN, STATUS_DONE, STATUS_SKIPPED) } ?: STATUS_OPEN
        val time = o.optString("timeOfDay").takeIf { name -> TimeOfDay.entries.any { it.name == name } }
        return Item(
            title = title, notes = o.optString("notes"),
            person = o.optString("person", "Me"), context = ctx,
            dueDate = if (o.has("dueDate")) o.optLong("dueDate") else java.time.LocalDate.now().toEpochDay(),
            status = status,
            source = o.optString("source", "CUSTOM"), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            body = o.optString("body"),
            completedAt = if (o.has("completedAt") && !o.isNull("completedAt")) o.optLong("completedAt") else null,
            timeOfDay = time ?: TimeOfDay.defaultFor(ctx).name,
            recurrence = Recurrence.of(o.optString("recurrence")).name,
            id = o.optLong("id", 0L).coerceAtLeast(0L),
            seriesId = o.optLongOrNull("seriesId"),
            seriesStart = o.optLongOrNull("seriesStart"),
            endDate = o.optLongOrNull("endDate"),
            endCount = o.optLongOrNull("endCount")?.toInt()
        )
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null

    // ------------------------------------------------------------ crypto

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encrypt(plain: ByteArray, salt: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(blob: ByteArray, key: ByteArray): ByteArray {
        val iv = blob.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(blob, 32, blob.size - 32)
    }

    /** Keeps the derived key on this phone, wrapped by a hardware-backed Keystore key. */
    private fun storeKey(context: Context, salt: ByteArray, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        prefs(context).edit()
            .putString("salt", b64(salt))
            .putString("key", b64(cipher.doFinal(key)))
            .putString("key_iv", b64(cipher.iv))
            .apply()
    }

    private fun loadKey(context: Context): Pair<ByteArray, ByteArray>? {
        val p = prefs(context)
        val salt = p.getString("salt", null) ?: return null
        val key = p.getString("key", null) ?: return null
        val iv = p.getString("key_iv", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, unb64(iv)))
        return unb64(salt) to cipher.doFinal(unb64(key))
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(WRAP_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
        }
        return ks.getKey(WRAP_ALIAS, null) as SecretKey
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    // ------------------------------------------------------------ Drive REST

    /** The backup file's id and last-modified time (epoch millis), or null if there is none. */
    private fun findFileMeta(token: String): Pair<String, Long?>? {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val res = JSONObject(String(request("GET", "$DRIVE?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)", token)))
        val files = res.optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        val f = files.getJSONObject(0)
        val modified = runCatching { Instant.parse(f.getString("modifiedTime")).toEpochMilli() }.getOrNull()
        return f.getString("id") to modified
    }

    private fun createFile(token: String, blob: ByteArray) {
        val boundary = "atb" + System.nanoTime()
        val meta = JSONObject().put("name", FILE_NAME).put("parents", JSONArray().put("appDataFolder"))
        val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray() +
            blob + "\r\n--$boundary--\r\n".toByteArray()
        request("POST", "$UPLOAD?uploadType=multipart", token, body, "multipart/related; boundary=$boundary")
    }

    private fun updateFile(token: String, id: String, blob: ByteArray) {
        request("PATCH", "$UPLOAD/$id?uploadType=media", token, blob, "application/octet-stream")
    }

    private fun download(token: String, id: String): ByteArray = request("GET", "$DRIVE/$id?alt=media", token)

    private fun request(method: String, url: String, token: String, body: ByteArray? = null, type: String? = null): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        // HttpURLConnection has no PATCH; Drive accepts the override header.
        if (method == "PATCH") {
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            conn.requestMethod = method
        }
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", type)
                conn.outputStream.use { it.write(body) }
            }
            if (conn.responseCode !in 200..299) throw IOException("Drive returned HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val auth = Backup.authorize(applicationContext)
        val token = auth.accessToken
        if (auth.hasResolution() || token == null) {
            Notify.signInNeeded(applicationContext)
            Result.success()
        } else {
            Backup.backUp(applicationContext, token)
            Result.success()
        }
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}
