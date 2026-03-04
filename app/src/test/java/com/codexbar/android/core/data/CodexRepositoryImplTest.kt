package com.codexbar.android.core.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
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
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class CodexRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: CodexApiService
    private lateinit var tokenRefreshService: CodexTokenRefreshService
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: CodexRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val testCredential = Credential.CodexCredential(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        accountId = "test-account-id"
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
            .create(CodexApiService::class.java)

        tokenRefreshService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(CodexTokenRefreshService::class.java)

        prefsManager = mock(EncryptedPrefsManager::class.java)
        `when`(prefsManager.loadCredential(AiService.CODEX)).thenReturn(testCredential)

        repository = CodexRepositoryImpl(apiService, tokenRefreshService, prefsManager)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchQuota returns success with rate limit windows`() = runTest {
        val responseJson = """
        {
            "plan_type": "pro",
            "rate_limit": {
                "primary_window": {
                    "used_percent": 45,
                    "reset_at": 1234567890,
                    "limit_window_seconds": 18000
                },
                "secondary_window": {
                    "used_percent": 20,
                    "reset_at": 1234567890,
                    "limit_window_seconds": 604800
                }
            },
            "credits": {
                "has_credits": true,
                "unlimited": false,
                "balance": 8.50
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(AiService.CODEX, quotaInfo.service)
        assertEquals(2, quotaInfo.windows.size)
        assertEquals("5-Hour", quotaInfo.windows[0].label)
        assertEquals(0.45, quotaInfo.windows[0].utilization, 0.001)
        assertEquals("7-Day", quotaInfo.windows[1].label)
        assertEquals(0.20, quotaInfo.windows[1].utilization, 0.001)
        assertEquals("Pro", quotaInfo.tier)
    }

    @Test
    fun `fetchQuota returns AuthError on 401`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError)
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
        `when`(prefsManager.loadCredential(AiService.CODEX)).thenReturn(null)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `parseBalance handles Double`() {
        val element = kotlinx.serialization.json.JsonPrimitive(8.50)
        val balance = CodexRepositoryImpl.parseBalance(element)
        assertEquals(8.50, balance!!, 0.001)
    }

    @Test
    fun `parseBalance handles String`() {
        val element = kotlinx.serialization.json.JsonPrimitive("12.75")
        val balance = CodexRepositoryImpl.parseBalance(element)
        assertEquals(12.75, balance!!, 0.001)
    }

    @Test
    fun `parseBalance returns null for null`() {
        val balance = CodexRepositoryImpl.parseBalance(null)
        assertTrue(balance == null)
    }

    @Test
    fun `custom window label for non-standard seconds`() = runTest {
        val responseJson = """
        {
            "plan_type": "team",
            "rate_limit": {
                "primary_window": {
                    "used_percent": 30,
                    "reset_at": 1234567890,
                    "limit_window_seconds": 3600
                }
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals("1h", quotaInfo.windows[0].label)
    }
}
