package com.yourname.aiquota.core.data

import com.yourname.aiquota.core.domain.model.AiService
import com.yourname.aiquota.core.domain.model.AppError
import com.yourname.aiquota.core.domain.model.Credential
import com.yourname.aiquota.core.domain.model.Result
import com.yourname.aiquota.core.network.claude.ClaudeApiService
import com.yourname.aiquota.core.network.claude.ClaudeTokenRefreshService
import com.yourname.aiquota.core.security.EncryptedPrefsManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit

class ClaudeRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ClaudeApiService
    private lateinit var tokenRefreshService: ClaudeTokenRefreshService
    private lateinit var prefsManager: EncryptedPrefsManager
    private lateinit var repository: ClaudeRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val testCredential = Credential.ClaudeCredential(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token"
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
            .create(ClaudeApiService::class.java)

        tokenRefreshService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ClaudeTokenRefreshService::class.java)

        prefsManager = mock(EncryptedPrefsManager::class.java)
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(testCredential)

        repository = ClaudeRepositoryImpl(apiService, tokenRefreshService, prefsManager)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchQuota returns success with all windows`() = runTest {
        val responseJson = """
        {
            "five_hour": { "utilization": 0.42, "resets_at": "2025-06-01T12:00:00Z" },
            "seven_day": { "utilization": 0.15, "resets_at": "2025-06-07T00:00:00Z" },
            "seven_day_oauth_apps": { "utilization": 0.10 },
            "seven_day_opus": { "utilization": 0.30 },
            "seven_day_sonnet": { "utilization": 0.20 },
            "iguana_necktie": { "utilization": 0.05 },
            "extra_usage": {
                "is_enabled": true,
                "monthly_limit": 50.0,
                "used_credits": 12.5,
                "utilization": 0.25,
                "currency": "USD"
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value

        assertEquals(AiService.CLAUDE, quotaInfo.service)
        assertEquals(6, quotaInfo.windows.size)
        assertEquals("5-Hour", quotaInfo.windows[0].label)
        assertEquals(0.42, quotaInfo.windows[0].utilization, 0.001)
        assertEquals("Extended", quotaInfo.windows[5].label)
        assertNotNull(quotaInfo.extraUsage)
        assertEquals(50.0, quotaInfo.extraUsage!!.monthlyLimit, 0.001)
        assertEquals(12.5, quotaInfo.extraUsage!!.usedCredits, 0.001)
    }

    @Test
    fun `fetchQuota handles response without iguana_necktie`() = runTest {
        val responseJson = """
        {
            "five_hour": { "utilization": 0.42 },
            "seven_day": { "utilization": 0.15 }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertEquals(2, quotaInfo.windows.size)
        assertTrue(quotaInfo.windows.none { it.label == "Extended" })
    }

    @Test
    fun `fetchQuota returns AuthError on 401`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        // Token refresh also fails
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.AuthError)
        assertTrue((error as AppError.AuthError).isTerminal)
    }

    @Test
    fun `fetchQuota returns RateLimited on 429`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(429))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.RateLimited)
    }

    @Test
    fun `fetchQuota returns CredentialNotFound when no credential saved`() = runTest {
        `when`(prefsManager.loadCredential(AiService.CLAUDE)).thenReturn(null)

        val result = repository.fetchQuota()

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is AppError.CredentialNotFound)
    }

    @Test
    fun `fetchQuota extra_usage null when disabled`() = runTest {
        val responseJson = """
        {
            "five_hour": { "utilization": 0.42 },
            "extra_usage": { "is_enabled": false, "monthly_limit": 0, "used_credits": 0, "utilization": 0, "currency": "USD" }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = repository.fetchQuota()

        assertTrue(result is Result.Success)
        val quotaInfo = (result as Result.Success).value
        assertTrue(quotaInfo.extraUsage == null)
    }
}
