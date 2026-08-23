package com.github.mwiest.voclet.data.ai

import com.github.mwiest.voclet.data.database.AppSettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudAiModule {

    @Singleton
    @Provides
    fun provideCloudAiService(
        appSettingsDao: AppSettingsDao
    ): CloudAiService {
        return OpenAiCompatibleService(appSettingsDao)
    }
}
