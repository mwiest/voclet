package com.github.mwiest.voclet.ui.practice

import com.github.mwiest.voclet.data.tts.TtsManager
import com.github.mwiest.voclet.data.tts.TtsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TtsDelegate(
    private val ttsManager: TtsManager,
    private val scope: CoroutineScope
) {
    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    fun initialize(languages: Set<String>) {
        ttsManager.initialize { result ->
            scope.launch {
                when (result) {
                    is TtsResult.Success -> ttsManager.preLoadLanguages(languages)
                    is TtsResult.EngineNotInstalled -> _isTtsEnabled.update { false }
                    else -> {}
                }
            }
        }
    }

    fun toggle() = _isTtsEnabled.update { !it }

    fun speak(text: String, languageCode: String) {
        if (!_isTtsEnabled.value) return
        speakWithRetry(text, languageCode)
    }

    private fun speakWithRetry(text: String, languageCode: String, retryCount: Int = 0) {
        scope.launch {
            when (ttsManager.speak(text, languageCode)) {
                TtsResult.Success -> {}
                TtsResult.Initializing -> {
                    if (retryCount < 3) {
                        delay(200)
                        speakWithRetry(text, languageCode, retryCount + 1)
                    }
                }
                is TtsResult.EngineNotInstalled,
                is TtsResult.LanguageMissing,
                is TtsResult.LanguageNotSupported -> _isTtsEnabled.update { false }
            }
        }
    }
}
