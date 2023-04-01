package a.sac.jellyfindocumentsprovider

import kotlinx.serialization.Serializable

data class ServerInfo(
    var baseUrl: String = "",
    var username: String = "",
    var password: String = ""
)

data class MediaLibraryListItem(
    var checked: Boolean,
    val name: String,
    val id: String
)

@Serializable
data class MediaInfo(
    val duration: Long,
    val year: Int,
    val title: String,
    val album: String,
    val track: Int,
    val artist: String,
    val bitrate: Int,
    val thumbnail: String?
)

val Any.TAG: String
    get() {
        return if (!javaClass.isAnonymousClass) {
            val name = javaClass.simpleName
            if (name.length <= 23) name else name.substring(0, 23)// first 23 chars
        } else {
            val name = javaClass.name
            if (name.length <= 23) name else name.substring(
                name.length - 23,
                name.length
            )// last 23 chars
        }
    }