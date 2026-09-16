package com.evyr.rebuild.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the PIN as a salted hash, never plaintext. Note (per design doc discussion):
 * this is a privacy speed bump against casual access to the device, not a security
 * vault — app-level storage on a rooted/compromised device is not airtight. Good enough
 * for "keep it out of casual view," not for anything that needs real cryptographic guarantees.
 */
class PinManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "rebuild_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun hasPinSet(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains("pin_hash")
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", hash)
            .apply()
    }

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val salt = prefs.getString("pin_salt", null) ?: return@withContext false
        val storedHash = prefs.getString("pin_hash", null) ?: return@withContext false
        hashPin(pin, salt) == storedHash
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
