package com.github.mwiest.voclet.ui.wordlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordListDetailViewModel @Inject constructor(
    private val repository: VocletRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val wordListId: Long = savedStateHandle.get<String>("wordListId")?.toLongOrNull() ?: -1

    val wordList: StateFlow<WordList?> = repository.getWordList(wordListId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val wordPairs: StateFlow<List<WordPair>> = repository.getWordPairsForList(wordListId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateWordListName(name: String) {
        // This will be handled differently with StateFlow
    }

    fun addWordPair() {
        // Handled by UI for now
    }

    fun updateWordPair(updatedPair: WordPair) {
        // This will be handled differently with StateFlow
    }

    fun deleteWordPair(pair: WordPair) {
        // Handled by UI for now
    }

    fun saveChanges() {
        viewModelScope.launch {
            wordList.value?.let { repository.updateWordList(it) }
            // TODO: Also save word pairs
        }
    }
}
