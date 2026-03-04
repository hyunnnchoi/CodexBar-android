package com.codexbar.android.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.codexbar.android.BuildConfig
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.network.RetryInterceptor
import com.codexbar.android.core.network.claude.ClaudeApiService
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.gemini.GeminiApiService
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeTokenClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexTokenClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiTokenClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun baseOkHttpBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor())

        if (BuildConfig.IS_DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }

        return builder
    }

    // --- Claude ---

    @Provides
    @Singleton
    @ClaudeClient
    fun provideClaudeOkHttpClient(): OkHttpClient = baseOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideClaudeApiService(
        @ClaudeClient client: OkHttpClient,
        json: Json
    ): ClaudeApiService {
        return Retrofit.Builder()
            .baseUrl(AiService.CLAUDE.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClaudeApiService::class.java)
    }

    @Provides
    @Singleton
    @ClaudeTokenClient
    fun provideClaudeTokenOkHttpClient(): OkHttpClient = baseOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideClaudeTokenRefreshService(
        @ClaudeTokenClient client: OkHttpClient,
        json: Json
    ): ClaudeTokenRefreshService {
        return Retrofit.Builder()
            .baseUrl(ClaudeTokenRefreshService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClaudeTokenRefreshService::class.java)
    }

    // --- Codex ---

    @Provides
    @Singleton
    @CodexClient
    fun provideCodexOkHttpClient(): OkHttpClient = baseOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideCodexApiService(
        @CodexClient client: OkHttpClient,
        json: Json
    ): CodexApiService {
        return Retrofit.Builder()
            .baseUrl(AiService.CODEX.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CodexApiService::class.java)
    }

    @Provides
    @Singleton
    @CodexTokenClient
    fun provideCodexTokenOkHttpClient(): OkHttpClient = baseOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideCodexTokenRefreshService(
        @CodexTokenClient client: OkHttpClient,
        json: Json
    ): CodexTokenRefreshService {
        return Retrofit.Builder()
            .baseUrl(CodexTokenRefreshService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CodexTokenRefreshService::class.java)
    }

    // --- Gemini ---

    @Provides
    @Singleton
    @GeminiClient
    fun provideGeminiOkHttpClient(): OkHttpClient = baseOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideGeminiApiService(
        @GeminiClient client: OkHttpClient,
        json: Json
    ): GeminiApiService {
        return Retrofit.Builder()
            .baseUrl(AiService.GEMINI.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }

    @Provides
    @Singleton
    @GeminiTokenClient
    fun provideGeminiTokenOkHttpClient(): OkHttpClient = baseOkHttpBuilder().build()

    @Provides
    @Singleton
    fun provideGeminiTokenRefreshService(
        @GeminiTokenClient client: OkHttpClient,
        json: Json
    ): GeminiTokenRefreshService {
        return Retrofit.Builder()
            .baseUrl(GeminiTokenRefreshService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiTokenRefreshService::class.java)
    }
}
