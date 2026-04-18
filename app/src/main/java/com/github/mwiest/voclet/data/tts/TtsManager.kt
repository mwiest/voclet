package com.github.mwiest.voclet.data.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.delay
import java.util.Locale

sealed class TtsResult {
    object Initializing : TtsResult()
    object Success : TtsResult()
    data class LanguageMissing(val installDataIntent: Intent) : TtsResult()
    data class LanguageNotSupported(val settingsIntent: Intent) : TtsResult()
    data class EngineNotInstalled(val settingsIntent: Intent) : TtsResult()
}

/**
 * Manages Text-to-Speech functionality with comprehensive error handling.
 * Thread-safe singleton supporting multiple languages.
 *
 * Usage:
 * ```
 * // 1. Initialize once at app/activity start
 * when (ttsManager.initialize()) {
 *     is TtsResult.EngineNotInstalled -> { /* show error */ }
 *     TtsResult.Initializing -> { /* wait and retry */ }
 *     TtsResult.Success -> { /* ready */ }
 * }
 *
 * // 2. Speak with any language (thread-safe)
 * when (ttsManager.speak("hello", "en")) {
 *     TtsResult.Success -> { /* spoken */ }
 *     is TtsResult.LanguageMissing -> { startActivity(result.installDataIntent) }
 *     is TtsResult.LanguageNotSupported -> { startActivity(result.settingsIntent) }
 * }
 * ```
 *
 * Note: TTS initialization is asynchronous. initialize() may return Initializing
 * on first call. Retry after a short delay or call when actually needed.
 */
class TtsManager(private val context: Context) {
    private sealed class State {
        object NotInitialized : State()
        object Initializing : State()
        object Ready : State()
        object Failed : State()
    }

    @Volatile
    private var state: State = State.NotInitialized

    // Callback invoked when TTS initialization completes
    private var onInitComplete: ((TtsResult) -> Unit)? = null

    private val tts: TextToSpeech by lazy {
        state = State.Initializing
        TextToSpeech(context) { status ->
            // Update state FIRST, before calling callback
            if (status == TextToSpeech.SUCCESS) {
                state = State.Ready
                onInitComplete?.invoke(TtsResult.Success)
            } else {
                state = State.Failed
                onInitComplete?.invoke(TtsResult.EngineNotInstalled(createTtsSettingsIntent()))
            }
            onInitComplete = null // Clear callback after invocation
        }
    }

    /**
     * Initialize TTS engine with optional callback when ready.
     * Calls onInitComplete when initialization completes (e.g. use for language pre-loading).
     */
    fun initialize(onInitComplete: (TtsResult) -> Unit = {}) {
        this@TtsManager.onInitComplete = onInitComplete
        // Trigger lazy initialization
        tts
    }

    /**
     * Speak text in the specified language.
     * Thread-safe - synchronizes language setting and speaking atomically.
     */
    @Synchronized
    fun speak(text: String, languageCode: String): TtsResult {
        // Check if TTS is ready
        if (state != State.Ready) {
            return when (state) {
                State.NotInitialized, State.Initializing -> TtsResult.Initializing
                State.Failed -> TtsResult.EngineNotInstalled(createTtsSettingsIntent())
                State.Ready -> TtsResult.Success // unreachable
            }
        }

        // Set language and speak atomically
        val locale = Locale.forLanguageTag(languageCode)
        return when (tts.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA -> TtsResult.LanguageMissing(createInstallTtsDataIntent())
            TextToSpeech.LANG_NOT_SUPPORTED -> TtsResult.LanguageNotSupported(
                createTtsSettingsIntent()
            )

            else -> {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vclt_$text")
                TtsResult.Success
            }
        }
    }

    /**
     * Pre-loads languages by speaking an empty string in each language
     * to trigger voice synthesis initialization.
     * This eliminates the delay on first real speak() call.
     * Should only be called after TTS is confirmed ready.
     */
    suspend fun preLoadLanguages(languages: Set<String>) {
        languages.forEach { languageCode ->
            // Speak empty string to trigger setLanguage and voice synthesis pipeline
            speak("", languageCode)

            // Small delay between languages to avoid overwhelming TTS engine
            delay(100)
        }
    }

    private fun createInstallTtsDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }

    private fun createTtsSettingsIntent(): Intent {
        return Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
