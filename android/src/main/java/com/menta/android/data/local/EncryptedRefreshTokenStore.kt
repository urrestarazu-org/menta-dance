package com.menta.android.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.menta.android.domain.repository.RefreshTokenStore
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Stores the opaque refresh credential encrypted with a non-exportable Android
 * Keystore AES/GCM key. SharedPreferences contains only ciphertext and nonce.
 */
class EncryptedRefreshTokenStore(context: Context) : RefreshTokenStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val lock = Any()

    override fun read(): String? {
        return synchronized(lock) {
            val encodedCiphertext = preferences.getString(CIPHERTEXT_KEY, null)
                ?: return@synchronized null
            val encodedNonce = preferences.getString(NONCE_KEY, null) ?: run {
                clearLocked()
                return@synchronized null
            }

            try {
                val plaintext = cipher(Cipher.DECRYPT_MODE, Base64.decode(encodedNonce, BASE64_FLAGS))
                    .doFinal(Base64.decode(encodedCiphertext, BASE64_FLAGS))
                String(plaintext, UTF_8)
            } catch (_: GeneralSecurityException) {
                clearLocked()
                null
            } catch (_: IllegalArgumentException) {
                clearLocked()
                null
            }
        }
    }

    override fun replace(refreshToken: String) {
        require(refreshToken.isNotBlank()) { "refreshToken must not be blank" }

        synchronized(lock) {
            val encryptionCipher = cipher(Cipher.ENCRYPT_MODE)
            val ciphertext = encryptionCipher.doFinal(refreshToken.toByteArray(UTF_8))

            // One synchronous editor transaction replaces both values. The old
            // credential is not retained in a second preference entry.
            check(
                preferences.edit()
                    .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, BASE64_FLAGS))
                    .putString(NONCE_KEY, Base64.encodeToString(encryptionCipher.iv, BASE64_FLAGS))
                    .commit(),
            ) { "Unable to persist encrypted refresh token" }
        }
    }

    override fun clear() {
        synchronized(lock) {
            clearLocked()
        }
    }

    private fun clearLocked() {
        check(
            preferences.edit()
                .remove(CIPHERTEXT_KEY)
                .remove(NONCE_KEY)
                .commit(),
        ) { "Unable to clear encrypted refresh token" }
    }

    private fun cipher(mode: Int, nonce: ByteArray? = null): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            if (nonce == null) {
                init(mode, getOrCreateSecretKey())
            } else {
                init(mode, getOrCreateSecretKey(), javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        // Cambiar este nombre obliga a actualizar res/xml/backup_rules.xml y
        // res/xml/data_extraction_rules.xml, que excluyen del backup el archivo
        // "$PREFERENCES_NAME.xml". Si dejan de coincidir nada falla: la
        // exclusión simplemente deja de aplicar, en silencio.
        const val PREFERENCES_NAME = "encrypted_refresh_token"
        const val CIPHERTEXT_KEY = "ciphertext"
        const val NONCE_KEY = "nonce"
        const val KEY_ALIAS = "menta.android.refresh-token.aes-gcm.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        const val BASE64_FLAGS = Base64.NO_WRAP
    }
}
