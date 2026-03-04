package com.codexbar.android.core.domain.model

import java.time.Instant

sealed class Credential {
    abstract val accessToken: String
    abstract val refreshToken: String?

    data class ClaudeCredential(
        override val accessToken: String,
        override val refreshToken: String?,
        val expiresAt: Instant? = null,
        val scopes: String? = null,
        val rateLimitTier: String? = null
    ) : Credential()

    data class CodexCredential(
        override val accessToken: String,
        override val refreshToken: String,
        val accountId: String? = null
    ) : Credential()

    data class GeminiCredential(
        override val accessToken: String,
        override val refreshToken: String,
        val expiresAtMs: Long,
        val oauthClientId: String,
        val oauthClientSecret: String
    ) : Credential()
}
