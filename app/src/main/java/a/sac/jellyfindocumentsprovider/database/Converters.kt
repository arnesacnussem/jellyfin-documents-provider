package a.sac.jellyfindocumentsprovider.database

import a.sac.jellyfindocumentsprovider.MediaInfo
import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class Converters {
    @TypeConverter
    fun fromMap(value: Map<String, String>) = Json.encodeToString(value)

    @TypeConverter
    fun toMap(value: String) = Json.decodeFromString<Map<String, String>>(value)

    @TypeConverter
    fun fromMediaInfo(value: MediaInfo) = Json.encodeToString(value)

    @TypeConverter
    fun toMediaInfo(value: String) = Json.decodeFromString<MediaInfo>(value)
}