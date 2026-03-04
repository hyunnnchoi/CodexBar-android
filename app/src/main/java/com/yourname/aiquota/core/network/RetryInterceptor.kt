package com.yourname.aiquota.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val maxWaitSeconds: Long = 60
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0

        while (response.code == 429 && attempt < maxRetries) {
            response.close()
            attempt++

            val retryAfter = response.header("Retry-After")?.toLongOrNull()
            val waitMs = if (retryAfter != null) {
                min(retryAfter * 1000, maxWaitSeconds * 1000)
            } else {
                // Exponential backoff: 1s, 2s, 4s...
                val backoff = 2.0.pow(attempt - 1).toLong() * 1000
                min(backoff, maxWaitSeconds * 1000)
            }

            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                throw IOException("Retry interrupted")
            }

            response = chain.proceed(request)
        }

        return response
    }
}
