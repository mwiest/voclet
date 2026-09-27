package com.github.mwiest.voclet.data.di

import android.content.Context
import com.github.mwiest.voclet.data.ai.cloud.CloudApiKeyStore
import com.github.mwiest.voclet.data.database.AppSettingsDao
import com.github.mwiest.voclet.data.database.PracticeResultDao
import com.github.mwiest.voclet.data.database.VocletDatabase
import com.github.mwiest.voclet.data.database.WordListDao
import com.github.mwiest.voclet.data.database.WordPairDao
import com.github.mwiest.voclet.data.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideCloudApiKeyStore(@ApplicationContext context: Context): CloudApiKeyStore {
        return CloudApiKeyStore(File(context.noBackupFilesDir, CloudApiKeyStore.FILE_NAME))
    }

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyStore: CloudApiKeyStore,
    ): VocletDatabase {
        return VocletDatabase.getDatabase(context, keyStore)
    }

    @Singleton
    @Provides
    fun provideWordListDao(database: VocletDatabase): WordListDao {
        return database.wordListDao()
    }

    @Singleton
    @Provides
    fun provideWordPairDao(database: VocletDatabase): WordPairDao {
        return database.wordPairDao()
    }

    @Singleton
    @Provides
    fun providePracticeResultDao(database: VocletDatabase): PracticeResultDao {
        return database.practiceResultDao()
    }

    @Singleton
    @Provides
    fun provideAppSettingsDao(database: VocletDatabase): AppSettingsDao {
        return database.appSettingsDao()
    }

    @Singleton
    @Provides
    fun provideTtsManager(@ApplicationContext context: Context): TtsManager {
        return TtsManager(context)
    }
}