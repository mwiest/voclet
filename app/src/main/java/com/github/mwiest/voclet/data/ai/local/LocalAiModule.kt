package com.github.mwiest.voclet.data.ai.local

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Hilt bindings for the on-device (local) AI feature. [DeviceHardware] is
 * constructor-injected; [FileDownloader] and [ModelRepository] are provided
 * here. [ModelRepository] takes its storage directory explicitly so it stays
 * unit-testable with a temp dir.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalAiModule {

    @Singleton
    @Provides
    fun provideFileDownloader(): FileDownloader = HttpFileDownloader()

    @Singleton
    @Provides
    fun provideModelRepository(
        @ApplicationContext context: Context,
        downloader: FileDownloader,
    ): ModelRepository = ModelRepository(File(context.filesDir, "models"), downloader)
}
