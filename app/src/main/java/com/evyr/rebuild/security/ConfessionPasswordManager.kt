package com.evyr.rebuild.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Password gate for the Confession Booth (APPLE cheat code) -- deliberately
 * separate from PinManager per design discussion, so unlocking the app itself
 * doesn't also unlock the booth. Same pattern as PinManager: salted SHA-256,
 * never plaintext, in its own EncryptedSharedPreferences namespace.
 *
 * Same honesty applies here as noted in PinManager: this is a privacy speed
 * bump against casual access, not a cryptographic vault. Entries themselves
 * are stored as plain text in Room once the booth is unlocked for a session --
 * the password gates entry to the booth screen, it doesn't encrypt each
 * entry individually.
 */
class ConfessionPasswordManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "rebuild_confession_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun hasPasswordSet(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains("confession_hash")
    }

    suspend fun setPassword(password: String) = withContext(Dispatchers.IO) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString("confession_salt", salt)
            .putString("confession_hash", hash)
            .apply()
    }

    suspend fun verifyPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        val salt = prefs.getString("confession_salt", null) ?: return@withContext false
        val storedHash = prefs.getString("confession_hash", null) ?: return@withContext false
        hashPassword(password, salt) == storedHash
    }

    /** Clears the password entirely (a deliberate "forget it and start over"
     *  escape hatch -- does not touch or delete existing entries). */
    suspend fun clearPassword() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove("confession_salt")
            .remove("confession_hash")
            .apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
