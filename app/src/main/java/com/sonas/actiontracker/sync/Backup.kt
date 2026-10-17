package com.sonas.actiontracker.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.sonas.actiontracker.data.Db
import com.sonas.actiontracker.data.Item
import com.sonas.actiontracker.data.Processed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.SecureRandom
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
 * the Android Keystore so the nightly backup can run without asking.
 *
 * File layout: "ATB1" | salt (16) | iv (12) | ciphertext of the JSON export.
 */
object Backup {
    private const val FILE_NAME = "action-tracker-backup.bin"
    private const val MAGIC = "ATB1"
    private const val ITERATIONS = 210_000
    private const val PREFS = "backup"
    private const val WRAP_ALIAS = "action_tracker_backup_wrap"
    private const val WORK = "drive_backup"
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

    /** Turns on backups with [passphrase] and starts the nightly job. */
    fun enable(context: Context, passphrase: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        storeKey(context, salt, derive(passphrase, salt))
        schedule(context)
    }

    fun disable(context: Context) {
        prefs(context).edit().remove("key").remove("key_iv").remove("salt").apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
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
        val existing = findFile(token)
        if (existing == null) createFile(token, blob) else updateFile(token, existing, blob)
        prefs(context).edit().putLong("last", System.currentTimeMillis()).apply()
    }

    /**
     * Downloads the backup, decrypts it with [passphrase] and replaces everything on
     * this phone with it. Also turns on nightly backups with the same passphrase.
     * Returns the number of actions restored.
     */
    suspend fun restore(context: Context, token: String, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        val id = findFile(token) ?: throw IOException("No backup found in your Google Drive")
        val blob = download(token, id)
        if (blob.size < 33 || String(blob, 0, 4, Charsets.US_ASCII) != MAGIC) throw IOException("Not an Action Tracker backup")
        val salt = blob.copyOfRange(4, 20)
        val key = derive(passphrase, salt)
        val json = try {
            decrypt(blob, key)
        } catch (e: Exception) {
            throw IOException("Wrong passphrase")
        }
        val count = import(context, JSONObject(String(json, Charsets.UTF_8)))
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
                    .put("title", i.title).put("notes", i.notes).put("person", i.person)
                    .put("context", i.context).put("dueDate", i.dueDate).put("status", i.status)
                    .put("source", i.source).put("createdAt", i.createdAt).put("body", i.body)
                    .apply { i.completedAt?.let { put("completedAt", it) } }
            )
        }
        val processed = JSONArray()
        dao.allProcessed().forEach { processed.put(JSONObject().put("key", it.sourceKey).put("at", it.at)) }
        return JSONObject().put("version", 1).put("items", items).put("processed", processed)
    }

    private suspend fun import(context: Context, root: JSONObject): Int {
        val db = Db.get(context)
        val dao = db.dao()
        val items = root.getJSONArray("items")
        val processed = root.getJSONArray("processed")
        db.withTransaction {
            dao.deleteAllItems()
            dao.deleteAllProcessed()
            for (n in 0 until items.length()) {
                val o = items.getJSONObject(n)
                dao.insert(
                    Item(
                        title = o.getString("title"), notes = o.optString("notes"),
                        person = o.optString("person", "Me"), context = o.optString("context", "Work"),
                        dueDate = o.getLong("dueDate"), status = o.getString("status"),
                        source = o.optString("source", "CUSTOM"), createdAt = o.optLong("createdAt"),
                        body = o.optString("body"),
                        completedAt = if (o.has("completedAt")) o.getLong("completedAt") else null
                    )
                )
            }
            for (n in 0 until processed.length()) {
                val o = processed.getJSONObject(n)
                dao.markProcessed(Processed(o.getString("key"), o.getLong("at")))
            }
        }
        return items.length()
    }

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

    private fun findFile(token: String): String? {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val res = JSONObject(String(request("GET", "$DRIVE?spaces=appDataFolder&q=$q&fields=files(id)", token)))
        val files = res.optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
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
