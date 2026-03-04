package com.codexbar.android.core.domain.model

sealed class AppError {
    data class NetworkError(val message: String, val cause: Throwable? = null) : AppError()

    data class AuthError(
        val service: AiService,
        val isTerminal: Boolean, // true = re-auth required, false = transient
        val message: String = ""
    ) : AppError()

    data object RateLimited : AppError()

    data class ParseError(val message: String, val cause: Throwable? = null) : AppError()

    data class CredentialNotFound(val service: AiService) : AppError()

    data object ServiceUnavailable : AppError()
}
