package com.codexbar.android.core.network.gemini

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GeminiApiService {

    @POST("v1internal:loadCodeAssist")
    suspend fun loadCodeAssist(
        @Header("Authorization") authorization: String,
        @Body body: GeminiDto.LoadCodeAssistRequest
    ): Response<GeminiDto.LoadCodeAssistResponse>

    @POST("v1internal:retrieveUserQuota")
    suspend fun retrieveUserQuota(
        @Header("Authorization") authorization: String,
        @Body body: GeminiDto.RetrieveUserQuotaRequest
    ): Response<GeminiDto.QuotaResponse>
}
