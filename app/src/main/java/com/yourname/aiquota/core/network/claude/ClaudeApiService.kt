package com.yourname.aiquota.core.network.claude

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface ClaudeApiService {

    @Headers("anthropic-beta: oauth-2025-04-20")
    @GET("api/oauth/usage")
    suspend fun getUsage(
        @Header("Authorization") authorization: String
    ): Response<ClaudeDto.OAuthUsageResponse>
}
