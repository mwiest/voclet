package com.github.mwiest.voclet.data.di

import android.content.Context
import com.github.mwiest.voclet.data.database.VocletDatabase
import com.github.mwiest.voclet.data.database.WordListDao
import com.github.mwiest.voclet.data.database.WordPairDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): VocletDatabase {
        return VocletDatabase.getDatabase(context)
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
}