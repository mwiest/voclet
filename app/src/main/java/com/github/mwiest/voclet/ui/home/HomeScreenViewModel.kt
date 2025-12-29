package com.github.mwiest.voclet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.WordListInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    repository: VocletRepository
) : ViewModel() {

    val wordListsWithInfo: StateFlow<List<WordListInfo>> = repository.getAllWordListsWithInfo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedIds = MutableStateFlow(setOf<Long>())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun updateSelection(newIds: Set<Long>) {
        _selectedIds.value = newIds
    }

    fun toggleSelection(id: Long, isSelected: Boolean) {
        _selectedIds.update { current ->
            if (isSelected) current + id else current - id
        }
    }

    fun selectAll(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }
}
