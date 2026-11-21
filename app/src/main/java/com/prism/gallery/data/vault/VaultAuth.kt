package com.prism.gallery.data.vault

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prism.gallery.data.appStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class VaultMode { REAL, DECOY }

@Singleton
class VaultSession @Inject constructor() {
    private val _mode = MutableStateFlow<VaultMode?>(null)
    val mode: StateFlow<VaultMode?> = _mode
    fun unlock(m: VaultMode) { _mode.value = m }
    fun lock() { _mode.value = null }
}

/** Password/PIN stored as salted PBKDF2-HMAC-SHA256. A second "decoy" secret opens an empty vault. */
@Singleton
class VaultAuth @Inject constructor(@ApplicationContext private val ctx: Context, private val session: VaultSession) {
    private val real = stringPreferencesKey("vault_real")
    private val decoy = stringPreferencesKey("vault_decoy")
    private val fails = intPreferencesKey("vault_fails")

    private fun hash(secret: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(secret.toCharArray(), salt, 120_000, 256)).encoded

    private fun encode(secret: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(hash(secret, salt), Base64.NO_WRAP)
    }

    private fun matches(stored: String?, secret: String): Boolean {
        val (s, h) = stored?.split(":")?.takeIf { it.size == 2 } ?: return false
        return java.security.MessageDigest.isEqual(hash(secret, Base64.decode(s, Base64.NO_WRAP)), Base64.decode(h, Base64.NO_WRAP))
    }

    suspend fun isConfigured(): Boolean = ctx.appStore.data.first()[real] != null

    suspend fun setup(secret: String, decoySecret: String?) {
        // PBKDF2 is deliberately slow: never run it on the main thread.
        val realHash = withContext(Dispatchers.Default) { encode(secret) }
        val decoyHash = if (decoySecret.isNullOrBlank()) null else withContext(Dispatchers.Default) { encode(decoySecret) }
        ctx.appStore.edit { it[real] = realHash; if (decoyHash != null) it[decoy] = decoyHash }
    }

    /** Returns the unlock mode, or null on failure. After 3 consecutive failures [onBreakIn] fires (hook for the intruder selfie). */
    suspend fun attempt(secret: String, onBreakIn: () -> Unit): VaultMode? {
        val prefs = ctx.appStore.data.first()
        val mode = withContext(Dispatchers.Default) {
            when {
                matches(prefs[real], secret) -> VaultMode.REAL
                matches(prefs[decoy], secret) -> VaultMode.DECOY
                else -> null
            }
        }
        ctx.appStore.edit { it[fails] = if (mode == null) (prefs[fails] ?: 0) + 1 else 0 }
        if (mode == null && (prefs[fails] ?: 0) + 1 >= 3) onBreakIn()
        mode?.let(session::unlock)
        return mode
    }

    fun unlockWithBiometric() = session.unlock(VaultMode.REAL)
}
