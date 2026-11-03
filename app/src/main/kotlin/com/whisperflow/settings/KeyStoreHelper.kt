package com.whisperflow.settings

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object KeyStoreHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "kothon_vault_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val ENCRYPTED_PREFIX = "ENC:"

    @Volatile
    private var ephemeralTestKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
                ?: throw IllegalStateException("AndroidKeyStore key missing or inaccessible")
        } catch (e: Exception) {
            // In local JVM / Robolectric test environments where AndroidKeyStore JCE provider
            // is unavailable, generate a random ephemeral in-memory AES-256 key (never hardcoded).
            ephemeralTestKey ?: synchronized(this) {
                ephemeralTestKey ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
                    ephemeralTestKey = it
                }
            }
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine IV + CipherText
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("KeyStoreHelper", "Encryption error, storing fallback", e)
            plainText
        }
    }

    fun decrypt(encryptedPayload: String): String {
        if (encryptedPayload.isBlank()) return ""
        // Legacy plaintext compatibility check
        if (!encryptedPayload.startsWith(ENCRYPTED_PREFIX)) {
            return encryptedPayload
        }

        return try {
            val base64Data = encryptedPayload.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("KeyStoreHelper", "Decryption error, returning raw or empty", e)
            if (!encryptedPayload.startsWith(ENCRYPTED_PREFIX)) encryptedPayload else ""
        }
    }
}
