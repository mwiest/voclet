package com.github.mwiest.voclet.ui.wordlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.ai.GeminiService
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.ui.utils.Language
import com.github.mwiest.voclet.ui.utils.isoToLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordListDetailUiState(
    val listName: String = "",
    val language1: Language? = null,
    val language2: Language? = null,
    val wordPairs: List<WordPair> = emptyList(),
    val isNewList: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val translationSuggestions: Map<Long, TranslationSuggestion?> = emptyMap(),
    val loadingSuggestions: Set<Long> = emptySet()
)

@HiltViewModel
class WordListDetailViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val geminiService: GeminiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val wordListId: Long = savedStateHandle.get<String>("wordListId")?.toLongOrNull() ?: -1

    private val _uiState = MutableStateFlow(WordListDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val deletedWordPairs = mutableListOf<WordPair>()
    private var originalWordList: WordList? = null
    private var originalListName: String = ""
    private var originalLanguage1: Language? = null
    private var originalLanguage2: Language? = null
    private var originalWordPairs: List<WordPair> = emptyList()

    // Cache for translation suggestions to prevent duplicate API calls
    private val suggestionCache = mutableMapOf<String, TranslationSuggestion>()
    private val inFlightRequests = mutableSetOf<String>()

    private fun getCacheKey(word: String, fromLang: String, toLang: String): String {
        return "$word|$fromLang|$toLang"
    }

    init {
        viewModelScope.launch {
            if (wordListId != -1L) {
                originalWordList = repository.getWordList(wordListId)
                val wordPairs = repository.getWordPairsForList(wordListId).first()
                originalListName = originalWordList?.name ?: ""
                originalLanguage1 = originalWordList?.language1?.isoToLanguage()
                originalLanguage2 = originalWordList?.language2?.isoToLanguage()
                originalWordPairs = wordPairs
                _uiState.update {
                    it.copy(
                        listName = originalListName,
                        language1 = originalLanguage1,
                        language2 = originalLanguage2,
                        wordPairs = wordPairs.withEmptyRow(),
                        isNewList = false
                    )
                }
            } else {
                originalListName = ""
                originalLanguage1 = null
                originalLanguage2 = null
                originalWordPairs = emptyList()
                _uiState.update {
                    it.copy(
                        listName = "",
                        language1 = null,
                        language2 = null,
                        wordPairs = emptyList<WordPair>().withEmptyRow(),
                        isNewList = true
                    )
                }
            }
        }
    }

    private fun List<WordPair>.withEmptyRow(): List<WordPair> {
        val lastIsEmtpy = isNotEmpty() && last().let { it.word1.isEmpty() && it.word2.isEmpty() }
        if (lastIsEmtpy) {
            return this
        }
        val newPair = WordPair(
            id = System.currentTimeMillis() * -1,
            wordListId = wordListId,
            word1 = "",
            word2 = ""
        )
        return this + newPair
    }

    private fun hasChanges(
        listName: String,
        language1: Language?,
        language2: Language?,
        wordPairs: List<WordPair>
    ): Boolean {
        if (listName != originalListName) return true
        if (language1 != originalLanguage1) return true
        if (language2 != originalLanguage2) return true

        // Compare word pairs by content only (word1, word2), excluding empty trailing row
        val currentContent = wordPairs.filter { it.word1.isNotEmpty() || it.word2.isNotEmpty() }
        val originalContent =
            originalWordPairs.filter { it.word1.isNotEmpty() || it.word2.isNotEmpty() }

        if (currentContent.size != originalContent.size) return true

        return currentContent.any { current ->
            originalContent.none { original ->
                current.word1 == original.word1 && current.word2 == original.word2
            }
        }
    }

    fun updateWordListName(name: String) {
        _uiState.update { state ->
            val updatedState = state.copy(listName = name)
            updatedState.copy(
                hasUnsavedChanges = hasChanges(
                    updatedState.listName,
                    updatedState.language1,
                    updatedState.language2,
                    updatedState.wordPairs
                )
            )
        }
    }

    fun updateLanguage1(language: Language?) {
        // Clear suggestion cache when language changes
        suggestionCache.clear()
        _uiState.update { state ->
            val updatedState = state.copy(
                language1 = language,
                translationSuggestions = emptyMap(),
                loadingSuggestions = emptySet()
            )
            updatedState.copy(
                hasUnsavedChanges = hasChanges(
                    updatedState.listName,
                    updatedState.language1,
                    updatedState.language2,
                    updatedState.wordPairs
                )
            )
        }
    }

    fun updateLanguage2(language: Language?) {
        // Clear suggestion cache when language changes
        suggestionCache.clear()
        _uiState.update { state ->
            val updatedState = state.copy(
                language2 = language,
                translationSuggestions = emptyMap(),
                loadingSuggestions = emptySet()
            )
            updatedState.copy(
                hasUnsavedChanges = hasChanges(
                    updatedState.listName,
                    updatedState.language1,
                    updatedState.language2,
                    updatedState.wordPairs
                )
            )
        }
    }

    fun updateWordPair(updatedPair: WordPair) {
        _uiState.update { state ->
            val updatedList = state.wordPairs.map {
                if (it.id == updatedPair.id) updatedPair else it
            }
            val updatedState = state.copy(wordPairs = updatedList.withEmptyRow())
            updatedState.copy(
                hasUnsavedChanges = hasChanges(
                    updatedState.listName,
                    updatedState.language1,
                    updatedState.language2,
                    updatedState.wordPairs
                )
            )
        }
    }

    fun deleteWordPair(pair: WordPair) {
        _uiState.update { state ->
            val updatedList = state.wordPairs - pair
            val updatedState = state.copy(wordPairs = updatedList.withEmptyRow())
            updatedState.copy(
                hasUnsavedChanges = hasChanges(
                    updatedState.listName,
                    updatedState.language1,
                    updatedState.language2,
                    updatedState.wordPairs
                )
            )
        }
        if (pair.id > 0) { // Only track deletions of existing pairs
            deletedWordPairs.add(pair)
        }
    }

    fun saveChanges() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val currentState = _uiState.value

                // Check if list is entirely empty (no title and no pairs)
                val hasPairs =
                    currentState.wordPairs.any { it.word1.isNotBlank() || it.word2.isNotBlank() }
                val hasTitle = currentState.listName.isNotBlank()

                if (!hasTitle && !hasPairs) {
                    // Silently discard entirely empty list
                    _uiState.update { it.copy(hasUnsavedChanges = false, isSaving = false) }
                    return@launch
                }

                // If pairs exist, title must not be empty
                if (hasPairs && !hasTitle) {
                    _uiState.update { it.copy(isSaving = false) }
                    return@launch
                }

                val listIdToSave: Long

                if (currentState.isNewList) {
                    val newList = WordList(
                        name = currentState.listName,
                        language1 = currentState.language1?.code,
                        language2 = currentState.language2?.code
                    )
                    listIdToSave = repository.insertWordList(newList)
                } else {
                    listIdToSave = wordListId
                    originalWordList?.let {
                        val updatedList = it.copy(
                            name = currentState.listName,
                            language1 = currentState.language1?.code,
                            language2 = currentState.language2?.code
                        )
                        repository.updateWordList(updatedList)
                    }
                }

                val existingPairIds =
                    if (!currentState.isNewList) repository.getWordPairsForList(listIdToSave)
                        .first()
                        .map { it.id }.toSet() else emptySet()

                currentState.wordPairs.forEach { pair ->
                    if (pair.word1.isNotBlank() || pair.word2.isNotBlank()) { // Save if at least one field is not blank
                        if (pair.id <= 0 || !existingPairIds.contains(pair.id)) { // New pairs (temporary negative IDs or not in DB)
                            repository.insertWordPair(pair.copy(id = 0, wordListId = listIdToSave))
                        } else { // Existing pairs
                            repository.updateWordPair(pair.copy(wordListId = listIdToSave))
                        }
                    }
                }

                deletedWordPairs.forEach { repository.deleteWordPair(it) }

                // Update original state and clear unsaved changes flag after successful save
                val savedState = _uiState.value
                originalListName = savedState.listName
                originalWordPairs =
                    savedState.wordPairs.filter { it.word1.isNotEmpty() || it.word2.isNotEmpty() }
                deletedWordPairs.clear()

                _uiState.update { it.copy(hasUnsavedChanges = false, isSaving = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                throw e
            }
        }
    }

    fun resetToOriginal() {
        deletedWordPairs.clear()
        suggestionCache.clear()
        inFlightRequests.clear()
        _uiState.update {
            it.copy(
                listName = originalListName,
                language1 = originalLanguage1,
                language2 = originalLanguage2,
                wordPairs = originalWordPairs.withEmptyRow(),
                hasUnsavedChanges = false,
                translationSuggestions = emptyMap(),
                loadingSuggestions = emptySet()
            )
        }
    }

    fun deleteWordList() {
        viewModelScope.launch {
            if (wordListId != -1L) {
                repository.deleteWordList(originalWordList ?: return@launch)
            }
        }
    }

    fun fetchTranslationSuggestions(wordPairId: Long, word1: String) {

        // Early exits - silent failures per requirements
        val currentState = _uiState.value

        // Skip if word1 is empty
        if (word1.isBlank()) {
            return
        }

        // Skip if languages not selected (silent)
        val lang1 = currentState.language1?.code
        val lang2 = currentState.language2?.code


        if (lang1 == null || lang2 == null) {
            return
        }

        // Check cache first
        val cacheKey = getCacheKey(word1, lang1, lang2)
        suggestionCache[cacheKey]?.let { cachedSuggestion ->
            _uiState.update { state ->
                state.copy(
                    translationSuggestions = state.translationSuggestions + (wordPairId to cachedSuggestion)
                )
            }
            return
        }

        // Skip if already loading this exact request
        if (inFlightRequests.contains(cacheKey)) {
            return
        }

        android.util.Log.d("WordList", "Starting API call for: $word1 ($lang1 -> $lang2)")

        // Mark as loading
        inFlightRequests.add(cacheKey)
        _uiState.update { state ->
            state.copy(loadingSuggestions = state.loadingSuggestions + wordPairId)
        }

        viewModelScope.launch {
            try {
                val result = geminiService.suggestTranslation(
                    word = word1,
                    fromLanguage = lang1,
                    toLanguage = lang2
                )

                result.fold(
                    onSuccess = { suggestion ->
                        android.util.Log.d(
                            "WordList",
                            "API success - primary: ${suggestion.primaryTranslation}, alternatives: ${suggestion.alternatives.size}"
                        )

                        // Cache the successful result
                        suggestionCache[cacheKey] = suggestion

                        _uiState.update { state ->
                            state.copy(
                                translationSuggestions = state.translationSuggestions + (wordPairId to suggestion),
                                loadingSuggestions = state.loadingSuggestions - wordPairId
                            )
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("WordList", "API failure: ${error.message}", error)

                        // Silent failure per requirements - just remove loading state
                        _uiState.update { state ->
                            state.copy(loadingSuggestions = state.loadingSuggestions - wordPairId)
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("WordList", "Exception during API call: ${e.message}", e)

                // Silent failure - remove loading state
                _uiState.update { state ->
                    state.copy(loadingSuggestions = state.loadingSuggestions - wordPairId)
                }
            } finally {
                inFlightRequests.remove(cacheKey)
            }
        }
    }

    fun applySuggestion(wordPairId: Long, suggestion: String) {
        _uiState.update { state ->
            val updatedPairs = state.wordPairs.map { pair ->
                if (pair.id == wordPairId) {
                    pair.copy(word2 = suggestion)
                } else {
                    pair
                }
            }

            val updatedState = state.copy(
                wordPairs = updatedPairs.withEmptyRow(),
                // Clear suggestions for this pair after applying
                translationSuggestions = state.translationSuggestions - wordPairId
            )

            updatedState.copy(
                hasUnsavedChanges = hasChanges(
                    updatedState.listName,
                    updatedState.language1,
                    updatedState.language2,
                    updatedState.wordPairs
                )
            )
        }
    }

    fun clearSuggestions(wordPairId: Long) {
        _uiState.update { state ->
            state.copy(translationSuggestions = state.translationSuggestions - wordPairId)
        }
    }
}
