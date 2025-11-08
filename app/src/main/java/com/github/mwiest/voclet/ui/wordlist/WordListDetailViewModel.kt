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
    val wordList: WordList? = null,
    val wordPairs: List<WordPair> = emptyList()
)

@HiltViewModel
class WordListDetailViewModel @Inject constructor(
    private val repository: VocletRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val wordListId: Long = savedStateHandle.get<String>("wordListId")?.toLongOrNull() ?: -1

    private val _uiState = MutableStateFlow(WordListDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (wordListId != -1L) {
            viewModelScope.launch {
                _uiState.update { it.copy(wordList = repository.getWordList(wordListId)) }
                repository.getWordPairsForList(wordListId).collect { wordPairs ->
                    _uiState.update { it.copy(wordPairs = wordPairs) }
                }
            }
        }
    }

    fun updateWordListName(name: String) {
        _uiState.update { it.copy(wordList = it.wordList?.copy(name = name)) }
    }

    fun addWordPair(newPair: WordPair) {
        viewModelScope.launch {
            repository.insertWordPair(newPair.copy(wordListId = wordListId))
        }
    }

    fun updateWordPair(updatedPair: WordPair) {
        viewModelScope.launch {
            repository.updateWordPair(updatedPair)
        }
    }

    fun deleteWordPair(pair: WordPair) {
        viewModelScope.launch {
            repository.deleteWordPair(pair)
        }
    }

    fun saveChanges() {
        viewModelScope.launch {
            _uiState.value.wordList?.let { repository.updateWordList(it) }
        }
    }
}
