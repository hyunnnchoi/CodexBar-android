package com.yourname.aiquota.di

import android.content.Context
import com.yourname.aiquota.core.security.EncryptedPrefsManager
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
        @ApplicationContext context: Context
    ): EncryptedPrefsManager = EncryptedPrefsManager(context)
}
