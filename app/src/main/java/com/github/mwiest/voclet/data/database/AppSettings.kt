package com.github.mwiest.voclet.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Long = 1L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettings)
}

class Converters {
    @TypeConverter
    fun fromThemeMode(value: ThemeMode): String = value.name

    @TypeConverter
    fun toThemeMode(value: String): ThemeMode = ThemeMode.valueOf(value)
}
