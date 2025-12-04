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

                                val listId = wordListDao.insert(WordList(name = "Sample Wordlist", language1 = "en", language2 = "es"))

                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Hello", word2 = "Hola"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Goodbye", word2 = "Adiós"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Thank you", word2 = "Gracias"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Please", word2 = "Por favor"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Yes", word2 = "Sí"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "No", word2 = "No"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Good morning", word2 = "Buenos días"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Good night", word2 = "Buenas noches"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "How are you?", word2 = "¿Cómo estás?"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "I'm fine", word2 = "Estoy bien"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Water", word2 = "Agua"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Food", word2 = "Comida"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Friend", word2 = "Amigo"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "House", word2 = "Casa"))
                                wordPairDao.insert(WordPair(wordListId = listId, word1 = "Family", word2 = "Familia"))
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
