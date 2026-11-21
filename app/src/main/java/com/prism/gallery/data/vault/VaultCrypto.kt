package com.prism.gallery.data.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** AES-256-GCM, key held in the Android Keystore (hardware-backed where available). Files live in app-private storage, so the system gallery never sees them. */
@Singleton
class VaultCrypto @Inject constructor(@ApplicationContext ctx: Context) {
    private val dir = File(ctx.filesDir, "vault").apply { mkdirs() }
    private val alias = "prism_vault_key"

    @Volatile private var cachedKey: SecretKey? = null

    private fun key(): SecretKey = cachedKey ?: synchronized(this) { cachedKey ?: loadKey().also { cachedKey = it } }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    fun list(): List<File> = dir.listFiles { f -> f.extension == "enc" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun encrypt(input: InputStream, ext: String): File {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = File(dir, "${System.currentTimeMillis()}-${SecureRandom().nextInt(99999)}.$ext.enc")
        out.outputStream().use { f ->
            f.write(c.iv.size); f.write(c.iv)
            CipherOutputStream(f, c).use { input.copyTo(it) }
        }
        return out
    }

    fun decrypt(file: File): ByteArray = file.inputStream().use { f ->
        val iv = ByteArray(f.read()).also { f.read(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        CipherInputStream(f, c).readBytes()
    }

    /** Overwrite with random bytes before unlinking. Flash wear-levelling means this is best-effort, not forensic-grade. */
    fun shred(file: File) {
        runCatching {
            file.outputStream().use { o -> val buf = ByteArray(8192); var left = file.length(); while (left > 0) { SecureRandom().nextBytes(buf); o.write(buf, 0, minOf(left, 8192).toInt()); left -= 8192 } }
        }
        file.delete()
    }
}
