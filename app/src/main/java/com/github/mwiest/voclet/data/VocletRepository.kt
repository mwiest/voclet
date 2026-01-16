package com.github.mwiest.voclet.data

import androidx.room.withTransaction
import com.github.mwiest.voclet.data.database.AppSettings
import com.github.mwiest.voclet.data.database.AppSettingsDao
import com.github.mwiest.voclet.data.database.PracticeResult
import com.github.mwiest.voclet.data.database.PracticeResultDao
import com.github.mwiest.voclet.data.database.PracticeType
import com.github.mwiest.voclet.data.database.ThemeMode
import com.github.mwiest.voclet.data.database.VocletDatabase
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordListDao
import com.github.mwiest.voclet.data.database.WordListInfo
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.database.WordPairDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocletRepository @Inject constructor(
    private val database: VocletDatabase,
    private val wordListDao: WordListDao,
    private val wordPairDao: WordPairDao,
    private val practiceResultDao: PracticeResultDao,
    private val appSettingsDao: AppSettingsDao
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Ensure settings row exists on first app launch
        repositoryScope.launch {
            val settings = appSettingsDao.getSettings().first()
            if (settings == null) {
                appSettingsDao.insertOrUpdate(AppSettings())
            }
        }
    }
    fun getAllWordListsWithInfo(): Flow<List<WordListInfo>> = wordListDao.getAllWordListsWithInfo()

    suspend fun getWordList(wordListId: Long): WordList? = wordListDao.getWordList(wordListId)

    fun getWordPairsForList(listId: Long): Flow<List<WordPair>> =
        wordPairDao.getWordPairsForList(listId)

    suspend fun insertWordList(wordList: WordList): Long {
        return wordListDao.insert(wordList)
    }

    suspend fun updateWordList(wordList: WordList) {
        wordListDao.update(wordList)
    }

    suspend fun deleteWordList(wordList: WordList) {
        wordListDao.delete(wordList)
    }

    suspend fun insertWordPair(wordPair: WordPair): Long {
        return wordPairDao.insert(wordPair)
    }

    suspend fun insertWordPairs(wordPairs: List<WordPair>): List<Long> {
        return wordPairDao.insertAll(wordPairs)
    }

    suspend fun updateWordPair(wordPair: WordPair) {
        wordPairDao.update(wordPair)
    }

    suspend fun deleteWordPair(wordPair: WordPair) {
        wordPairDao.delete(wordPair)
    }

    suspend fun recordPracticeResult(
        wordPairId: Long,
        correct: Boolean,
        practiceType: PracticeType = PracticeType.FLASHCARD
    ) {
        database.withTransaction {
            // Record the practice result
            val result = PracticeResult(
                wordPairId = wordPairId,
                correct = correct,
                practiceType = practiceType.name,
                timestamp = System.currentTimeMillis()
            )
            practiceResultDao.insert(result)

            // Update correctInARow field
            val currentPair = wordPairDao.getWordPairById(wordPairId)
            if (currentPair != null) {
                val updatedPair = if (correct) {
                    currentPair.copy(correctInARow = currentPair.correctInARow + 1)
                } else {
                    currentPair.copy(correctInARow = 0)
                }
                wordPairDao.update(updatedPair)
            }
        }
    }

    fun getPracticeResults(wordPairId: Long): Flow<List<PracticeResult>> =
        practiceResultDao.getPracticeResults(wordPairId)

    suspend fun getWordPairsForLists(listIds: List<Long>): List<WordPair> {
        if (listIds.isEmpty()) return emptyList()
        return listIds.flatMap { listId ->
            wordPairDao.getWordPairsForList(listId).first()
        }
    }

    suspend fun getWordPairsForListsStarredOnly(listIds: List<Long>): List<WordPair> {
        return getWordPairsForLists(listIds).filter { it.starred }
    }

    fun getHardWordPairIds(): Flow<List<Long>> {
        return practiceResultDao.getHardWordPairIds()
    }

    suspend fun getWordPairsForListsHardOnly(listIds: List<Long>): List<WordPair> {
        if (listIds.isEmpty()) return emptyList()
        val hardWordPairIds = practiceResultDao.getHardWordPairIds().first()
        if (hardWordPairIds.isEmpty()) return emptyList()
        return wordPairDao.getHardWordPairsForLists(listIds, hardWordPairIds)
    }

    /**
     * Saves all word pair changes (inserts, updates, deletes) in a single atomic transaction.
     * This ensures that either all operations succeed or none of them are applied,
     * preventing partial saves.
     */
    suspend fun saveWordPairsTransaction(
        pairsToInsert: List<WordPair>,
        pairsToUpdate: List<WordPair>,
        pairsToDelete: List<WordPair>
    ) {
        database.withTransaction {
            if (pairsToInsert.isNotEmpty()) {
                wordPairDao.insertAll(pairsToInsert)
            }
            if (pairsToUpdate.isNotEmpty()) {
                wordPairDao.updateAll(pairsToUpdate)
            }
            if (pairsToDelete.isNotEmpty()) {
                wordPairDao.deleteAll(pairsToDelete)
            }
        }
    }

    /**
     * Fetches word lists with their pairs for export
     * Returns data in a format ready for serialization
     */
    suspend fun getWordListsForExport(listIds: List<Long>): List<Pair<WordList, List<WordPair>>> {
        return listIds.map { listId ->
            val wordList = getWordList(listId)
                ?: throw IllegalArgumentException("Word list not found: $listId")
            val wordPairs = wordPairDao.getWordPairsForList(listId).first()
            wordList to wordPairs
        }
    }

    // Settings methods
    fun getSettings(): Flow<AppSettings?> = appSettingsDao.getSettings()

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        val settings = appSettingsDao.getSettings().first() ?: AppSettings()
        appSettingsDao.insertOrUpdate(settings.copy(themeMode = themeMode))
    }

    /**
     * Deletes all practice statistics in a single atomic transaction.
     * This resets correctInARow for all word pairs and deletes all practice results.
     */
    suspend fun deleteAllStatistics() {
        database.withTransaction {
            wordPairDao.resetAllCorrectInARow()
            practiceResultDao.deleteAll()
        }
    }
}
