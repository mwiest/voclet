package com.github.mwiest.voclet.ui.wordlist

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.ai.AI_LOG_TAG
import com.github.mwiest.voclet.data.ai.AiBackendResolver
import com.github.mwiest.voclet.data.ai.AiRouting
import com.github.mwiest.voclet.data.ai.AiUnavailableReason
import com.github.mwiest.voclet.data.ai.CloudAiService
import com.github.mwiest.voclet.data.ai.NetworkMonitor
import com.github.mwiest.voclet.data.ai.ResolvedBackend
import com.github.mwiest.voclet.data.ai.cloud.isCloudConfigured
import com.github.mwiest.voclet.data.ai.local.LlmEngine
import com.github.mwiest.voclet.data.ai.local.LocalTranslationParser
import com.github.mwiest.voclet.data.ai.local.LocalWordPairParser
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.fileimport.FileParseException
import com.github.mwiest.voclet.data.fileimport.FileParserFactory
import com.github.mwiest.voclet.data.fileimport.ImportFileType
import com.github.mwiest.voclet.data.fileimport.ImportStep
import com.github.mwiest.voclet.ui.utils.LANGUAGES
import com.github.mwiest.voclet.ui.utils.Language
import com.github.mwiest.voclet.ui.utils.isoToLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

data class WordListDetailUiState(
    val listName: String = "",
    val language1: Language? = null,
    val language2: Language? = null,
    val wordPairs: List<WordPair> = emptyList(),
    val isNewList: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val translationSuggestions: Map<Long, TranslationSuggestion?> = emptyMap(),
    val loadingSuggestions: Set<Long> = emptySet(),

    // Camera/scanning state
    val showCameraDialog: Boolean = false,
    val isScanningImage: Boolean = false,
    val scanError: String? = null,
    val lastScanBatch: LastScanBatch? = null,

    // Import dialog state
    val showImportDialog: Boolean = false,
    val importStep: ImportStep = ImportStep.FILE_SELECTION,
    val importFileType: ImportFileType? = null,
    val importError: String? = null,
    val importFileUri: Uri? = null,

    // Step 2: Preview and column mapping
    val importPreviewData: List<List<String>> = emptyList(),
    val importPreviewCount: Int = 0,
    val importColumnHeaders: List<String> = emptyList(),
    val importSourceColumn: Int? = null,
    val importTargetColumn: Int? = null,
    val importHasHeaderRow: Boolean = false,

    // Step 3: Processing
    val isImportingFile: Boolean = false
)

/**
 * The pairs added by the most recent scan, so the UI can offer to swap just that batch
 * if the AI read the two columns the wrong way round.
 *
 * @param languagesCameFromScan true when the list had no languages and this scan detected
 * them, in which case they are as reversed as the pairs are and have to swap along. Labels
 * the user picked themselves are left alone.
 */
data class LastScanBatch(
    val pairIds: List<Long>,
    val languagesCameFromScan: Boolean
)

