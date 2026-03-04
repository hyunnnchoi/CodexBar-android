package com.yourname.aiquota.core.data

import com.yourname.aiquota.core.domain.model.AiService
import com.yourname.aiquota.core.domain.model.AppError
import com.yourname.aiquota.core.domain.model.Credential
import com.yourname.aiquota.core.domain.model.QuotaInfo
import com.yourname.aiquota.core.domain.model.Result
import com.yourname.aiquota.core.domain.model.UsageWindow
import com.yourname.aiquota.core.domain.repository.QuotaRepository
import com.yourname.aiquota.core.network.codex.CodexApiService
import com.yourname.aiquota.core.network.codex.CodexDto
import com.yourname.aiquota.core.network.codex.CodexTokenRefreshService
import com.yourname.aiquota.core.security.EncryptedPrefsManager
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class CodexRepositoryImpl @Inject constructor(
    private val apiService: CodexApiService,
    private val tokenRefreshService: CodexTokenRefreshService,
    private val prefsManager: EncryptedPrefsManager
) : QuotaRepository {

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.CODEX)
            as? Credential.CodexCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.CODEX))

        return try {
            val response = apiService.getUsage(
                authorization = "Bearer ${credential.accessToken}",
                accountId = credential.accountId
            )

            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty response body"))
                    Result.Success(mapToQuotaInfo(body))
                }
                401 -> {
                    val refreshed = refreshToken(credential)
                    if (refreshed != null) {
                        val retryResponse = apiService.getUsage(
                            authorization = "Bearer ${refreshed.accessToken}",
                            accountId = refreshed.accountId
                        )
                        if (retryResponse.isSuccessful) {
                            val body = retryResponse.body()
                                ?: return Result.Failure(AppError.ParseError("Empty response body"))
                            Result.Success(mapToQuotaInfo(body))
                        } else {
                            Result.Failure(AppError.AuthError(AiService.CODEX, isTerminal = true))
                        }
                    } else {
                        Result.Failure(AppError.AuthError(AiService.CODEX, isTerminal = true))
                    }
                }
                429 -> Result.Failure(AppError.RateLimited)
                else -> Result.Failure(
                    AppError.NetworkError("HTTP ${response.code()}: ${response.message()}")
                )
            }
        } catch (e: IOException) {
            Result.Failure(AppError.NetworkError(e.message ?: "Network error", e))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError(e.message ?: "Parse error", e))
        }
    }

    override suspend fun validateCredential(): Result<Unit, AppError> {
        return when (val result = fetchQuota()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }

    private suspend fun refreshToken(credential: Credential.CodexCredential): Credential.CodexCredential? {
        return try {
            val request = CodexDto.TokenRefreshRequest(refreshToken = credential.refreshToken)
            val response = tokenRefreshService.refreshToken(request)

            if (response.isSuccessful) {
                val body = response.body() ?: return null
                val newCredential = Credential.CodexCredential(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken ?: credential.refreshToken,
                    accountId = credential.accountId
                )
                prefsManager.saveCredential(AiService.CODEX, newCredential)
                newCredential
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                val isTerminal = CodexDto.TERMINAL_ERROR_CODES.any { errorBody.contains(it) }
                if (isTerminal) {
                    prefsManager.deleteCredential(AiService.CODEX)
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapToQuotaInfo(response: CodexDto.UsageResponse): QuotaInfo {
        val windows = buildList {
            response.rateLimit?.primaryWindow?.let { window ->
                add(mapRateLimitWindow("primary", window))
            }
            response.rateLimit?.secondaryWindow?.let { window ->
                add(mapRateLimitWindow("secondary", window))
            }
        }

        return QuotaInfo(
            service = AiService.CODEX,
            windows = windows,
            extraUsage = null,
            tier = response.planType,
            fetchedAt = Instant.now()
        )
    }

    private fun mapRateLimitWindow(type: String, window: CodexDto.RateLimitWindow): UsageWindow {
        val label = when (window.limitWindowSeconds) {
            18000L -> "5-Hour"
            604800L -> "7-Day"
            else -> {
                val seconds = window.limitWindowSeconds ?: 0L
                if (seconds > 0) "${seconds / 3600}h" else type.replaceFirstChar { it.uppercase() }
            }
        }

        return UsageWindow(
            label = label,
            utilization = window.usedPercent / 100.0,
            resetsAt = window.resetAt?.let { Instant.ofEpochSecond(it) }
        )
    }

    companion object {
        fun parseBalance(element: kotlinx.serialization.json.JsonElement?): Double? {
            if (element == null) return null
            return when (element) {
                is JsonPrimitive -> {
                    element.doubleOrNull ?: element.content.toDoubleOrNull()
                }
                else -> null
            }
        }
    }
}
