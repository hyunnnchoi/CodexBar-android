package com.yourname.aiquota.feature.settings

import com.yourname.aiquota.core.domain.model.AiService

data class SettingsUiState(
    val serviceStates: Map<AiService, ServiceCredentialState> = AiService.entries.associateWith {
        ServiceCredentialState()
    },
    val refreshIntervalMinutes: Long = 30L,
    val showDeleteConfirmDialog: Boolean = false
)

data class ServiceCredentialState(
    val accessToken: String = "",
    val refreshToken: String = "",
    val accountId: String = "", // Codex only
    val oauthClientId: String = "", // Gemini only
    val oauthClientSecret: String = "", // Gemini only
    val expiresAtDisplay: String = "", // Gemini only (read-only)
    val isValidating: Boolean = false,
    val validationResult: ValidationResult? = null
)

sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Failure(val message: String) : ValidationResult()
}
