package com.yourname.aiquota.core.network.gemini

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface GeminiTokenRefreshService {

    @POST("token")
    suspend fun refreshToken(
        @Body request: GeminiDto.TokenRefreshRequest
    ): Response<GeminiDto.TokenRefreshResponse>

    companion object {
        const val BASE_URL = "https://oauth2.googleapis.com/"
    }
}
