package com.codexbar.android.core.network.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object GeminiDto {

    @Serializable
    data class LoadCodeAssistRequest(
        val metadata: Metadata = Metadata()
    )

    @Serializable
    data class Metadata(
        val ideType: String = "GEMINI_CLI",
        val pluginType: String = "GEMINI"
    )

    @Serializable
    data class LoadCodeAssistResponse(
        // Can be String or Object {"id": "..."}
        val cloudaicompanionProject: JsonElement? = null,
        val currentTier: Tier? = null
    )

    @Serializable
    data class Tier(
        val id: String? = null
    )

    @Serializable
    data class RetrieveUserQuotaRequest(
        val project: String = ""
    )

    @Serializable
    data class QuotaResponse(
        val buckets: List<QuotaBucket> = emptyList()
    )

    @Serializable
    data class QuotaBucket(
        val modelId: String = "",
        val remainingFraction: Double = 1.0,
        val resetTime: String? = null,
        val tokenType: String? = null
    )

    @Serializable
    data class TokenRefreshRequest(
        @SerialName("grant_type") val grantType: String = "refresh_token",
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("client_id") val clientId: String,
        @SerialName("client_secret") val clientSecret: String
    )

    @Serializable
    data class TokenRefreshResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("id_token") val idToken: String? = null
    )
}
