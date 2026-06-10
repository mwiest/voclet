package com.github.mwiest.voclet.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.PracticeType
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SpellItSubmission {
    data class Correct(val canonical: String) : SpellItSubmission
    data class Wrong(val diff: List<DiffOp>, val canonical: String) : SpellItSubmission
}

data class SpellItUiState(
    val isLoading: Boolean = true,
    val sessionInitialized: Boolean = false,
    val wordPairs: List<WordPair> = emptyList(),
    val languageMap: Map<Long, String> = emptyMap(),
    val currentIndex: Int = 0,
    val userInput: String = "",
    val submission: SpellItSubmission? = null,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val practiceComplete: Boolean = false,
) {
    val currentPair: WordPair? get() = wordPairs.getOrNull(currentIndex)
    val canSubmit: Boolean get() = submission == null
}

@HiltViewModel
class SpellItPracticeViewModel @Inject constructor(
    private val repository: VocletRepository,
    ttsManager: TtsManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val ttsDelegate = TtsDelegate(ttsManager, viewModelScope)
    private var ttsDefaultApplied = false
    private var autoAdvanceJob: Job? = null

    private val _uiState = MutableStateFlow(SpellItUiState())
    val uiState = _uiState.asStateFlow()

    fun initializeSession() {
        if (_uiState.value.sessionInitialized) return

        viewModelScope.launch {
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            val settings = repository.getSettings().filterNotNull().first()

            val wordLists = repository.getWordListsByIds(selectedListIds)
            val overrides = settings.ttsLanguageOverrides
            val languageMap = wordLists.associate {
                val base = it.language2 ?: "en"
                it.id to (overrides[base] ?: base)
            }

            ttsDelegate.initialize(languageMap.values.toSet())
            if (!ttsDefaultApplied) {
                ttsDefaultApplied = true
                if (!settings.ttsEnabledByDefault) ttsDelegate.toggle()
            }

            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                "hard" -> repository.getWordPairsForListsHardOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }.shuffled()

            _uiState.update {
                it.copy(
                    wordPairs = wordPairs,
                    languageMap = languageMap,
                    isLoading = false,
                    sessionInitialized = true
                )
            }
        }
    }

    fun onInputChange(input: String) {
        if (_uiState.value.submission != null) return
        _uiState.update { it.copy(userInput = input) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.submission != null) return
        val pair = state.currentPair ?: return
        val input = state.userInput

        val result = SpellItMatcher.matches(pair.word2, input)

        viewModelScope.launch {
            repository.recordPracticeResult(pair.id, result.isCorrect, PracticeType.SPELL_IT)
        }

        // Speak the canonical (original) word2.
        val languageCode = state.languageMap[pair.wordListId] ?: "en"
        ttsDelegate.speak(pair.word2, languageCode)

        if (result.isCorrect) {
            _uiState.update {
                it.copy(
                    submission = SpellItSubmission.Correct(result.canonical),
                    correctCount = it.correctCount + 1
                )
            }
            scheduleAutoAdvance()
        } else {
            val ops = SpellItDiff.diff(result.matchedCandidate, input)
            _uiState.update {
                it.copy(
                    submission = SpellItSubmission.Wrong(ops, result.canonical),
                    incorrectCount = it.incorrectCount + 1
                )
            }
        }
    }

    fun skip() {
        val state = _uiState.value
        if (state.submission != null) return
        val pair = state.currentPair ?: return

        viewModelScope.launch {
            repository.recordPracticeResult(pair.id, false, PracticeType.SPELL_IT)
        }

        val languageCode = state.languageMap[pair.wordListId] ?: "en"
        ttsDelegate.speak(pair.word2, languageCode)

        // Diff against the full word2 — every char missing.
        val ops = SpellItDiff.diff(pair.word2, "")
        _uiState.update {
            it.copy(
                submission = SpellItSubmission.Wrong(ops, pair.word2),
                incorrectCount = it.incorrectCount + 1
            )
        }
    }

    fun next() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.wordPairs.size) {
            _uiState.update { it.copy(practiceComplete = true) }
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    userInput = "",
                    submission = null
                )
            }
        }
    }

    private fun scheduleAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = viewModelScope.launch {
            delay(AUTO_ADVANCE_DELAY_MS)
            next()
        }
    }

    fun resetPractice() {
        autoAdvanceJob?.cancel()
        viewModelScope.launch {
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()
            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"
            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                "hard" -> repository.getWordPairsForListsHardOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }.shuffled()

            _uiState.update {
                it.copy(
                    wordPairs = wordPairs,
                    currentIndex = 0,
                    userInput = "",
                    submission = null,
                    correctCount = 0,
                    incorrectCount = 0,
                    practiceComplete = false
                )
            }
        }
    }

    companion object {
        private const val AUTO_ADVANCE_DELAY_MS = 1500L
    }
}
