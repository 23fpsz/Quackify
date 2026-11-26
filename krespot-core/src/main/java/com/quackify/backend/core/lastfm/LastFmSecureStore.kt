package com.quackify.backend.core.lastfm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted storage for the Last.fm session key.
 *
 * The AES key is generated and retained by Android Keystore.
 * Only the encrypted session key and its IV are stored in preferences.
 */
object LastFmSecureStore {

    private const val KEY_ALIAS = "quackify_lastfm_session"
    private const val PREFS_NAME = "lastfm_secure"
    private const val PREF_CIPHERTEXT = "session_ciphertext"
    private const val PREF_IV = "session_iv"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128

    @Synchronized
    fun saveSessionKey(context: Context, sessionKey: String) {
        require(sessionKey.isNotBlank()) { "Session key must not be blank" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val encrypted = cipher.doFinal(sessionKey.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        preferences(context).edit()
            .putString(PREF_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun getSessionKey(context: Context): String? {
        val prefs = preferences(context)
        val encryptedText = prefs.getString(PREF_CIPHERTEXT, null) ?: return null
        val ivText = prefs.getString(PREF_IV, null) ?: return null

        return try {
            val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
            val iv = Base64.decode(ivText, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, iv)
            )

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            prefs.edit()
                .remove(PREF_CIPHERTEXT)
                .remove(PREF_IV)
                .apply()
            null
        }
    }

    @Synchronized
    fun clearSessionKey(context: Context) {
        preferences(context).edit()
            .remove(PREF_CIPHERTEXT)
            .remove(PREF_IV)
            .apply()

        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }
}
