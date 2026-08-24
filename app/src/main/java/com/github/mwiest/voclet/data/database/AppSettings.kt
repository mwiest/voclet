package com.github.mwiest.voclet.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import com.github.mwiest.voclet.data.ai.CloudProvider
import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Long = 1L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val ttsEnabledByDefault: Boolean = true,
    val ttsLanguageOverrides: Map<String, String> = emptyMap(),
    val aiHintShown: Boolean = false,
    // Cloud AI is bring-your-own-key: which preset is selected, plus the
    // endpoint override, key and model. A blank base URL or model means
    // "use the preset default" (see CloudProvider).
    val aiCloudProvider: CloudProvider = CloudProvider.GEMINI,
    val aiCloudBaseUrl: String = "",
    val aiCloudApiKey: String = "",
    val aiCloudModel: String = ""
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

    @TypeConverter
    fun fromCloudProvider(value: CloudProvider): String = value.name

    @TypeConverter
    fun toCloudProvider(value: String): CloudProvider = CloudProvider.valueOf(value)

    @TypeConverter
    fun fromLanguageOverrides(map: Map<String, String>): String =
        map.entries.joinToString(",") { "${it.key}=${it.value}" }

    @TypeConverter
    fun toLanguageOverrides(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return value.split(",").associate { entry ->
            val idx = entry.indexOf('=')
            entry.substring(0, idx) to entry.substring(idx + 1)
        }
    }
}
