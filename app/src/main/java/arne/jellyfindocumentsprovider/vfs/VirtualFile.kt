package arne.jellyfindocumentsprovider.vfs

import android.webkit.MimeTypeMap
import io.objectbox.annotation.Entity
import logcat.logcat
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.ZoneOffset

@Entity
data class VirtualFile(
    @Id var id: Long = 0,
    val name: String,
    @Index val documentId: String,

    // attributes
    val mimeType: String,
    val displayName: String,
    val lastModified: Long,
    val size: Long,

    // links
    @Index val libId: String,
    @Index val serverId: Long = 0,
    val thumbCacheId: Long = 0,

    val powerampExtId: Long = 0,

    // previous in media info
    val duration: Long?,
    val year: Int?,
    val title: String?,
    val album: String?,
    val track: Int?,
    val artist: String?,
    val bitrate: Int?,
    val albumId: String?,
    val albumCoverTag: String?,

    val isFavorite: Boolean = false,

    // following is for improve performance
    val providerId: String = "",
) {
    lateinit var server: ToOne<JellyfinServer>
    lateinit var thumbCache: ToOne<ThumbCache>

    companion object {
        fun BaseItemDto.toVirtualFile(credential: JellyfinServer, libId: String): VirtualFile {
            val mediaSource = mediaSources?.first()
            val documentId = id?.asString() ?: ""
            return VirtualFile(
                name = name ?: "Unknown",
                documentId = documentId,
                mimeType = (mediaSource?.container ?: container).toMIMEType().also { mime ->
                    logcat { "toVirtualFile: name=$name container=${mediaSource?.container ?: container} mimeType=$mime" }
                },
                displayName = name ?: "Unknown",
                lastModified = 1000 * (dateCreated?.toEpochSecond(ZoneOffset.UTC) ?: 0),
                size = mediaSource?.size ?: 0,
                libId = libId,
                providerId = VPath.File(credential.uuid, libId, albumId?.asString(), documentId)
                    .toString(),

                // media info
                duration = (runTimeTicks ?: 0) / 10000,
                year = productionYear,
                title = name,
                album = album,
                track = indexNumber ?: 0,
                artist = artists?.joinToString(", ") ?: "",
                bitrate = mediaSources?.firstOrNull()?.bitrate ?: 0,
                albumId = albumId?.asString(),
                albumCoverTag = albumPrimaryImageTag,
                isFavorite = userData?.isFavorite ?: false,
            ).also {
                it.server.target = credential
            }
        }

        private val mimeTypeCache = HashMap<String, String>()

        private fun String?.toMIMEType(): String {
            if (this == null) return "application/octet-stream"
            return mimeTypeCache.getOrPut(this) {
                // Get the MIME type for the extension using the MimeTypeMap class
                return@getOrPut MimeTypeMap.getSingleton().getMimeTypeFromExtension(this)
                    ?: "application/octet-stream"
            }
        }
    }
}
