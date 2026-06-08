package arne.jellyfindocumentsprovider.vfs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne

@Entity
data class ItemRecord(
    @Id var id: Long = 0,
    @Index val documentId: String = "",
    val name: String = "",
    val mimeType: String = "",
    val displayName: String = "",
    val lastModified: Long = 0,
    val size: Long = 0,
    val duration: Long? = null,
    val year: Int? = null,
    val title: String? = null,
    val album: String? = null,
    val track: Int? = null,
    val artist: String? = null,
    val bitrate: Int? = null,
    @Index val albumId: String? = null,
    val albumCoverTag: String? = null,
    val thumbCacheId: Long = 0,
) {
    lateinit var thumbCache: ToOne<ThumbCache>
}
