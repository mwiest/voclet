package com.github.mwiest.voclet.data

import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordListDao
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.database.WordPairDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocletRepository @Inject constructor(
    private val wordListDao: WordListDao,
    private val wordPairDao: WordPairDao
) {
    fun getAllWordLists(): Flow<List<WordList>> = wordListDao.getAllWordLists()

    fun getWordList(wordListId: Long): Flow<WordList?> = wordListDao.getWordList(wordListId)

    fun getWordPairsForList(listId: Long): Flow<List<WordPair>> = wordPairDao.getWordPairsForList(listId)

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
}
