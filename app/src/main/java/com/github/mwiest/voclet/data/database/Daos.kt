package com.github.mwiest.voclet.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordListDao {
    @Insert
    suspend fun insert(wordList: WordList): Long

    @Update
    suspend fun update(wordList: WordList)

    @Delete
    suspend fun delete(wordList: WordList)

    @Query("SELECT * FROM word_lists ORDER BY name ASC")
    fun getAllWordLists(): Flow<List<WordList>>
}

@Dao
interface WordPairDao {
    @Insert
    suspend fun insert(wordPair: WordPair): Long

    @Update
    suspend fun update(wordPair: WordPair)

    @Delete
    suspend fun delete(wordPair: WordPair)

    @Query("SELECT * FROM word_pairs WHERE word_list_id = :wordListId ORDER BY word1 ASC")
    fun getWordPairsForList(wordListId: Long): Flow<List<WordPair>>
}