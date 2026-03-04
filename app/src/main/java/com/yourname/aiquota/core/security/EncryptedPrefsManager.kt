package com.yourname.aiquota.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.yourname.aiquota.core.domain.model.AiService
import com.yourname.aiquota.core.domain.model.Credential
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "aiquota_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredential(service: AiService, credential: Credential) {
        val editor = prefs.edit()
        val prefix = service.name

        editor.putString("${prefix}_access_token", credential.accessToken)
        editor.putString("${prefix}_refresh_token", credential.refreshToken)

        when (credential) {
            is Credential.ClaudeCredential -> {
                credential.expiresAt?.let {
                    editor.putLong("${prefix}_expires_at", it.epochSecond)
                }
                credential.scopes?.let {
                    editor.putString("${prefix}_scopes", it)
                }
                credential.rateLimitTier?.let {
                    editor.putString("${prefix}_rate_limit_tier", it)
                }
            }
            is Credential.CodexCredential -> {
                credential.accountId?.let {
                    editor.putString("${prefix}_account_id", it)
                }
            }
            is Credential.GeminiCredential -> {
                editor.putLong("${prefix}_expires_at_ms", credential.expiresAtMs)
                editor.putString("${prefix}_oauth_client_id", credential.oauthClientId)
                editor.putString("${prefix}_oauth_client_secret", credential.oauthClientSecret)
            }
        }

        editor.apply() // atomic write via SharedPreferences commit semantics
    }

    fun loadCredential(service: AiService): Credential? {
        val prefix = service.name
        val accessToken = prefs.getString("${prefix}_access_token", null) ?: return null

        return when (service) {
            AiService.CLAUDE -> {
                val refreshToken = prefs.getString("${prefix}_refresh_token", null)
                val expiresAt = prefs.getLong("${prefix}_expires_at", -1L)
                    .takeIf { it > 0 }
                    ?.let { Instant.ofEpochSecond(it) }
                val scopes = prefs.getString("${prefix}_scopes", null)
                val rateLimitTier = prefs.getString("${prefix}_rate_limit_tier", null)
                Credential.ClaudeCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    scopes = scopes,
                    rateLimitTier = rateLimitTier
                )
            }
            AiService.CODEX -> {
                val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: return null
                val accountId = prefs.getString("${prefix}_account_id", null)
                Credential.CodexCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accountId = accountId
                )
            }
            AiService.GEMINI -> {
                val refreshToken = prefs.getString("${prefix}_refresh_token", null) ?: return null
                val expiresAtMs = prefs.getLong("${prefix}_expires_at_ms", -1L)
                    .takeIf { it > 0 } ?: return null
                val clientId = prefs.getString("${prefix}_oauth_client_id", null) ?: return null
                val clientSecret = prefs.getString("${prefix}_oauth_client_secret", null) ?: return null
                Credential.GeminiCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMs = expiresAtMs,
                    oauthClientId = clientId,
                    oauthClientSecret = clientSecret
                )
            }
        }
    }

    fun deleteCredential(service: AiService) {
        val prefix = service.name
        val editor = prefs.edit()

        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        keys.forEach { editor.remove(it) }

        editor.apply()
    }

    fun deleteAllCredentials() {
        prefs.edit().clear().apply()
    }

    fun hasCredential(service: AiService): Boolean {
        return prefs.getString("${service.name}_access_token", null) != null
    }

    fun getRefreshInterval(): Long {
        return prefs.getLong("refresh_interval_minutes", 30L)
    }

    fun setRefreshInterval(minutes: Long) {
        prefs.edit().putLong("refresh_interval_minutes", minutes).apply()
    }
}
