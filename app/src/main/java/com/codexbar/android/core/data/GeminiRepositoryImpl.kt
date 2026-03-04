package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.gemini.GeminiApiService
import com.codexbar.android.core.network.gemini.GeminiDto
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class GeminiRepositoryImpl @Inject constructor(
    private val apiService: GeminiApiService,
    private val tokenRefreshService: GeminiTokenRefreshService,
    private val prefsManager: EncryptedPrefsManager
) : QuotaRepository {

    override suspend fun fetchQuota(): Result<QuotaInfo, AppError> {
        val credential = prefsManager.loadCredential(AiService.GEMINI)
            as? Credential.GeminiCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.GEMINI))

        val workingCredential = ensureValidToken(credential)
            ?: return Result.Failure(AppError.AuthError(AiService.GEMINI, isTerminal = true))

        return try {
            val result = fetchQuotaWithToken(workingCredential)
            if (result is Result.Failure && result.error is AppError.AuthError) {
                // Token might be expired despite expiresAtMs — try refresh once
                val refreshed = refreshToken(workingCredential)
                if (refreshed != null) {
                    fetchQuotaWithToken(refreshed)
                } else {
                    Result.Failure(AppError.AuthError(AiService.GEMINI, isTerminal = true))
                }
            } else {
                result
            }
        } catch (e: IOException) {
            Result.Failure(AppError.NetworkError(e.message ?: "Network error", e))
        } catch (e: Exception) {
            Result.Failure(AppError.ParseError("${e::class.simpleName}: ${e.message}", e))
        }
    }

    private suspend fun fetchQuotaWithToken(
        credential: Credential.GeminiCredential
    ): Result<QuotaInfo, AppError> {
        // Step 1: loadCodeAssist to get projectId and tier
        val loadResponse = apiService.loadCodeAssist(
            authorization = "Bearer ${credential.accessToken}",
            body = GeminiDto.LoadCodeAssistRequest()
        )

        if (!loadResponse.isSuccessful) {
            return handleErrorResponse(loadResponse.code())
        }

        val loadBody = loadResponse.body()
            ?: return Result.Failure(AppError.ParseError("Empty loadCodeAssist response"))

        val projectId = extractProjectId(loadBody)
        val tierRaw = loadBody.currentTier?.id
        val tier = mapTier(tierRaw)

        // Step 2: retrieveUserQuota
        val quotaRequest = GeminiDto.RetrieveUserQuotaRequest(
            project = projectId ?: ""
        )
        val quotaResponse = apiService.retrieveUserQuota(
            authorization = "Bearer ${credential.accessToken}",
            body = quotaRequest
        )

        if (!quotaResponse.isSuccessful) {
            return handleErrorResponse(quotaResponse.code())
        }

        val quotaBody = quotaResponse.body()
            ?: return Result.Failure(AppError.ParseError("Empty quota response"))

        return Result.Success(mapToQuotaInfo(quotaBody, tier))
    }

    override suspend fun validateCredential(): Result<Unit, AppError> {
        return when (val result = fetchQuota()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }

    private fun extractProjectId(response: GeminiDto.LoadCodeAssistResponse): String? {
        val element = response.cloudaicompanionProject ?: return null
        // cloudaicompanionProject can be String or Object {"id": "..."}
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonObject -> element["id"]?.jsonPrimitive?.content
            else -> null
        }
    }

    private fun mapTier(tierId: String?): String? {
        return when (tierId) {
            "free-tier" -> "Free"
            "standard-tier" -> "Paid"
            "legacy-tier" -> "Legacy"
            null -> null
            else -> tierId
        }
    }

    private fun mapToQuotaInfo(response: GeminiDto.QuotaResponse, tier: String?): QuotaInfo {
        // Deduplicate: keep lowest remainingFraction per modelId
        val deduped = response.buckets
            .groupBy { it.modelId }
            .mapValues { (_, buckets) -> buckets.minByOrNull { it.remainingFraction } ?: buckets.first() }
            .values.toList()

        // Sort: flash models first, then alphabetical
        val sorted = deduped.sortedWith(compareBy<GeminiDto.QuotaBucket> {
            if (it.modelId.contains("flash", ignoreCase = true)) 0 else 1
        }.thenBy { it.modelId })

        val windows = sorted.map { bucket ->
            UsageWindow(
                label = bucket.modelId,
                utilization = 1.0 - bucket.remainingFraction, // remaining → used
                resetsAt = bucket.resetTime?.let { parseInstant(it) }
            )
        }

        return QuotaInfo(
            service = AiService.GEMINI,
            windows = windows,
            extraUsage = null,
            tier = tier,
            fetchedAt = Instant.now()
        )
    }

    private suspend fun ensureValidToken(credential: Credential.GeminiCredential): Credential.GeminiCredential? {
        if (System.currentTimeMillis() < credential.expiresAtMs - 60_000) {
            return credential
        }
        return refreshToken(credential)
    }

    private suspend fun refreshToken(credential: Credential.GeminiCredential): Credential.GeminiCredential? {
        return try {
            val request = GeminiDto.TokenRefreshRequest(
                refreshToken = credential.refreshToken,
                clientId = credential.oauthClientId,
                clientSecret = credential.oauthClientSecret
            )
            val response = tokenRefreshService.refreshToken(request)
            if (response.isSuccessful) {
                val body = response.body() ?: return null
                val expiresIn = body.expiresIn ?: 3600
                val newCredential = Credential.GeminiCredential(
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken ?: credential.refreshToken,
                    expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L),
                    oauthClientId = credential.oauthClientId,
                    oauthClientSecret = credential.oauthClientSecret
                )
                prefsManager.saveCredential(AiService.GEMINI, newCredential)
                newCredential
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun <T> handleErrorResponse(code: Int): Result<T, AppError> {
        return when (code) {
            401 -> Result.Failure(AppError.AuthError(AiService.GEMINI, isTerminal = false))
            429 -> Result.Failure(AppError.RateLimited)
            503 -> Result.Failure(AppError.ServiceUnavailable)
            else -> Result.Failure(AppError.NetworkError("HTTP $code"))
        }
    }

    private fun parseInstant(isoString: String): Instant? {
        return try {
            Instant.parse(isoString)
        } catch (_: Exception) {
            null
        }
    }
}
