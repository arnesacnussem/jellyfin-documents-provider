package arne.jellyfindocumentsprovider.ui.browser

import arne.jellyfindocumentsprovider.vfs.CacheChunks
import arne.jellyfindocumentsprovider.vfs.CacheInfo

enum class ThumbStatus { CACHED, NOT_CACHED, NONE }

enum class LyricsStatus { CACHED, NOT_CACHED }

data class CacheBrowserEntry(
    val virtualFileId: Long,
    val documentId: String,
    val itemId: String,
    val serverId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String?,
    val duration: Long?,
    val size: Long,
    val fileCacheStatus: CacheStatus,
    val thumbStatus: ThumbStatus,
    val lyricsStatus: LyricsStatus,
    val cacheInfoId: Long?,
    val localPath: String?,
)

sealed class CacheStatus {
    data object NotCached : CacheStatus()
    data class Partial(val cachedSize: Long, val totalSize: Long, val chunks: CacheChunks) : CacheStatus()
    data class Complete(val cachedSize: Long, val totalSize: Long) : CacheStatus()
}

fun CacheInfo.toCacheStatus(itemSize: Long): CacheStatus {
    val chunksList = chunks.toList()
    val cached = if (chunksList.isNotEmpty()) chunksList.sumOf { it.last - it.first + 1 }
                 else localLength
    return when {
        isComplete -> CacheStatus.Complete(cached, itemSize)
        cached > 0 -> CacheStatus.Partial(cached, itemSize, chunks)
        else -> CacheStatus.NotCached
    }
}

fun CacheStatus.cachedBytes(): Long = when (this) {
    CacheStatus.NotCached -> 0
    is CacheStatus.Partial -> cachedSize
    is CacheStatus.Complete -> cachedSize
}

fun CacheStatus.progressPercent(): Float = when (this) {
    CacheStatus.NotCached -> 0f
    is CacheStatus.Partial -> if (totalSize > 0) cachedSize.toFloat() / totalSize else 0f
    is CacheStatus.Complete -> 1f
}
