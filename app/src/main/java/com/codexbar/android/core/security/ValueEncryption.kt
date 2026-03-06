package com.codexbar.android.core.security

import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KeyStore 기반 AES-256-GCM 값 암복호화.
 * DataStore에 민감한 값을 저장할 때 사용합니다.
 */
@Singleton
class ValueEncryption @Inject constructor() {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "codexbar_datastore_aes_key"
    private val gcmTagLength = 128
    private val gcmIvLength = 12

    private val secretKey: SecretKey by lazy {
        if (keyStore.containsAlias(keyAlias)) {
            (keyStore.getKey(keyAlias, null) as SecretKey)
        } else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
        }
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(encrypted, 0, this, iv.size, encrypted.size)
        }
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? {
        if (encoded.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= gcmIvLength) return null
            val iv = combined.copyOfRange(0, gcmIvLength)
            val encrypted = combined.copyOfRange(gcmIvLength, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(gcmTagLength, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
