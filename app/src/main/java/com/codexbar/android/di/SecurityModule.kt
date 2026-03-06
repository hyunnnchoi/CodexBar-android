package com.codexbar.android.di

import android.content.Context
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.ValueEncryption
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideEncryptedPrefsManager(
        @ApplicationContext context: Context,
        valueEncryption: ValueEncryption
    ): EncryptedPrefsManager = EncryptedPrefsManager(context, valueEncryption)
}
