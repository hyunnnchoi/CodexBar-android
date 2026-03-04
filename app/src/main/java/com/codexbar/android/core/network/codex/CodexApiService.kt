package com.codexbar.android.core.network.codex

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface CodexApiService {

    @GET("backend-api/wham/usage")
    suspend fun getUsage(
        @Header("Authorization") authorization: String,
        @Header("ChatGPT-Account-Id") accountId: String? = null,
        @Header("User-Agent") userAgent: String = "CodexBar-Android"
    ): Response<CodexDto.UsageResponse>
}
