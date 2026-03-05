package com.codexbar.android.core.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.gemini.GeminiApiService
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class GeminiRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: GeminiApiService
    private lateinit var tokenRefreshService: GeminiTokenRefreshService
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: GeminiRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val testCredential = Credential.GeminiCredential(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        expiresAtMs = System.currentTimeMillis() + 3600_000,
        oauthClientId = "test-client-id",
        oauthClientSecret = "test-client-secret"
    )

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = OkHttpClient.Builder().build()
        val contentType = "application/json".toMediaType()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GeminiApiService::class.java)

        tokenRefreshService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GeminiTokenRefreshService::class.java)

        prefsManager = mock(EncryptedPrefsManager::class.java)
        runBlocking { `when`(prefsManager.loadCredential(AiService.GEMINI)).thenReturn(testCredential) }

        repository = GeminiRepositoryImpl(apiService, tokenRefreshService, prefsManager)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchQuota with string projectId`() = runTest {
        val loadResponse = """
        {
            "cloudaicompanionProject": "my-project-123",
            "currentTier": { "id": "free-tier" }
        }
        """.trimIndent()

        val quotaResponse = """
        {
            "buckets": [
                { "modelId": "gemini-2.0-flash", "remainingFraction": 0.73, "resetTime": "2025-06-01T12:00:00Z" },
                { "modelId": "gemini-2.0-pro", "remainingFraction": 0.50, "resetTime": "2025-06-01T12:00:00Z" }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(loadResponse))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(quotaResponse))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(AiService.GEMINI, quotaInfo.service)
        assertEquals("Free", quotaInfo.tier)
        assertEquals(2, quotaInfo.windows.size)
        // Pro first, then Flash
        assertEquals("Pro", quotaInfo.windows[0].label)
        assertEquals(0.50, quotaInfo.windows[0].utilization, 0.01)
        assertEquals("Flash", quotaInfo.windows[1].label)
        assertEquals(0.27, quotaInfo.windows[1].utilization, 0.01)
    }

    @Test
    fun `fetchQuota with object projectId`() = runTest {
        val loadResponse = """
        {
            "cloudaicompanionProject": { "id": "my-project-456" },
            "currentTier": { "id": "standard-tier" }
        }
        """.trimIndent()

        val quotaResponse = """
        {
            "buckets": [
                { "modelId": "gemini-2.0-flash", "remainingFraction": 0.5 }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(loadResponse))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(quotaResponse))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals("Paid", quotaInfo.tier)
    }

    @Test
    fun `fetchQuota deduplicates buckets by modelId`() = runTest {
        val loadResponse = """
        {
            "cloudaicompanionProject": "proj",
            "currentTier": { "id": "free-tier" }
        }
        """.trimIndent()

        val quotaResponse = """
        {
            "buckets": [
                { "modelId": "gemini-2.0-flash", "remainingFraction": 0.80 },
                { "modelId": "gemini-2.0-flash", "remainingFraction": 0.30 },
                { "modelId": "gemini-2.0-pro", "remainingFraction": 0.60 }
            ]
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(loadResponse))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(quotaResponse))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(2, quotaInfo.windows.size)
        // Flash group: max(1-0.80, 1-0.30) = max(0.20, 0.70) = 0.70
        val flashWindow = quotaInfo.windows.first { it.label == "Flash" }
        assertEquals(0.70, flashWindow.utilization, 0.01)
        // Pro group: max(1-0.60) = 0.40
        val proWindow = quotaInfo.windows.first { it.label == "Pro" }
        assertEquals(0.40, proWindow.utilization, 0.01)
    }

    @Test
    fun `fetchQuota returns AuthError on 401`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.AuthError)
    }

    @Test
    fun `fetchQuota returns RateLimited on 429`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(429))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.RateLimited)
    }

    @Test
    fun `fetchQuota returns CredentialNotFound when no credential`() = runTest {
        `when`(prefsManager.loadCredential(AiService.GEMINI)).thenReturn(null)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }
}
