package com.github.mwiest.voclet.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.VocletDatabase
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordListInfo
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.export.ExportException
import com.github.mwiest.voclet.data.export.ExportWordList
import com.github.mwiest.voclet.data.export.ExportWordPair
import com.github.mwiest.voclet.data.export.ImportException
import com.github.mwiest.voclet.data.export.VocletExport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val database: VocletDatabase
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

    val hardWordPairIds: StateFlow<Set<Long>> = repository.getHardWordPairIds()
        .map { it.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

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

    // Import state
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    sealed class ImportState {
        object Idle : ImportState()
        object ParsingFile : ImportState()
        data class ShowingPreview(val previewData: ImportPreviewData) : ImportState()
        object Importing : ImportState()
        data class Success(val importedCount: Int) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    data class ImportPreviewData(
        val vocletExport: VocletExport,
        val listSelections: Map<Int, Boolean> = emptyMap()
    ) {
        init {
            // Initialize all lists as selected by default if not provided
            if (listSelections.isEmpty() && vocletExport.lists.isNotEmpty()) {
                @Suppress("UNUSED_EXPRESSION")
                vocletExport.lists.indices.associateWith { true }
            }
        }
    }

    /**
     * Parses a .voclet.json file and shows preview dialog
     * @param uri The URI of the file to import (from OpenDocument contract)
     * @param context Android context for ContentResolver access
     */
    fun parseImportFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.ParsingFile

                val jsonString = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.reader().readText()
                    } ?: throw ImportException("Failed to open file")
                }

                // Parse JSON
                val json = Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
                val vocletExport = json.decodeFromString<VocletExport>(jsonString)

                // Validate
                if (vocletExport.lists.isEmpty()) {
                    _importState.value = ImportState.Error("File contains no word lists")
                    return@launch
                }

                // Show preview with all lists pre-selected
                val previewData = ImportPreviewData(
                    vocletExport = vocletExport,
                    listSelections = vocletExport.lists.indices.associateWith { true }
                )
                _importState.value = ImportState.ShowingPreview(previewData)

                Log.d("HomeViewModel", "Import preview ready: ${vocletExport.lists.size} lists")

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Import parsing failed", e)
                _importState.value = ImportState.Error(
                    e.message ?: "Failed to parse import file"
                )
            }
        }
    }

    /**
     * Updates which lists are selected for import in the preview
     */
    fun updateImportSelection(listIndex: Int, selected: Boolean) {
        val currentState = _importState.value
        if (currentState is ImportState.ShowingPreview) {
            val updatedSelections = currentState.previewData.listSelections.toMutableMap()
            updatedSelections[listIndex] = selected
            _importState.value = ImportState.ShowingPreview(
                currentState.previewData.copy(listSelections = updatedSelections)
            )
        }
    }

    /**
     * Imports selected lists from the preview data
     */
    fun importSelectedLists() {
        viewModelScope.launch {
            try {
                val currentState = _importState.value
                if (currentState !is ImportState.ShowingPreview) {
                    return@launch
                }

                _importState.value = ImportState.Importing

                val previewData = currentState.previewData
                val listsToImport = previewData.vocletExport.lists
                    .filterIndexed { index, _ ->
                        previewData.listSelections[index] == true
                    }

                if (listsToImport.isEmpty()) {
                    _importState.value = ImportState.Error("No lists selected for import")
                    return@launch
                }

                // Import in transaction
                val importedCount = withContext(Dispatchers.IO) {
                    database.withTransaction {
                        listsToImport.forEach { exportList ->
                            // Insert word list (generates new ID)
                            val newListId = repository.insertWordList(
                                WordList(
                                    id = 0, // Will be auto-generated
                                    name = exportList.name,
                                    language1 = exportList.language1,
                                    language2 = exportList.language2
                                )
                            )

                            // Insert word pairs for this list
                            if (exportList.pairs.isNotEmpty()) {
                                val wordPairs = exportList.pairs.map { exportPair ->
                                    WordPair(
                                        id = 0, // Will be auto-generated
                                        wordListId = newListId,
                                        word1 = exportPair.word1,
                                        word2 = exportPair.word2,
                                        starred = exportPair.starred,
                                        correctInARow = 0 // Reset practice stats
                                    )
                                }
                                repository.insertWordPairs(wordPairs)
                            }
                        }
                    }
                    listsToImport.size
                }

                _importState.value = ImportState.Success(importedCount)

                Log.d("HomeViewModel", "Import successful: $importedCount lists")

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Import failed", e)
                _importState.value = ImportState.Error(
                    e.message ?: "Failed to import word lists"
                )
            }
        }
    }

    /**
     * Resets import state to Idle (call this after showing success/error message)
     */
    fun clearImportState() {
        _importState.value = ImportState.Idle
    }
}
