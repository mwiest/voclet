package com.github.mwiest.voclet.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.ai
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
    fun provideFirebaseAI(): FirebaseAI {
        return Firebase.ai
    }

    @Singleton
    @Provides
    fun provideGeminiService(
        ai: FirebaseAI
    ): GeminiService {
        return GeminiServiceImpl(ai)
    }
}
