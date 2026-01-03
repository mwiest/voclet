package com.github.mwiest.voclet.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * SQL fragments for reuse across multiple DAO queries.
 * Extracted as constants to maintain DRY principle.
 */
object DaoConstants {
    /**
     * WHERE condition to check if a practice result represents a failure
     * within the last 3 attempts for a word pair.
     *
     * This fragment checks if there are fewer than 3 practice results
     * more recent than the current failure result, meaning this failure
     * is within the last 3 attempts.
     *
     * Variables available in context:
     * - pr: practice_results row being checked (must have word_pair_id, correct fields)
     */
    const val HARD_WORD_CONDITION = """
        pr.correct = 0
        AND (
            SELECT COUNT(*)
            FROM practice_results pr2
            WHERE pr2.word_pair_id = pr.word_pair_id
              AND pr2.timestamp > pr.timestamp
        ) < 3
    """
}

@Dao
interface WordListDao {
    @Insert
    suspend fun insert(wordList: WordList): Long

    @Update
    suspend fun update(wordList: WordList)

    @Delete
    suspend fun delete(wordList: WordList)

    @Query("""
        SELECT
            word_lists.*,
            (SELECT COUNT(*) FROM word_pairs WHERE word_list_id = word_lists.id) as pairCount,
            (SELECT COUNT(*) FROM word_pairs WHERE word_list_id = word_lists.id AND starred = 1) as starredCount,
            (SELECT COUNT(DISTINCT wp.id)
             FROM word_pairs wp
             WHERE wp.word_list_id = word_lists.id
               AND EXISTS (
                 SELECT 1 FROM practice_results pr
                 WHERE pr.word_pair_id = wp.id
                   AND ${DaoConstants.HARD_WORD_CONDITION}
               )
            ) as hardCount
        FROM word_lists
        ORDER BY id DESC
        """)
    fun getAllWordListsWithInfo(): Flow<List<WordListInfo>>

    @Query("SELECT * FROM word_lists WHERE id = :wordListId")
    suspend fun getWordList(wordListId: Long): WordList?
}

@Dao
interface WordPairDao {
    @Insert
    suspend fun insert(wordPair: WordPair): Long

    @Insert
    suspend fun insertAll(wordPairs: List<WordPair>): List<Long>

    @Update
    suspend fun update(wordPair: WordPair)

    @Update
    suspend fun updateAll(wordPairs: List<WordPair>)

    @Delete
    suspend fun delete(wordPair: WordPair)

    @Delete
    suspend fun deleteAll(wordPairs: List<WordPair>)

    @Query("SELECT * FROM word_pairs WHERE word_list_id = :wordListId ORDER BY id ASC")
    fun getWordPairsForList(wordListId: Long): Flow<List<WordPair>>

    @Query("SELECT * FROM word_pairs WHERE id = :wordPairId")
    suspend fun getWordPairById(wordPairId: Long): WordPair?

    @Query("SELECT * FROM word_pairs WHERE word_list_id IN (:listIds) AND id IN (:hardWordPairIds) ORDER BY id ASC")
    suspend fun getHardWordPairsForLists(listIds: List<Long>, hardWordPairIds: List<Long>): List<WordPair>
}

@Dao
interface PracticeResultDao {
    @Insert
    suspend fun insert(result: PracticeResult): Long

    @Query("SELECT * FROM practice_results WHERE word_pair_id = :wordPairId ORDER BY timestamp DESC")
    fun getPracticeResults(wordPairId: Long): Flow<List<PracticeResult>>

    /**
     * Gets IDs of word pairs that have at least one incorrect attempt in their last 3 practice results.
     * A word pair is "hard" if it has failed at least once in the last 3 attempts.
     * Returns a Flow that automatically updates when practice_results table changes.
     */
    @Query("""
        SELECT DISTINCT pr.word_pair_id
        FROM practice_results pr
        WHERE ${DaoConstants.HARD_WORD_CONDITION}
    """)
    fun getHardWordPairIds(): Flow<List<Long>>
}