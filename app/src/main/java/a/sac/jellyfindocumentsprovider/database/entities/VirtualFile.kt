package a.sac.jellyfindocumentsprovider.database.entities

import a.sac.jellyfindocumentsprovider.MediaInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vFile")
data class VirtualFile(
    @PrimaryKey val id: String,
    val mimeType: String,
    val displayName: String,
    val lastModified: Long,
    val flags: Int,
    val size: Long,

    /**
     * Library ID
     */
    val lid: String,
    /**
     * User ID
     */
    val uid: String,

    val mediaInfo: MediaInfo
)