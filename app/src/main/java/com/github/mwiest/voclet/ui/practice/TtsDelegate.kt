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

    // Non-null when TTS was disabled due to an error (not by user toggle).
    // Drives the error dialog; kept across dismissals so re-tap shows dialog again.
    private var lastError: TtsResult? = null

    // True only after the user tapped the fix action in the error dialog.
    // Causes the next toggle-on to reinitialize the TTS engine.
    private var pendingReinit = false

    private val _errorToShow = MutableStateFlow<TtsResult?>(null)
    val errorToShow: StateFlow<TtsResult?> = _errorToShow.asStateFlow()

    fun initialize(languages: Set<String>) {
        ttsManager.initialize { result ->
            scope.launch {
                when (result) {
                    is TtsResult.Success -> ttsManager.preLoadLanguages(languages)
                    is TtsResult.EngineNotInstalled -> disableWithError(result)
                    else -> {}
                }
            }
        }
    }

    fun toggle() {
        if (_isTtsEnabled.value) {
            // User disabling: forget any previous error and pending reinit
            lastError = null
            pendingReinit = false
            _isTtsEnabled.update { false }
        } else {
            val error = lastError
            if (error != null) {
                // Disabled by error — show dialog instead of re-enabling
                _errorToShow.update { error }
            } else {
                // Re-enable; reinitialize engine only if user went through the fix flow
                _isTtsEnabled.update { true }
                if (pendingReinit) {
                    pendingReinit = false
                    ttsManager.reinitialize { result ->
                        scope.launch {
                            if (result is TtsResult.EngineNotInstalled) disableWithError(result)
                        }
                    }
                }
            }
        }
    }

    /** Called when user taps "Close" in the error dialog. Error is remembered so next toggle shows it again. */
    fun dismissError() {
        _errorToShow.update { null }
    }

    /** Called when user taps the action button (e.g. "Open Settings"). Clears the error so next toggle re-enables and reinitializes. */
    fun onFixStarted() {
        lastError = null
        pendingReinit = true
        _errorToShow.update { null }
    }

    fun speak(text: String, languageCode: String) {
        if (!_isTtsEnabled.value) return
        speakWithRetry(text, languageCode)
    }

    private fun speakWithRetry(text: String, languageCode: String, retryCount: Int = 0) {
        scope.launch {
            when (val result = ttsManager.speak(text, languageCode)) {
                TtsResult.Success -> {}
                TtsResult.Initializing -> {
                    if (retryCount < 3) {
                        delay(200)
                        speakWithRetry(text, languageCode, retryCount + 1)
                    }
                }
                is TtsResult.EngineNotInstalled,
                is TtsResult.LanguageMissing,
                is TtsResult.LanguageNotSupported -> disableWithError(result)
            }
        }
    }

    private fun disableWithError(error: TtsResult) {
        lastError = error
        _isTtsEnabled.update { false }
    }
}
