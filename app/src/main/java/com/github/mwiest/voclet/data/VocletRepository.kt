package com.github.mwiest.voclet.data

import androidx.room.withTransaction
import com.github.mwiest.voclet.data.database.PracticeResult
import com.github.mwiest.voclet.data.database.PracticeResultDao
import com.github.mwiest.voclet.data.database.PracticeType
import com.github.mwiest.voclet.data.database.VocletDatabase
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordListDao
import com.github.mwiest.voclet.data.database.WordListInfo
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.database.WordPairDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocletRepository @Inject constructor(
    private val database: VocletDatabase,
    private val wordListDao: WordListDao,
    private val wordPairDao: WordPairDao,
    private val practiceResultDao: PracticeResultDao
) {
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
        val result = PracticeResult(
            wordPairId = wordPairId,
            correct = correct,
            practiceType = practiceType.name,
            timestamp = System.currentTimeMillis()
        )
        practiceResultDao.insert(result)
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
}
