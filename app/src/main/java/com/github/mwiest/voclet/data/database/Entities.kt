package com.github.mwiest.voclet.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class PracticeType(val displayName: String) {
    FLASHCARD("Flashcard Flip")
}

@Entity(tableName = "word_lists")
data class WordList(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "language_1")
    val language1: String?,
    @ColumnInfo(name = "language_2")
    val language2: String?
)

@Entity(
    tableName = "word_pairs",
    foreignKeys = [
        ForeignKey(
            entity = WordList::class,
            parentColumns = ["id"],
            childColumns = ["word_list_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WordPair(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_list_id", index = true)
    val wordListId: Long,
    val word1: String,
    val word2: String,
    val starred: Boolean = false,
    // We can use this to track difficult words. 0 = not practiced, higher is better.
    @ColumnInfo(name = "correct_in_a_row")
    val correctInARow: Int = 0
)

data class WordListInfo(
    @Embedded val wordList: WordList,
    val pairCount: Int
)

@Entity(
    tableName = "practice_results",
    foreignKeys = [
        ForeignKey(
            entity = WordPair::class,
            parentColumns = ["id"],
            childColumns = ["word_pair_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PracticeResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_pair_id", index = true)
    val wordPairId: Long,
    val correct: Boolean,
    val practiceType: String,
    val timestamp: Long = System.currentTimeMillis()
)