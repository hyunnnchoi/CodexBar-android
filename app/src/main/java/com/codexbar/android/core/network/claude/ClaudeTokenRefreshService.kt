package com.codexbar.android.core.network.claude

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ClaudeTokenRefreshService {

    @FormUrlEncoded
    @POST("v1/oauth/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String = CLAUDE_CLIENT_ID
    ): Response<ClaudeDto.TokenRefreshResponse>

    companion object {
        const val BASE_URL = "https://platform.claude.com/"
        const val CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    }
}
