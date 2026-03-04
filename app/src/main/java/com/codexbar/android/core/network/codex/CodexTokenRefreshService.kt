package com.codexbar.android.core.network.codex

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface CodexTokenRefreshService {

    @POST("oauth/token")
    suspend fun refreshToken(
        @Body request: CodexDto.TokenRefreshRequest
    ): Response<CodexDto.TokenRefreshResponse>

    companion object {
        const val BASE_URL = "https://auth.openai.com/"
    }
}
