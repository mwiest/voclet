package com.github.mwiest.voclet.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.PracticeType
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.tts.TtsManager
import com.github.mwiest.voclet.data.tts.TtsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashcardPracticeUiState(
    val currentCardIndex: Int = 0,
    val wordPairs: List<WordPair> = emptyList(),
    val isFlipped: Boolean = false,
    val isLoading: Boolean = true,
    val practiceComplete: Boolean = false,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isTtsEnabled: Boolean = true,
    val languageMap: Map<Long, String> = emptyMap() // wordListId -> language2 code
)

@HiltViewModel
class FlashcardPracticeViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val ttsManager: TtsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashcardPracticeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Extract route params
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            // Load word lists to get language codes
            val wordLists = repository.getWordListsByIds(selectedListIds)
            val languageMap = wordLists.associate { it.id to (it.language2 ?: "en") }

            // Initialize TTS with callback
            ttsManager.initialize { readyResult ->
                viewModelScope.launch {
                    when (readyResult) {
                        is TtsResult.Success -> {
                            // Pre-load languages when ready
                            ttsManager.preLoadLanguages(languageMap.values.toSet())
                        }

                        is TtsResult.EngineNotInstalled -> {
                            _uiState.update { it.copy(isTtsEnabled = false) }
                        }

                        else -> {
                            // Other states not expected from callback
                        }
                    }
                }
            }

            // Load word pairs based on selected lists and filter
            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                "hard" -> repository.getWordPairsForListsHardOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }

            // Shuffle word pairs
            val shuffledPairs = wordPairs.shuffled()

            _uiState.update { state ->
                state.copy(
                    wordPairs = shuffledPairs,
                    languageMap = languageMap,
                    isLoading = false
                )
            }
        }
    }

    fun flipCard() {
        val currentState = _uiState.value
        val willBeFlipped = !currentState.isFlipped

        _uiState.update { state ->
            state.copy(isFlipped = willBeFlipped)
        }

        // Speak word2 when flipping to the back side
        if (willBeFlipped && currentState.isTtsEnabled) {
            val currentPair = currentState.wordPairs.getOrNull(currentState.currentCardIndex)
            if (currentPair != null) {
                val languageCode = currentState.languageMap[currentPair.wordListId] ?: "en"
                speakWithRetry(currentPair.word2, languageCode)
            }
        }
    }

    private fun speakWithRetry(text: String, languageCode: String, retryCount: Int = 0) {
        viewModelScope.launch {
            when (ttsManager.speak(text, languageCode)) {
                TtsResult.Success -> {
                    // Successfully spoken
                }

                TtsResult.Initializing -> {
                    // TTS still initializing, retry after delay
                    if (retryCount < 3) {
                        delay(200)
                        speakWithRetry(text, languageCode, retryCount + 1)
                    }
                }

                is TtsResult.EngineNotInstalled, is TtsResult.LanguageNotSupported, is TtsResult.LanguageMissing -> {
                    _uiState.update { it.copy(isTtsEnabled = false) }
                }
            }
        }
    }

    fun toggleTts() {
        _uiState.update { state ->
            state.copy(isTtsEnabled = !state.isTtsEnabled)
        }
    }

    fun markCorrect() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.currentCardIndex < currentState.wordPairs.size) {
                val wordPair = currentState.wordPairs[currentState.currentCardIndex]
                repository.recordPracticeResult(wordPair.id, true, PracticeType.FLASHCARD)
            }

            _uiState.update { state ->
                state.copy(
                    correctCount = state.correctCount + 1,
                    isFlipped = false
                )
            }

            moveToNext()
        }
    }

    fun markIncorrect() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.currentCardIndex < currentState.wordPairs.size) {
                val wordPair = currentState.wordPairs[currentState.currentCardIndex]
                repository.recordPracticeResult(wordPair.id, false, PracticeType.FLASHCARD)
            }

            _uiState.update { state ->
                state.copy(
                    incorrectCount = state.incorrectCount + 1,
                    isFlipped = false
                )
            }

            moveToNext()
        }
    }

    private suspend fun moveToNext() {
        // Wait 300ms for result feedback perception
        delay(300)

        _uiState.update { state ->
            val nextIndex = state.currentCardIndex + 1
            if (nextIndex >= state.wordPairs.size) {
                state.copy(practiceComplete = true)
            } else {
                state.copy(currentCardIndex = nextIndex)
            }
        }
    }

    fun resetPractice() {
        _uiState.update { state ->
            state.copy(
                currentCardIndex = 0,
                isFlipped = false,
                practiceComplete = false,
                correctCount = 0,
                incorrectCount = 0,
                wordPairs = state.wordPairs.shuffled()
            )
        }
    }
}
