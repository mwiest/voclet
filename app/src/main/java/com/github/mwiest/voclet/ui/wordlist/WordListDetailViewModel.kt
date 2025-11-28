package com.github.mwiest.voclet.ui.wordlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordListDetailUiState(
    val listName: String = "",
    val wordPairs: List<WordPair> = emptyList(),
    val isNewList: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false
)

@HiltViewModel
class WordListDetailViewModel @Inject constructor(
    private val repository: VocletRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val wordListId: Long = savedStateHandle.get<String>("wordListId")?.toLongOrNull() ?: -1

    private val _uiState = MutableStateFlow(WordListDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val deletedWordPairs = mutableListOf<WordPair>()
    private var originalWordList: WordList? = null
    private var originalListName: String = ""
    private var originalWordPairs: List<WordPair> = emptyList()

    init {
        viewModelScope.launch {
            if (wordListId != -1L) {
                originalWordList = repository.getWordList(wordListId)
                val wordPairs = repository.getWordPairsForList(wordListId).first()
                originalListName = originalWordList?.name ?: ""
                originalWordPairs = wordPairs
                _uiState.update {
                    it.copy(
                        listName = originalListName,
                        wordPairs = wordPairs.withEmptyRow(),
                        isNewList = false
                    )
                }
            } else {
                originalListName = "New Word List"
                originalWordPairs = emptyList()
                _uiState.update {
                    it.copy(
                        listName = "New Word List",
                        isNewList = true,
                        wordPairs = emptyList<WordPair>().withEmptyRow()
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

    private fun hasChanges(listName: String, wordPairs: List<WordPair>): Boolean {
        if (listName != originalListName) return true

        // Compare word pairs by content only (word1, word2), excluding empty trailing row
        val currentContent = wordPairs.filter { it.word1.isNotEmpty() || it.word2.isNotEmpty() }
        val originalContent = originalWordPairs.filter { it.word1.isNotEmpty() || it.word2.isNotEmpty() }

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
            updatedState.copy(hasUnsavedChanges = hasChanges(updatedState.listName, updatedState.wordPairs))
        }
    }

    fun updateWordPair(updatedPair: WordPair) {
        _uiState.update { state ->
            val updatedList = state.wordPairs.map {
                if (it.id == updatedPair.id) updatedPair else it
            }
            val updatedState = state.copy(wordPairs = updatedList.withEmptyRow())
            updatedState.copy(hasUnsavedChanges = hasChanges(updatedState.listName, updatedState.wordPairs))
        }
    }

    fun deleteWordPair(pair: WordPair) {
        _uiState.update { state ->
            val updatedList = state.wordPairs - pair
            val updatedState = state.copy(wordPairs = updatedList.withEmptyRow())
            updatedState.copy(hasUnsavedChanges = hasChanges(updatedState.listName, updatedState.wordPairs))
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
                val hasPairs = currentState.wordPairs.any { it.word1.isNotBlank() || it.word2.isNotBlank() }
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
                        language1 = "English",
                        language2 = "Youth Slang"
                    )
                    listIdToSave = repository.insertWordList(newList)
                } else {
                    listIdToSave = wordListId
                    originalWordList?.let {
                        val updatedList = it.copy(name = currentState.listName)
                        repository.updateWordList(updatedList)
                    }
                }

                val existingPairIds =
                    if (!currentState.isNewList) repository.getWordPairsForList(listIdToSave).first()
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
                originalWordPairs = savedState.wordPairs.filter { it.word1.isNotEmpty() || it.word2.isNotEmpty() }
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
        _uiState.update {
            it.copy(
                listName = originalListName,
                wordPairs = originalWordPairs.withEmptyRow(),
                hasUnsavedChanges = false
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
}
