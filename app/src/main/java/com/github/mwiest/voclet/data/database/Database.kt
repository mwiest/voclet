package com.github.mwiest.voclet.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [WordList::class, WordPair::class, PracticeResult::class, AppSettings::class],
    version = 5
)
@TypeConverters(Converters::class)
abstract class VocletDatabase : RoomDatabase() {
    abstract fun wordListDao(): WordListDao
    abstract fun wordPairDao(): WordPairDao
    abstract fun practiceResultDao(): PracticeResultDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: VocletDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create settings table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id INTEGER PRIMARY KEY NOT NULL,
                        themeMode TEXT NOT NULL DEFAULT 'SYSTEM'
                    )
                    """.trimIndent()
                )

                // Insert default settings
                database.execSQL(
                    """
                    INSERT INTO app_settings (id, themeMode) VALUES (1, 'SYSTEM')
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN ttsEnabledByDefault INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN ttsLanguageOverrides TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN aiBackend TEXT NOT NULL DEFAULT 'AUTO'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN aiHintShown INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): VocletDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VocletDatabase::class.java,
                    "voclet_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
