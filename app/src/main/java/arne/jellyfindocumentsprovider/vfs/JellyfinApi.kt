package arne.jellyfindocumentsprovider.vfs

import io.ktor.utils.io.ByteReadChannel
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult

typealias FileStreamFactory = suspend (start: Long, end: Long?) -> JellyfinApi.Stream

interface JellyfinApi {
    /**
     * Stream types returned by file/audio stream operations.
     */
    data class Stream(
        val channel: ByteReadChannel,
        val length: Long,
        val type: Type,
        val range: LongRange?
    ) {
        enum class Type {
            FILE, AUDIO_STREAM
        }
    }

    /** Query audio items from a library with pagination */
    suspend fun queryAudioItems(parentId: String, startIndex: Int = 0, limit: Int = 100): BaseItemDtoQueryResult?

    /** Download a thumbnail image as byte array. Returns null if not found. */
    suspend fun downloadThumbnail(itemId: String, width: Int? = 250, height: Int? = 250): ByteArray?

    /** Stream a thumbnail image. Returns null if not found. */
    suspend fun streamThumbnail(itemId: String, width: Int? = 250, height: Int? = 250): Stream?

    /** Get an audio stream factory for transcoded audio streaming */
    suspend fun getAudioStreamFactory(itemId: String, bps: Int): FileStreamFactory

    /** Get a file download stream factory (raw file download via Ktor) */
    fun getDownloadStreamFactory(itemId: String): FileStreamFactory

    /** Get item name by UUID */
    suspend fun getItemNameById(id: String): String?
}
