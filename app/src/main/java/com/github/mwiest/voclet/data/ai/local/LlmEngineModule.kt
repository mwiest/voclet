package com.github.mwiest.voclet.data.ai.local

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmEngineModule {

    @Binds
    @Singleton
    abstract fun bindLlmEngine(impl: LlamaLlmEngine): LlmEngine
}