@HiltViewModel
class WordListDetailViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val cloudAiService: CloudAiService,
    private val llmEngine: LlmEngine,
    private val networkMonitor: NetworkMonitor,
    @param:ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val wordListId: Long = savedStateHandle.get<String>("wordListId")?.toLongOrNull() ?: -1

    // Whether cloud AI is set up, kept in sync with settings so suggestion
    // routing can be decided synchronously.
    @Volatile
    private var cloudConfigured: Boolean = false

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

    // Job for image scanning to allow cancellation
    private var scanningJob: Job? = null

    // Job for file importing to allow cancellation
    private var importingJob: Job? = null

    // Thread-safe counter to ensure unique temporary IDs
    @OptIn(ExperimentalAtomicApi::class)
    private val tempIdCounter = AtomicLong(0L)

    @OptIn(ExperimentalAtomicApi::class)
    private fun generateTempId(): Long {
        return -tempIdCounter.fetchAndIncrement()
    }

    private fun getCacheKey(word: String, fromLang: String, toLang: String): String {
        return "$word|$fromLang|$toLang"
    }

    init {
        viewModelScope.launch {
            repository.getSettings().collect { settings ->
                cloudConfigured = settings != null && isCloudConfigured(
                    provider = settings.aiCloudProvider,
                    baseUrl = settings.aiCloudBaseUrl,
                    apiKey = settings.aiCloudApiKey,
                    model = settings.aiCloudModel,
                )
            }
        }
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
            id = generateTempId(),
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
                current.word1 == original.word1 &&
                current.word2 == original.word2 &&
                current.starred == original.starred
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

    fun toggleStarred(wordPairId: Long) {
        _uiState.update { state ->
            val updatedPairs = state.wordPairs.map { pair ->
                if (pair.id == wordPairId) {
                    pair.copy(starred = !pair.starred)
                } else {
                    pair
                }
            }
            val updatedState = state.copy(wordPairs = updatedPairs)
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

                // Prepare lists for batch operations
                val pairsToInsert = mutableListOf<WordPair>()
                val pairsToUpdate = mutableListOf<WordPair>()

                for (pair in currentState.wordPairs) {
                    if (pair.word1.isNotBlank() || pair.word2.isNotBlank()) { // Save if at least one field is not blank
                        if (pair.id <= 0 || !existingPairIds.contains(pair.id)) { // New pairs (temporary negative IDs or not in DB)
                            pairsToInsert.add(pair.copy(id = 0, wordListId = listIdToSave))
                        } else { // Existing pairs
                            pairsToUpdate.add(pair.copy(wordListId = listIdToSave))
                        }
                    }
                }

                // Execute all database operations in a transaction
                repository.saveWordPairsTransaction(
                    pairsToInsert = pairsToInsert,
                    pairsToUpdate = pairsToUpdate,
                    pairsToDelete = deletedWordPairs.toList()
                )

                // Update original state and clear unsaved changes flag after successful save
                val savedState = _uiState.value
                originalListName = savedState.listName
                originalWordPairs =
                    savedState.wordPairs.filter { it.word1.isNotEmpty() || it.word2.isNotEmpty() }
                deletedWordPairs.clear()

                _uiState.update { it.copy(hasUnsavedChanges = false, isSaving = false) }
            } catch (e: Exception) {
                Log.e("WordListDetail", "Error saving changes", e)
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

        // Decide which backend serves this request (cloud / on-device / none).
        // Silently do nothing if no backend is available (e.g. nothing set up
        // yet, or offline with no model downloaded): a translation hint is a
        // convenience, not something worth interrupting typing for.
        val routing = AiBackendResolver.resolve(
            cloudConfigured = cloudConfigured,
            online = networkMonitor.isOnline(),
            localModelAvailable = llmEngine.isModelAvailable(),
        )
        Log.d(AI_LOG_TAG, "Translation suggestion for \"$word1\" ($lang1->$lang2): $routing")
        val backend = (routing as? AiRouting.Use)?.backend ?: return

        // Mark as loading
        inFlightRequests.add(cacheKey)
        _uiState.update { state ->
            state.copy(loadingSuggestions = state.loadingSuggestions + wordPairId)
        }

        viewModelScope.launch {
            try {
                val suggestion = when (backend) {
                    ResolvedBackend.CLOUD ->
                        cloudAiService.suggestTranslation(word1, lang1, lang2)
                            .onFailure { Log.w(AI_LOG_TAG, "Cloud suggestion failed", it) }
                            .getOrNull()
                    ResolvedBackend.LOCAL ->
                        runLocalTranslation(word1, lang1, lang2)
                }

                if (suggestion != null) {
                    suggestionCache[cacheKey] = suggestion
                    _uiState.update { state ->
                        state.copy(
                            translationSuggestions = state.translationSuggestions + (wordPairId to suggestion),
                            loadingSuggestions = state.loadingSuggestions - wordPairId
                        )
                    }
                } else {
                    // Silent in the UI - a hint is a convenience - but logged
                    // under AI_LOG_TAG so a dead backend can be diagnosed.
                    Log.w(AI_LOG_TAG, "No suggestion returned by $backend")
                    _uiState.update { state ->
                        state.copy(loadingSuggestions = state.loadingSuggestions - wordPairId)
                    }
                }
            } catch (e: Exception) {
                Log.w(AI_LOG_TAG, "Suggestion request threw", e)
                _uiState.update { state ->
                    state.copy(loadingSuggestions = state.loadingSuggestions - wordPairId)
                }
            } finally {
                inFlightRequests.remove(cacheKey)
            }
        }
    }

    /** Runs on-device translation, collecting the accumulated stream then parsing it. */
    private suspend fun runLocalTranslation(
        word: String,
        fromLang: String,
        toLang: String
    ): TranslationSuggestion? {
        var full = ""
        llmEngine.suggestTranslation(word, fromLang, toLang).collect { full = it }
        return LocalTranslationParser.parse(full)
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

    fun openCameraDialog() {
        _uiState.update { it.copy(showCameraDialog = true, scanError = null) }
    }

    fun closeCameraDialog() {
        // Cancel any ongoing scanning
        scanningJob?.cancel()
        scanningJob = null
        _uiState.update {
            it.copy(
                showCameraDialog = false,
                isScanningImage = false,
                scanError = null
            )
        }
    }

    fun processCameraImage(bitmap: Bitmap) {
        // Cancel any existing scanning job
        scanningJob?.cancel()

        scanningJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanningImage = true, scanError = null) }

            try {
                val routing = AiBackendResolver.resolve(
                    cloudConfigured = cloudConfigured,
                    online = networkMonitor.isOnline(),
                    localModelAvailable = llmEngine.isModelAvailable(),
                )
                Log.d(AI_LOG_TAG, "Camera import (${bitmap.width}x${bitmap.height}): $routing")
                when (routing) {
                    is AiRouting.Use -> when (routing.backend) {
                        ResolvedBackend.CLOUD -> extractViaCloud(bitmap)
                        ResolvedBackend.LOCAL -> extractViaLocal(bitmap)
                    }
                    is AiRouting.Unavailable -> _uiState.update {
                        it.copy(
                            isScanningImage = false,
                            scanError = appContext.getString(
                                when (routing.reason) {
                                    AiUnavailableReason.NOT_CONFIGURED ->
                                        com.github.mwiest.voclet.R.string.ai_no_backend_available
                                    AiUnavailableReason.OFFLINE ->
                                        com.github.mwiest.voclet.R.string.ai_offline_no_local_model
                                }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanningImage = false,
                        scanError = "Unexpected error: ${e.message}"
                    )
                }
            } finally {
                // Clean up job reference when done
                if (scanningJob?.isActive == false) {
                    scanningJob = null
                }
            }
        }
    }

    private suspend fun extractViaCloud(bitmap: Bitmap) {
        val currentState = _uiState.value
        val result = cloudAiService.extractWordPairsFromImage(
            image = bitmap,
            preferredLanguage1 = currentState.language1?.code,
            preferredLanguage2 = currentState.language2?.code
        )

        result.fold(
            onSuccess = { extraction ->
                // Auto-update title if empty
                val updatedTitle =
                    if (currentState.listName.isEmpty() && !extraction.title.isNullOrBlank()) {
                        extraction.title
                    } else {
                        currentState.listName
                    }

                // Auto-update languages if empty
                val updatedLanguage1 =
                    currentState.language1 ?: LANGUAGES.find { it.code == extraction.detectedLanguage1 }
                val updatedLanguage2 =
                    currentState.language2 ?: LANGUAGES.find { it.code == extraction.detectedLanguage2 }

                val newPairs = extraction.wordPairs.map { extractedPair ->
                    WordPair(
                        id = generateTempId(),
                        wordListId = wordListId,
                        word1 = extractedPair.word1,
                        word2 = extractedPair.word2
                    )
                }

                applyExtractedPairs(newPairs, updatedTitle, updatedLanguage1, updatedLanguage2)
            },
            onFailure = { error ->
                Log.d("WordScan", "Error: ${error.message}", error)
                _uiState.update {
                    it.copy(
                        isScanningImage = false,
                        scanError = error.message ?: "Failed to extract word pairs"
                    )
                }
            }
        )
    }

    private suspend fun extractViaLocal(bitmap: Bitmap) {
        val currentState = _uiState.value
        val imageUri = writeBitmapToCache(bitmap)
        if (imageUri == null) {
            _uiState.update {
                it.copy(
                    isScanningImage = false,
                    scanError = appContext.getString(com.github.mwiest.voclet.R.string.ai_extract_failed)
                )
            }
            return
        }
        try {
            var jsonOut = ""
            llmEngine.extractWordPairs(
                imageUri = imageUri,
                lang1 = currentState.language1?.code,
                lang2 = currentState.language2?.code
            ).collect { jsonOut = it }

            val extracted = LocalWordPairParser.parse(jsonOut)
            if (extracted.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isScanningImage = false,
                        scanError = appContext.getString(com.github.mwiest.voclet.R.string.ai_extract_no_pairs)
                    )
                }
                return
            }

            val newPairs = extracted.map { extractedPair ->
                WordPair(
                    id = generateTempId(),
                    wordListId = wordListId,
                    word1 = extractedPair.word1,
                    word2 = extractedPair.word2
                )
            }
            // The local model only returns pairs (no title/language detection),
            // so keep the user's current title and languages.
            applyExtractedPairs(newPairs, currentState.listName, currentState.language1, currentState.language2)
        } finally {
            imageUri.path?.let { runCatching { java.io.File(it).delete() } }
        }
    }

    /** Merges freshly extracted pairs into the editor and closes the camera dialog. */
    private fun applyExtractedPairs(
        newPairs: List<WordPair>,
        title: String,
        language1: Language?,
        language2: Language?
    ) {
        _uiState.update { state ->
            val existingNonEmpty = state.wordPairs.filter {
                it.word1.isNotEmpty() || it.word2.isNotEmpty()
            }
            val combinedPairs = (existingNonEmpty + newPairs).withEmptyRow()
            val updatedState = state.copy(
                listName = title,
                language1 = language1,
                language2 = language2,
                wordPairs = combinedPairs,
                isScanningImage = false,
                showCameraDialog = false,
                lastScanBatch = LastScanBatch(
                    pairIds = newPairs.map { it.id },
                    languagesCameFromScan = state.language1 == null && state.language2 == null
                )
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

    /** Writes [bitmap] to a temp JPEG in the cache dir for the on-device model to read. */
    private fun writeBitmapToCache(bitmap: Bitmap): Uri? = try {
        val file = java.io.File(appContext.cacheDir, "scan_${System.nanoTime()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }

    fun clearScanError() {
        _uiState.update { it.copy(scanError = null) }
    }

    /** Turns the whole list around: both language labels and every pair. */
    fun swapLanguages() {
        // word1 is about to be in the other language, so cached suggestions no longer apply.
        suggestionCache.clear()
        _uiState.update { state ->
            val updatedState = state.copy(
                language1 = state.language2,
                language2 = state.language1,
                wordPairs = state.wordPairs.map { it.swapped() },
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

    /**
     * Turns around only the pairs the last scan added, for when the AI read the workbook's
     * two columns the wrong way round. Pairs that were already in the list keep their
     * direction, and so do language labels the user picked — see [LastScanBatch].
     */
    fun swapScannedPairs() {
        val batch = _uiState.value.lastScanBatch ?: return
        suggestionCache.clear()
        _uiState.update { state ->
            val scanned = batch.pairIds.toSet()
            val updatedState = state.copy(
                language1 = if (batch.languagesCameFromScan) state.language2 else state.language1,
                language2 = if (batch.languagesCameFromScan) state.language1 else state.language2,
                wordPairs = state.wordPairs.map { if (it.id in scanned) it.swapped() else it },
                translationSuggestions = emptyMap(),
                loadingSuggestions = emptySet(),
                lastScanBatch = null
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

    fun clearLastScanBatch() {
        _uiState.update { it.copy(lastScanBatch = null) }
    }

    private fun WordPair.swapped() = copy(word1 = word2, word2 = word1)

    // Import dialog methods

    fun openImportDialog() {
        _uiState.update {
            it.copy(
                showImportDialog = true,
                importStep = ImportStep.FILE_SELECTION,
                importError = null,
                importFileType = null,
                importFileUri = null,
                importPreviewData = emptyList(),
                importColumnHeaders = emptyList(),
                importSourceColumn = null,
                importTargetColumn = null,
                importHasHeaderRow = false,
                isImportingFile = false
            )
        }
    }

    fun closeImportDialog() {
        // Cancel any ongoing import
        importingJob?.cancel()
        importingJob = null
        _uiState.update {
            it.copy(
                showImportDialog = false,
                importStep = ImportStep.FILE_SELECTION,
                importError = null,
                importFileType = null,
                importFileUri = null,
                importPreviewData = emptyList(),
                importColumnHeaders = emptyList(),
                importSourceColumn = null,
                importTargetColumn = null,
                importHasHeaderRow = false,
                isImportingFile = false
            )
        }
    }

    fun processSelectedFile(uri: Uri, context: Context) {
        importingJob?.cancel()

        importingJob = viewModelScope.launch {
            _uiState.update { it.copy(importError = null) }

            try {
                // Detect file type from URI
                val fileName = uri.lastPathSegment?.lowercase() ?: ""
                val fileType = when {
                    fileName.endsWith(".csv") -> ImportFileType.CSV
                    fileName.endsWith(".xlsx") || fileName.endsWith(".xls") -> {
                        _uiState.update {
                            it.copy(
                                importError = "Excel files are not currently supported. Please export your file as CSV and try again."
                            )
                        }
                        return@launch
                    }

                    else -> {
                        // Try to detect from MIME type
                        val mimeType = context.contentResolver.getType(uri)
                        when {
                            mimeType?.contains("csv") == true || mimeType?.contains("comma-separated-value") == true -> ImportFileType.CSV
                            mimeType?.contains("spreadsheet") == true ||
                                    mimeType?.contains("excel") == true -> {
                                _uiState.update {
                                    it.copy(
                                        importError = "Excel files are not currently supported. Please export your file as CSV and try again."
                                    )
                                }
                                return@launch
                            }

                            else -> {
                                _uiState.update {
                                    it.copy(
                                        importError = context.getString(
                                            com.github.mwiest.voclet.R.string.import_error_invalid_file
                                        )
                                    )
                                }
                                return@launch
                            }
                        }
                    }
                }

                // Parse the file
                val parser = FileParserFactory.create(fileType)
                val allRows = parser.parse(uri, context)

                // Validate file
                if (allRows.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            importError = context.getString(
                                com.github.mwiest.voclet.R.string.import_error_empty_file
                            )
                        )
                    }
                    return@launch
                }

                // Get first row to check column count
                val firstRow = allRows.first()
                if (firstRow.size < 2) {
                    _uiState.update {
                        it.copy(
                            importError = context.getString(
                                com.github.mwiest.voclet.R.string.import_error_single_column
                            )
                        )
                    }
                    return@launch
                }

                // Extract preview (first 5 rows, first 5 columns)
                val previewData = allRows.take(5).map { row ->
                    row.take(5)
                }

                // Generate column headers (A, B, C, D, E for now, user can toggle header row)
                val columnHeaders =
                    listOf("A", "B", "C", "D", "E").take(firstRow.size.coerceAtMost(5))

                // Advance to column mapping step
                _uiState.update {
                    it.copy(
                        importStep = ImportStep.COLUMN_MAPPING,
                        importFileType = fileType,
                        importFileUri = uri,
                        importPreviewData = previewData,
                        importPreviewCount = allRows.size,
                        importColumnHeaders = columnHeaders,
                        importSourceColumn = 0, // Default to first column
                        importTargetColumn = if (firstRow.size > 1) 1 else null, // Default to second column
                        importError = null
                    )
                }
            } catch (e: FileParseException) {
                Log.e("Import", "Parse error", e)
                val errorKey = if (_uiState.value.importFileType == ImportFileType.CSV) {
                    com.github.mwiest.voclet.R.string.import_error_invalid_file
                } else {
                    com.github.mwiest.voclet.R.string.import_error_invalid_file
                }
                _uiState.update {
                    it.copy(importError = context.getString(errorKey))
                }
            } catch (e: Exception) {
                Log.e("Import", "Unexpected error", e)
                _uiState.update {
                    it.copy(
                        importError = context.getString(
                            com.github.mwiest.voclet.R.string.import_error_invalid_file
                        )
                    )
                }
            }
        }
    }

    fun updateSourceColumn(index: Int) {
        _uiState.update { state ->
            state.copy(
                importSourceColumn = index,
                importError = if (index == state.importTargetColumn) {
                    "Source and target columns must be different"
                } else {
                    null
                }
            )
        }
    }

    fun updateTargetColumn(index: Int) {
        _uiState.update { state ->
            state.copy(
                importTargetColumn = index,
                importError = if (index == state.importSourceColumn) {
                    "Source and target columns must be different"
                } else {
                    null
                }
            )
        }
    }

    fun toggleHeaderRow(hasHeader: Boolean) {
        _uiState.update { state ->
            // Update column headers based on hasHeader toggle
            val newHeaders = if (hasHeader && state.importPreviewData.isNotEmpty()) {
                // Use first row as headers
                state.importPreviewData.first()
            } else {
                // Use A, B, C, D, E
                listOf("A", "B", "C", "D", "E").take(
                    state.importPreviewData.firstOrNull()?.size?.coerceAtMost(5) ?: 5
                )
            }

            state.copy(
                importHasHeaderRow = hasHeader,
                importColumnHeaders = newHeaders
            )
        }
    }

    fun proceedToImport(context: Context) {
        val currentState = _uiState.value

        // Validate
        if (currentState.importSourceColumn == null || currentState.importTargetColumn == null) {
            return
        }

        if (currentState.importSourceColumn == currentState.importTargetColumn) {
            _uiState.update {
                it.copy(
                    importError = context.getString(
                        com.github.mwiest.voclet.R.string.import_error_same_columns
                    )
                )
            }
            return
        }

        val uri = currentState.importFileUri ?: return
        val fileType = currentState.importFileType ?: return

        importingJob?.cancel()

        importingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    importStep = ImportStep.PROCESSING,
                    isImportingFile = true,
                    importError = null
                )
            }

            try {
                // Parse the entire file
                val parser = FileParserFactory.create(fileType)
                val allRows = parser.parse(uri, context)

                // Skip header row if enabled
                val dataRows = if (currentState.importHasHeaderRow && allRows.isNotEmpty()) {
                    allRows.drop(1)
                } else {
                    allRows
                }

                // Extract word pairs from selected columns
                val newPairs = dataRows.mapNotNull { row ->
                    val sourceCol = currentState.importSourceColumn!!
                    val targetCol = currentState.importTargetColumn!!

                    if (row.size > sourceCol && row.size > targetCol) {
                        val word1 = row[sourceCol].trim()
                        val word2 = row[targetCol].trim()

                        // Only create pair if at least one word is non-empty
                        if (word1.isNotEmpty() || word2.isNotEmpty()) {
                            WordPair(
                                id = generateTempId(),
                                wordListId = wordListId,
                                word1 = word1,
                                word2 = word2
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

                // Enforce minimum 5 seconds delay
                val startTime = System.currentTimeMillis()

                // Merge with existing pairs
                val existingNonEmpty = currentState.wordPairs.filter {
                    it.word1.isNotEmpty() || it.word2.isNotEmpty()
                }
                val combinedPairs = (existingNonEmpty + newPairs).withEmptyRow()

                // Ensure minimum 5 seconds have elapsed
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 5000) {
                    delay(5000 - elapsed)
                }

                _uiState.update { state ->
                    val updatedState = state.copy(
                        wordPairs = combinedPairs,
                        isImportingFile = false,
                        showImportDialog = false,
                        importStep = ImportStep.FILE_SELECTION
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
            } catch (e: Exception) {
                Log.e("Import", "Import failed", e)
                _uiState.update {
                    it.copy(
                        isImportingFile = false,
                        importError = context.getString(
                            com.github.mwiest.voclet.R.string.import_error_invalid_file
                        ),
                        importStep = ImportStep.COLUMN_MAPPING
                    )
                }
            } finally {
                if (importingJob?.isActive == false) {
                    importingJob = null
                }
            }
        }
    }

    fun clearImportError() {
        _uiState.update { it.copy(importError = null) }
    }
}
