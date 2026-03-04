package com.codexbar.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val saveJobs = mutableMapOf<AiService, Job>()
    private val pendingChanges = MutableStateFlow<Pair<AiService, String>?>(null)

    init {
        loadSavedCredentials()
        _uiState.update { it.copy(refreshIntervalMinutes = prefsManager.getRefreshInterval()) }
        observePendingChanges()
    }

    private fun loadSavedCredentials() {
        for (service in AiService.entries) {
            val credential = prefsManager.loadCredential(service) ?: continue
            val state = when (credential) {
                is Credential.ClaudeCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken ?: ""
                )
                is Credential.CodexCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken,
                    accountId = credential.accountId ?: ""
                )
                is Credential.GeminiCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken,
                    oauthClientId = credential.oauthClientId,
                    oauthClientSecret = credential.oauthClientSecret,
                    expiresAtDisplay = formatExpiryMs(credential.expiresAtMs)
                )
            }
            _uiState.update {
                it.copy(serviceStates = it.serviceStates + (service to state))
            }
        }
    }

    fun updateField(service: AiService, field: String, value: String) {
        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            val updated = when (field) {
                "accessToken" -> current.copy(accessToken = value, validationResult = null)
                "refreshToken" -> current.copy(refreshToken = value, validationResult = null)
                "accountId" -> current.copy(accountId = value, validationResult = null)
                "oauthClientId" -> current.copy(oauthClientId = value, validationResult = null)
                "oauthClientSecret" -> current.copy(oauthClientSecret = value, validationResult = null)
                else -> current
            }
            state.copy(serviceStates = state.serviceStates + (service to updated))
        }

        // Debounced save
        scheduleSave(service)
    }

    @OptIn(FlowPreview::class)
    private fun observePendingChanges() {
        viewModelScope.launch {
            pendingChanges
                .debounce(500)
                .collect { pair ->
                    pair?.let { (service, _) -> saveCredential(service) }
                }
        }
    }

    private fun scheduleSave(service: AiService) {
        pendingChanges.value = service to System.currentTimeMillis().toString()
    }

    private fun saveCredential(service: AiService) {
        val state = _uiState.value.serviceStates[service] ?: return
        if (state.accessToken.isBlank()) return

        val credential = when (service) {
            AiService.CLAUDE -> Credential.ClaudeCredential(
                accessToken = state.accessToken,
                refreshToken = state.refreshToken.ifBlank { null }
            )
            AiService.CODEX -> {
                if (state.refreshToken.isBlank()) return
                Credential.CodexCredential(
                    accessToken = state.accessToken,
                    refreshToken = state.refreshToken,
                    accountId = state.accountId.ifBlank { null }
                )
            }
            AiService.GEMINI -> {
                if (state.refreshToken.isBlank() || state.oauthClientId.isBlank() || state.oauthClientSecret.isBlank()) return
                Credential.GeminiCredential(
                    accessToken = state.accessToken,
                    refreshToken = state.refreshToken,
                    expiresAtMs = System.currentTimeMillis() + 3600_000, // default 1h
                    oauthClientId = state.oauthClientId,
                    oauthClientSecret = state.oauthClientSecret
                )
            }
        }

        prefsManager.saveCredential(service, credential)
    }

    fun validateCredential(service: AiService) {
        val repo = when (service) {
            AiService.CLAUDE -> claudeRepository
            AiService.CODEX -> codexRepository
            AiService.GEMINI -> geminiRepository
        }

        // Ensure saved before validation
        saveCredential(service)

        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            state.copy(
                serviceStates = state.serviceStates + (service to current.copy(isValidating = true, validationResult = null))
            )
        }

        viewModelScope.launch {
            val result = repo.validateCredential()
            val validationResult = when (result) {
                is Result.Success -> ValidationResult.Success
                is Result.Failure -> ValidationResult.Failure(formatAppError(result.error))
            }

            _uiState.update { state ->
                val current = state.serviceStates[service] ?: ServiceCredentialState()
                state.copy(
                    serviceStates = state.serviceStates + (service to current.copy(
                        isValidating = false,
                        validationResult = validationResult
                    ))
                )
            }
        }
    }

    fun setRefreshInterval(minutes: Long) {
        prefsManager.setRefreshInterval(minutes)
        _uiState.update { it.copy(refreshIntervalMinutes = minutes) }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun deleteAllCredentials() {
        prefsManager.deleteAllCredentials()
        _uiState.update {
            SettingsUiState(
                refreshIntervalMinutes = it.refreshIntervalMinutes
            )
        }
    }

    private fun formatExpiryMs(expiresAtMs: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(expiresAtMs)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun formatAppError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network error: ${error.message}"
            is AppError.AuthError -> if (error.isTerminal) "Authentication failed (re-login required)" else "Authentication error"
            is AppError.RateLimited -> "Rate limited — try again later"
            is AppError.ParseError -> "Unexpected response format"
            is AppError.CredentialNotFound -> "No credentials saved"
            is AppError.ServiceUnavailable -> "Service temporarily unavailable"
        }
    }
}
