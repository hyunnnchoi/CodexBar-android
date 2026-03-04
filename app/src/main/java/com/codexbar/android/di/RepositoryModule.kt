package com.codexbar.android.di

import com.codexbar.android.core.data.ClaudeRepositoryImpl
import com.codexbar.android.core.data.CodexRepositoryImpl
import com.codexbar.android.core.data.GeminiRepositoryImpl
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.claude.ClaudeApiService
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.gemini.GeminiApiService
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import com.codexbar.android.core.security.EncryptedPrefsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRepository

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @ClaudeRepository
    fun provideClaudeRepository(
        apiService: ClaudeApiService,
        tokenRefreshService: ClaudeTokenRefreshService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = ClaudeRepositoryImpl(apiService, tokenRefreshService, prefsManager)

    @Provides
    @Singleton
    @CodexRepository
    fun provideCodexRepository(
        apiService: CodexApiService,
        tokenRefreshService: CodexTokenRefreshService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = CodexRepositoryImpl(apiService, tokenRefreshService, prefsManager)

    @Provides
    @Singleton
    @GeminiRepository
    fun provideGeminiRepository(
        apiService: GeminiApiService,
        tokenRefreshService: GeminiTokenRefreshService,
        prefsManager: EncryptedPrefsManager
    ): QuotaRepository = GeminiRepositoryImpl(apiService, tokenRefreshService, prefsManager)
}
