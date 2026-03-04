package com.yourname.aiquota.core.data

import com.yourname.aiquota.core.domain.model.AiService
import com.yourname.aiquota.core.domain.model.AppError
import com.yourname.aiquota.core.domain.model.Credential
import com.yourname.aiquota.core.domain.model.ExtraUsage
import com.yourname.aiquota.core.domain.model.QuotaInfo
import com.yourname.aiquota.core.domain.model.Result
import com.yourname.aiquota.core.domain.model.UsageWindow
import com.yourname.aiquota.core.domain.repository.QuotaRepository
import com.yourname.aiquota.core.network.claude.ClaudeApiService
import com.yourname.aiquota.core.network.claude.ClaudeDto
import com.yourname.aiquota.core.network.claude.ClaudeTokenRefreshService
import com.yourname.aiquota.core.security.EncryptedPrefsManager
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class ClaudeRepositoryImpl @Inject constructor(
    private val apiService: ClaudeApiService,
    private val tokenRefreshService: ClaudeTokenRefreshService,
    private val prefsManager: EncryptedPrefsManager
) : QuotaRepository {

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.CLAUDE)
            as? Credential.ClaudeCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.CLAUDE))

        val workingCredential = ensureValidToken(credential)
            ?: return Result.Failure(AppError.AuthError(AiService.CLAUDE, isTerminal = true))

        return try {
            val response = apiService.getUsage("Bearer ${workingCredential.accessToken}")

            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return Result.Failure(AppError.ParseError("Empty response body"))
                    Result.Success(mapToQuotaInfo(body))
                }
                401 -> {
                    // Try refresh once, then retry
                    val refreshed = refreshToken(workingCredential)
                    if (refreshed != null) {
                        val retryResponse = apiService.getUsage("Bearer ${refreshed.accessToken}")
                        if (retryResponse.isSuccessful) {
                            val body = retryResponse.body()
                                ?: return Result.Failure(AppError.ParseError("Empty response body"))
                            Result.Success(mapToQuotaInfo(body))
                        } else {
                            Result.Failure(AppError.AuthError(AiService.CLAUDE, isTerminal = true))
                        }
                    } else {
                        Result.Failure(AppError.AuthError(AiService.CLAUDE, isTerminal = true))
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

    private suspend fun ensureValidToken(credential: Credential.ClaudeCredential): Credential.ClaudeCredential? {
        val expiresAt = credential.expiresAt ?: return credential
        if (Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return credential
        }
        return refreshToken(credential)
    }

    private suspend fun refreshToken(credential: Credential.ClaudeCredential): Credential.ClaudeCredential? {
        val refreshToken = credential.refreshToken ?: return null

        return try {
            val response = tokenRefreshService.refreshToken(refreshToken = refreshToken)
            if (response.isSuccessful) {
                val body = response.body() ?: return null
                val newCredential = Credential.ClaudeCredential(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken ?: refreshToken,
                    expiresAt = Instant.now().plusSeconds(body.expiresIn.toLong()),
                    scopes = credential.scopes,
                    rateLimitTier = credential.rateLimitTier
                )
                prefsManager.saveCredential(AiService.CLAUDE, newCredential)
                newCredential
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapToQuotaInfo(response: ClaudeDto.OAuthUsageResponse): QuotaInfo {
        val windows = buildList {
            response.fiveHour?.let { add(mapWindow("5-Hour", it)) }
            response.sevenDay?.let { add(mapWindow("7-Day", it)) }
            response.sevenDayOauthApps?.let { add(mapWindow("OAuth Apps", it)) }
            response.sevenDayOpus?.let { add(mapWindow("Opus", it)) }
            response.sevenDaySonnet?.let { add(mapWindow("Sonnet", it)) }
            response.iguanaNecktie?.let { add(mapWindow("Extended", it)) }
        }

        val extraUsage = response.extraUsage?.takeIf { it.isEnabled }?.let {
            ExtraUsage(
                isEnabled = it.isEnabled,
                monthlyLimit = it.monthlyLimit,
                usedCredits = it.usedCredits,
                utilization = it.utilization,
                currency = it.currency
            )
        }

        return QuotaInfo(
            service = AiService.CLAUDE,
            windows = windows,
            extraUsage = extraUsage,
            fetchedAt = Instant.now()
        )
    }

    private fun mapWindow(label: String, window: ClaudeDto.OAuthUsageWindow): UsageWindow {
        return UsageWindow(
            label = label,
            utilization = ((window.utilization ?: 0.0) / 100.0).coerceIn(0.0, 1.0),
            resetsAt = window.resetsAt?.let { parseInstant(it) }
        )
    }

    private fun parseInstant(isoString: String): Instant? {
        return try {
            Instant.parse(isoString)
        } catch (_: Exception) {
            null
        }
    }
}
