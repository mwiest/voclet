package com.github.mwiest.voclet.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.WordListInfo
import com.github.mwiest.voclet.data.export.ExportException
import com.github.mwiest.voclet.data.export.ExportWordList
import com.github.mwiest.voclet.data.export.ExportWordPair
import com.github.mwiest.voclet.data.export.VocletExport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val repository: VocletRepository
) : ViewModel() {

    val wordListsWithInfo: StateFlow<List<WordListInfo>> = repository.getAllWordListsWithInfo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedIds = MutableStateFlow(setOf<Long>())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _selectedWordPairs =
        MutableStateFlow<List<com.github.mwiest.voclet.data.database.WordPair>>(emptyList())
    val selectedWordPairs: StateFlow<List<com.github.mwiest.voclet.data.database.WordPair>> =
        _selectedWordPairs.asStateFlow()

    // Export state
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    sealed class ExportState {
        object Idle : ExportState()
        object Exporting : ExportState()
        data class Success(val fileName: String) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    fun updateSelection(newIds: Set<Long>) {
        _selectedIds.value = newIds
        updateSelectedWordPairs(newIds)
    }

    fun toggleSelection(id: Long, isSelected: Boolean) {
        _selectedIds.update { current ->
            val newIds = if (isSelected) current + id else current - id
            updateSelectedWordPairs(newIds)
            newIds
        }
    }

    fun selectAll(ids: Set<Long>) {
        _selectedIds.value = ids
        updateSelectedWordPairs(ids)
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _selectedWordPairs.value = emptyList()
    }

    private fun updateSelectedWordPairs(selectedIds: Set<Long>) {
        viewModelScope.launch {
            val pairs = withContext(Dispatchers.IO) {
                repository.getWordPairsForLists(selectedIds.toList())
            }
            _selectedWordPairs.value = pairs
        }
    }

    /**
     * Exports selected word lists to a .voclet.json file
     * @param uri The URI where the file should be written (from CreateDocument contract)
     * @param context Android context for ContentResolver access
     */
    fun exportSelectedLists(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                _exportState.value = ExportState.Exporting

                val selectedListIds = _selectedIds.value.toList()
                if (selectedListIds.isEmpty()) {
                    _exportState.value = ExportState.Error("No lists selected for export")
                    return@launch
                }

                // Fetch data from repository
                val listsWithPairs = withContext(Dispatchers.IO) {
                    repository.getWordListsForExport(selectedListIds)
                }

                // Transform to export models
                val exportLists = listsWithPairs.map { (wordList, wordPairs) ->
                    ExportWordList(
                        name = wordList.name,
                        language1 = wordList.language1,
                        language2 = wordList.language2,
                        pairs = wordPairs.map { pair ->
                            ExportWordPair(
                                word1 = pair.word1,
                                word2 = pair.word2,
                                starred = pair.starred
                            )
                        }
                    )
                }

                val export = VocletExport(lists = exportLists)

                // Serialize to JSON
                val json = Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
                val jsonString = json.encodeToString(export)

                // Write to file
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.writer().use { writer ->
                            writer.write(jsonString)
                        }
                    } ?: throw ExportException("Failed to open output stream")
                }

                // Get filename from URI for success message
                val fileName = getFileNameFromUri(uri, context) ?: "export.voclet.json"
                _exportState.value = ExportState.Success(fileName)

                Log.d("HomeViewModel", "Export successful: $fileName")

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Export failed", e)
                _exportState.value = ExportState.Error(
                    e.message ?: "Failed to export word lists"
                )
            }
        }
    }

    /**
     * Generates a sanitized filename for export
     * Single list: {name}.voclet.json
     * Multiple lists: Voclet_export_{timestamp}.voclet.json
     */
    fun getExportFileName(): String {
        val selectedListIds = _selectedIds.value
        val listsInfo = wordListsWithInfo.value

        return if (selectedListIds.size == 1) {
            // Single list: use sanitized list name
            val wordList = listsInfo.find { it.wordList.id == selectedListIds.first() }
            val baseName = wordList?.wordList?.name?.let { sanitizeFileName(it) }
                ?: "wordlist"
            "$baseName.voclet.json"
        } else {
            // Multiple lists: use timestamp
            val timestamp = System.currentTimeMillis()
            "Voclet_export_$timestamp.voclet.json"
        }
    }

    /**
     * Sanitizes a filename by removing invalid characters
     * Rules:
     * - Remove: \ / : * ? " < > |
     * - Replace spaces with underscores
     * - Limit to 50 characters
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("""[\\/:*?"<>|]"""), "")
            .replace(" ", "_")
            .take(50)
            .ifEmpty { "wordlist" }
    }

    /**
     * Retrieves the actual filename from a content URI
     */
    private fun getFileNameFromUri(uri: Uri, context: Context): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }

    /**
     * Resets export state to Idle (call this after showing success/error message)
     */
    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }
}
