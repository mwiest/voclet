package com.github.mwiest.voclet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.AppSettings
import com.github.mwiest.voclet.data.database.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeleteStatsState {
    object Idle : DeleteStatsState()
    object Deleting : DeleteStatsState()
    object Success : DeleteStatsState()
    data class Error(val message: String) : DeleteStatsState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: VocletRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.getSettings()
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _deleteStatsState = MutableStateFlow<DeleteStatsState>(DeleteStatsState.Idle)
    val deleteStatsState: StateFlow<DeleteStatsState> = _deleteStatsState.asStateFlow()

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            repository.updateThemeMode(themeMode)
        }
    }

    fun deleteAllStatistics() {
        viewModelScope.launch {
            _deleteStatsState.value = DeleteStatsState.Deleting
            try {
                repository.deleteAllStatistics()
                _deleteStatsState.value = DeleteStatsState.Success
            } catch (e: Exception) {
                _deleteStatsState.value = DeleteStatsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetDeleteStatsState() {
        _deleteStatsState.value = DeleteStatsState.Idle
    }
}
