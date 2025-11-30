package com.github.mwiest.voclet.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [WordList::class, WordPair::class, PracticeResult::class], version = 1)
abstract class VocletDatabase : RoomDatabase() {
    abstract fun wordListDao(): WordListDao
    abstract fun wordPairDao(): WordPairDao
    abstract fun practiceResultDao(): PracticeResultDao

    companion object {
        @Volatile
        private var INSTANCE: VocletDatabase? = null

        fun getDatabase(context: Context): VocletDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VocletDatabase::class.java,
                    "voclet_database"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate the database
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.let {
                                val wordListDao = it.wordListDao()
                                val wordPairDao = it.wordPairDao()

                                val listId = wordListDao.insert(WordList(name = "Sample Wordlist", language1 = "English", language2 = "Youth Slang"))

                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "That's great!", word2 = "That's bussin'!"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "He's the best.", word2 = "He's the GOAT."))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "I'm just kidding.", word2 = "I'm just trolling."))
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
