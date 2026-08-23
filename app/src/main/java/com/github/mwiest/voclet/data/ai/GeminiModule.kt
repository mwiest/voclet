package com.github.mwiest.voclet.data.ai

import com.github.mwiest.voclet.data.database.AppSettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeminiModule {

    @Singleton
    @Provides
    fun provideGeminiService(
        appSettingsDao: AppSettingsDao
    ): GeminiService {
        return OpenAiCompatibleService(appSettingsDao)
    }
}
