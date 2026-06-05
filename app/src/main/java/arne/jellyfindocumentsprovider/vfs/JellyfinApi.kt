package arne.jellyfindocumentsprovider.vfs

import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import java.io.InputStream

typealias FileStreamFactory = suspend (start: Long, end: Long?) -> JellyfinApi.Stream

interface JellyfinApi {
    data class Stream(
        val inputStream: InputStream,
        val length: Long,
        val type: Type,
        val range: LongRange?
    ) {
        enum class Type {
            FILE, AUDIO_STREAM
        }
    }

    suspend fun queryAudioItems(parentId: String, startIndex: Int = 0, limit: Int = 100): BaseItemDtoQueryResult?

    suspend fun downloadThumbnail(itemId: String, width: Int? = 250, height: Int? = 250): ByteArray?

    suspend fun streamThumbnail(itemId: String, width: Int? = 250, height: Int? = 250): Stream?

    suspend fun getAudioStreamFactory(itemId: String, bps: Int): FileStreamFactory

    fun getDownloadStreamFactory(itemId: String): FileStreamFactory

    suspend fun getItemNameById(id: String): String?

    suspend fun getLyrics(itemId: String): String?

    suspend fun reportPlaybackStart(itemId: String, playSessionId: String? = null)

    suspend fun reportPlaybackStopped(itemId: String, playSessionId: String? = null, positionTicks: Long? = null)
}
