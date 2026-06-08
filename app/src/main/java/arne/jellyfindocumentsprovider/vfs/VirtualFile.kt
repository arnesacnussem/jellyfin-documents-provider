package arne.jellyfindocumentsprovider.vfs

import android.webkit.MimeTypeMap
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.ZoneOffset

@Entity
data class VirtualFile(
    @Id var id: Long = 0,
    @Index val documentId: String,
    @Index val libId: String,
    @Index val serverId: Long = 0,
    val albumId: String?,
    val isFavorite: Boolean = false,
    val providerId: String = "",
    val powerampExtId: Long = 0,
    val itemId: Long = 0,
) {
    lateinit var server: ToOne<JellyfinServer>
    lateinit var item: ToOne<ItemRecord>

    companion object {
        fun BaseItemDto.toVirtualFile(credential: JellyfinServer, libId: String, itemRecord: ItemRecord): VirtualFile {
            val documentId = id?.asString() ?: ""
            return VirtualFile(
                documentId = documentId,
                libId = libId,
                albumId = albumId?.asString(),
                providerId = VPath.File(credential.uuid, libId, albumId?.asString(), documentId)
                    .toString(),
                isFavorite = userData?.isFavorite ?: false,
            ).also {
                it.server.target = credential
                it.item.target = itemRecord
            }
        }

        fun BaseItemDto.toItemRecord(): ItemRecord {
            val mediaSource = mediaSources?.first()
            val documentId = id?.asString() ?: ""
            return ItemRecord(
                documentId = documentId,
                name = name ?: "Unknown",
                mimeType = (mediaSource?.container ?: container).toMIMEType(),
                displayName = name ?: "Unknown",
                lastModified = 1000 * (dateCreated?.toEpochSecond(ZoneOffset.UTC) ?: 0),
                size = mediaSource?.size ?: 0,
                duration = (runTimeTicks ?: 0) / 10000,
                year = productionYear,
                title = name,
                album = album,
                track = indexNumber ?: 0,
                artist = artists?.joinToString(", ") ?: "",
                bitrate = mediaSources?.firstOrNull()?.bitrate ?: 0,
                albumId = albumId?.asString(),
                albumCoverTag = albumPrimaryImageTag,
            )
        }

        private val mimeTypeCache = HashMap<String, String>()

        private fun String?.toMIMEType(): String {
            if (this == null) return "application/octet-stream"
            return mimeTypeCache.getOrPut(this) {
                return@getOrPut MimeTypeMap.getSingleton().getMimeTypeFromExtension(this)
                    ?: "application/octet-stream"
            }
        }
    }
}
