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
    val isNewList: Boolean = false
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

    init {
        if (wordListId != -1L) {
            viewModelScope.launch {
                originalWordList = repository.getWordList(wordListId)
                val wordPairs = repository.getWordPairsForList(wordListId).first()
                _uiState.update {
                    it.copy(
                        listName = originalWordList?.name ?: "",
                        wordPairs = wordPairs,
                        isNewList = false
                    )
                }
            }
        } else {
            _uiState.update { it.copy(listName = "New Word List", isNewList = true) }
        }
    }

    fun updateWordListName(name: String) {
        _uiState.update { it.copy(listName = name) }
    }

    fun addWordPair() {
        val newPair = WordPair(id = System.currentTimeMillis() * -1, wordListId = wordListId, word1 = "", word2 = "")
        _uiState.update { it.copy(wordPairs = it.wordPairs + newPair) }
    }

    fun updateWordPair(updatedPair: WordPair) {
        _uiState.update { state ->
            state.copy(wordPairs = state.wordPairs.map {
                if (it.id == updatedPair.id) updatedPair else it
            })
        }
    }

    fun deleteWordPair(pair: WordPair) {
        _uiState.update { it.copy(wordPairs = it.wordPairs - pair) }
        if (pair.id > 0) { // Only track deletions of existing pairs
            deletedWordPairs.add(pair)
        }
    }

    fun saveChanges() {
        viewModelScope.launch {
            val currentState = _uiState.value
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

            val existingPairIds = if (!currentState.isNewList) repository.getWordPairsForList(listIdToSave).first().map { it.id }.toSet() else emptySet()

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
        }
    }
}