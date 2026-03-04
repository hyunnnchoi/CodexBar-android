package com.codexbar.android.core.network.codex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object CodexDto {

    @Serializable
    data class UsageResponse(
        @SerialName("plan_type") val planType: String? = null,
        @SerialName("rate_limit") val rateLimit: RateLimit? = null,
        val credits: Credits? = null
    )

    @Serializable
    data class RateLimit(
        @SerialName("primary_window") val primaryWindow: RateLimitWindow? = null,
        @SerialName("secondary_window") val secondaryWindow: RateLimitWindow? = null
    )

    @Serializable
    data class RateLimitWindow(
        @SerialName("used_percent") val usedPercent: Double = 0.0,
        @SerialName("reset_at") val resetAt: Long? = null, // Unix timestamp
        @SerialName("limit_window_seconds") val limitWindowSeconds: Long? = null
    )

    @Serializable
    data class Credits(
        @SerialName("has_credits") val hasCredits: Boolean = false,
        val unlimited: Boolean = false,
        val balance: JsonElement? = null // Can be Double or String
    )

    @Serializable
    data class TokenRefreshRequest(
        @SerialName("client_id") val clientId: String = CODEX_CLIENT_ID,
        @SerialName("grant_type") val grantType: String = "refresh_token",
        @SerialName("refresh_token") val refreshToken: String,
        val scope: String = "openid profile email"
    )

    @Serializable
    data class TokenRefreshResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("token_type") val tokenType: String? = null
    )

    @Serializable
    data class TokenErrorResponse(
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"

    val TERMINAL_ERROR_CODES = setOf(
        "refresh_token_expired",
        "refresh_token_reused",
        "refresh_token_invalidated"
    )
}
