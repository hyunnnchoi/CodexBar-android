package com.yourname.aiquota.core.network.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ClaudeDto {

    @Serializable
    data class OAuthUsageResponse(
        @SerialName("five_hour") val fiveHour: OAuthUsageWindow? = null,
        @SerialName("seven_day") val sevenDay: OAuthUsageWindow? = null,
        @SerialName("seven_day_oauth_apps") val sevenDayOauthApps: OAuthUsageWindow? = null,
        @SerialName("seven_day_opus") val sevenDayOpus: OAuthUsageWindow? = null,
        @SerialName("seven_day_sonnet") val sevenDaySonnet: OAuthUsageWindow? = null,
        @SerialName("iguana_necktie") val iguanaNecktie: OAuthUsageWindow? = null,
        @SerialName("extra_usage") val extraUsage: OAuthExtraUsage? = null
    )

    @Serializable
    data class OAuthUsageWindow(
        val utilization: Double? = null,
        @SerialName("resets_at") val resetsAt: String? = null
    )

    @Serializable
    data class OAuthExtraUsage(
        @SerialName("is_enabled") val isEnabled: Boolean = false,
        @SerialName("monthly_limit") val monthlyLimit: Double = 0.0,
        @SerialName("used_credits") val usedCredits: Double = 0.0,
        val utilization: Double = 0.0,
        val currency: String = "USD"
    )

    @Serializable
    data class TokenRefreshResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("token_type") val tokenType: String? = null
    )
}
