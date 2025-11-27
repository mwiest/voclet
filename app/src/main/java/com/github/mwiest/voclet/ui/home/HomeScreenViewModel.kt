package com.github.mwiest.voclet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.WordListInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
}
