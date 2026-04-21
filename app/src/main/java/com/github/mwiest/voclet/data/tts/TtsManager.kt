package com.github.mwiest.voclet.data.tts

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
 * Call initialize() once to start the engine. Call reinitialize() after the
 * user installs a TTS engine so a fresh TextToSpeech instance is created.
 */
class TtsManager(private val context: Context) {
    private sealed class State {
        object NotInitialized : State()
        object Initializing : State()
        object Ready : State()
        object Failed : State()
    }

    @Volatile private var state: State = State.NotInitialized
    @Volatile private var ttsInstance: TextToSpeech? = null

    private var onInitComplete: ((TtsResult) -> Unit)? = null

    /**
     * Initialize TTS engine if not yet started.
     * If already Ready, calls onInitComplete immediately (useful for language pre-loading).
     * If already Failed, calls onInitComplete with the error immediately.
     */
    @Synchronized
    fun initialize(onInitComplete: (TtsResult) -> Unit = {}) {
        when (state) {
            State.NotInitialized -> {
                this.onInitComplete = onInitComplete
                createInstance()
            }
            State.Initializing -> this.onInitComplete = onInitComplete
            State.Ready -> onInitComplete(TtsResult.Success)
            State.Failed -> onInitComplete(TtsResult.EngineNotInstalled(createTtsSettingsIntent()))
        }
    }

    /**
     * Shuts down the current TTS engine and creates a new one.
     * Use after the user installs a TTS engine or language pack.
     */
    @Synchronized
    fun reinitialize(onInitComplete: (TtsResult) -> Unit = {}) {
        ttsInstance?.shutdown()
        ttsInstance = null
        state = State.NotInitialized
        this.onInitComplete = onInitComplete
        createInstance()
    }

    /** Speak text in the specified language. Thread-safe. */
    @Synchronized
    fun speak(text: String, languageCode: String): TtsResult {
        val tts = ttsInstance
        if (state != State.Ready || tts == null) {
            return when (state) {
                State.NotInitialized, State.Initializing -> TtsResult.Initializing
                State.Failed -> TtsResult.EngineNotInstalled(createTtsSettingsIntent())
                State.Ready -> TtsResult.Success // unreachable
            }
        }

        val locale = Locale.forLanguageTag(languageCode)
        return when (tts.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA -> TtsResult.LanguageMissing(createInstallTtsDataIntent())
            TextToSpeech.LANG_NOT_SUPPORTED -> TtsResult.LanguageNotSupported(createTtsSettingsIntent())
            else -> {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vclt_$text")
                TtsResult.Success
            }
        }
    }

    /**
     * Pre-loads languages by speaking an empty string in each to warm up voice synthesis.
     * Should only be called after TTS is confirmed ready.
     */
    suspend fun preLoadLanguages(languages: Set<String>) {
        languages.forEach { languageCode ->
            speak("", languageCode)
            delay(100)
        }
    }

    private fun createInstance() {
        state = State.Initializing
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                state = State.Ready
                onInitComplete?.invoke(TtsResult.Success)
            } else {
                state = State.Failed
                onInitComplete?.invoke(TtsResult.EngineNotInstalled(createTtsSettingsIntent()))
            }
            onInitComplete = null
        }
    }

    fun getDefaultEngineName(): String? {
        val pkg = Settings.Secure.getString(context.contentResolver, "tts_default_engine")
            ?: return null
        return try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) { null }
    }

    private fun createInstallTtsDataIntent() = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

    private fun createTtsSettingsIntent() = Intent("com.android.settings.TTS_SETTINGS").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}
