package com.codexbar.android.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "codexbar_secure_prefs"
)

@Singleton
class EncryptedPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val valueEncryption: ValueEncryption
) {
    private val dataStore = context.secureDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class Cache(
        val credentialServices: Set<AiService> = emptySet(),
        val refreshIntervalMinutes: Long = 30L,
        val notificationsEnabled: Boolean = true
    )

    private val cache = AtomicReference<Cache?>(null)

    private suspend fun ensureCache() {
        if (cache.get() != null) return
        val prefs = dataStore.data.first()
        val services = AiService.entries.filter { service ->
            prefs[stringPreferencesKey("${service.name}_access_token")] != null
        }.toSet()
        val interval = prefs[longPreferencesKey("refresh_interval_minutes")] ?: 30L
        val notifications = prefs[booleanPreferencesKey("notifications_enabled")] ?: true
        cache.set(Cache(services, interval, notifications))
    }

    private fun getEncrypted(key: String, prefs: Preferences): String? {
        val raw = prefs[stringPreferencesKey(key)] ?: return null
        return valueEncryption.decrypt(raw) ?: raw
    }

    suspend fun saveCredential(service: AiService, credential: Credential) {
        dataStore.edit { prefs ->
            val prefix = service.name
            prefs[stringPreferencesKey("${prefix}_access_token")] =
                valueEncryption.encrypt(credential.accessToken)
            credential.refreshToken?.let {
                prefs[stringPreferencesKey("${prefix}_refresh_token")] = valueEncryption.encrypt(it)
            }

            when (credential) {
                is Credential.ClaudeCredential -> {
                    credential.expiresAt?.let {
                        prefs[longPreferencesKey("${prefix}_expires_at")] = it.epochSecond
                    }
                    credential.scopes?.let {
                        prefs[stringPreferencesKey("${prefix}_scopes")] = valueEncryption.encrypt(it)
                    }
                    credential.rateLimitTier?.let {
                        prefs[stringPreferencesKey("${prefix}_rate_limit_tier")] =
                            valueEncryption.encrypt(it)
                    }
                }
                is Credential.CodexCredential -> {
                    credential.accountId?.let {
                        prefs[stringPreferencesKey("${prefix}_account_id")] =
                            valueEncryption.encrypt(it)
                    }
                }
                is Credential.GeminiCredential -> {
                    prefs[longPreferencesKey("${prefix}_expires_at_ms")] = credential.expiresAtMs
                    prefs[stringPreferencesKey("${prefix}_oauth_client_id")] =
                        valueEncryption.encrypt(credential.oauthClientId)
                    prefs[stringPreferencesKey("${prefix}_oauth_client_secret")] =
                        valueEncryption.encrypt(credential.oauthClientSecret)
                }
            }
        }
        cache.set(
            cache.get()?.copy(credentialServices = cache.get()!!.credentialServices + service)
                ?: Cache(credentialServices = setOf(service))
        )
    }

    suspend fun loadCredential(service: AiService): Credential? {
        ensureCache()
        val prefs = dataStore.data.first()
        val prefix = service.name
        val accessToken = getEncrypted("${prefix}_access_token", prefs) ?: return null

        return when (service) {
            AiService.CLAUDE -> {
                val refreshToken = getEncrypted("${prefix}_refresh_token", prefs)
                val expiresAt = prefs[longPreferencesKey("${prefix}_expires_at")]
                    ?.takeIf { it > 0 }
                    ?.let { Instant.ofEpochSecond(it) }
                val scopes = getEncrypted("${prefix}_scopes", prefs)
                val rateLimitTier = getEncrypted("${prefix}_rate_limit_tier", prefs)
                Credential.ClaudeCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    scopes = scopes,
                    rateLimitTier = rateLimitTier
                )
            }
            AiService.CODEX -> {
                val refreshToken = getEncrypted("${prefix}_refresh_token", prefs) ?: return null
                val accountId = getEncrypted("${prefix}_account_id", prefs)
                Credential.CodexCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accountId = accountId
                )
            }
            AiService.GEMINI -> {
                val refreshToken = getEncrypted("${prefix}_refresh_token", prefs) ?: return null
                val expiresAtMs = prefs[longPreferencesKey("${prefix}_expires_at_ms")]
                    ?.takeIf { it > 0 } ?: return null
                val clientId = getEncrypted("${prefix}_oauth_client_id", prefs) ?: return null
                val clientSecret = getEncrypted("${prefix}_oauth_client_secret", prefs) ?: return null
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

    suspend fun deleteCredential(service: AiService) {
        dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith(service.name) }
            toRemove.forEach { prefs.remove(it) }
        }
        cache.set(
            cache.get()?.copy(credentialServices = cache.get()!!.credentialServices - service)
                ?: Cache()
        )
    }

    suspend fun deleteAllCredentials() {
        dataStore.edit { it.clear() }
        cache.set(Cache())
    }

    fun hasCredential(service: AiService): Boolean {
        val c = cache.get()
        if (c != null) return service in c.credentialServices
        scope.launch { ensureCache() }
        return false
    }

    fun getRefreshInterval(): Long {
        val c = cache.get()
        if (c != null) return c.refreshIntervalMinutes
        scope.launch { ensureCache() }
        return 30L
    }

    suspend fun setRefreshInterval(minutes: Long) {
        dataStore.edit {
            it[longPreferencesKey("refresh_interval_minutes")] = minutes
        }
        cache.set(cache.get()?.copy(refreshIntervalMinutes = minutes) ?: Cache(refreshIntervalMinutes = minutes))
    }

    fun isNotificationsEnabled(): Boolean {
        val c = cache.get()
        if (c != null) return c.notificationsEnabled
        scope.launch { ensureCache() }
        return true
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit {
            it[booleanPreferencesKey("notifications_enabled")] = enabled
        }
        cache.set(cache.get()?.copy(notificationsEnabled = enabled) ?: Cache(notificationsEnabled = enabled))
    }

    suspend fun saveResetTimes(service: AiService, windows: List<Pair<String, Instant?>>) {
        dataStore.edit { prefs ->
            windows.forEach { (label, resetsAt) ->
                val key = longPreferencesKey("${service.name}_${label}_resets_at")
                if (resetsAt != null) {
                    prefs[key] = resetsAt.epochSecond
                } else {
                    prefs.remove(key)
                }
            }
        }
    }

    suspend fun loadResetTimes(service: AiService): Map<String, Instant> {
        val prefs = dataStore.data.first()
        val prefix = "${service.name}_"
        val suffix = "_resets_at"
        return prefs.asMap()
            .filter { (key, _) -> key.name.startsWith(prefix) && key.name.endsWith(suffix) }
            .mapNotNull { (key, value) ->
                val label = key.name.removePrefix(prefix).removeSuffix(suffix)
                val epochSecond = (value as? Long)?.takeIf { it > 0 } ?: return@mapNotNull null
                label to Instant.ofEpochSecond(epochSecond)
            }
            .toMap()
    }
}
