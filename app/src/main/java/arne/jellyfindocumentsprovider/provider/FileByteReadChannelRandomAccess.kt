package arne.jellyfindocumentsprovider.provider

import arne.jellyfindocumentsprovider.hacks.short
import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.FileStreamFactory
import arne.jellyfindocumentsprovider.vfs.JellyfinAccessor
import arne.jellyfindocumentsprovider.vfs.JellyfinApi
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import arne.jellyfindocumentsprovider.vfs.getOrCreate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat
import java.io.Closeable
import java.io.File
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class FileByteReadChannelRandomAccess(
    file: File,
    virtualFile: VirtualFile,
    private val fileStreamFactory: FileStreamFactory,
) : RandomAccess() {
    override val length: Long = virtualFile.size
    private var currentJob: Job? = null
    private val logId = virtualFile.documentId.short
    private var channel: FileByteReadChannel? = null
    private val cacheInfo = ObjectBox.cacheInfo.getOrCreate(virtualFile, file.absolutePath)
    private val cache = cacheInfo.cacheFile

    override fun read(offset: Long, size: Int, data: ByteArray): Int {
        if (offset < 0 || size < 0 || offset > length) {
            return -1
        }

        // check if already in cache
        val chunk = cache.offsetInChunks(offset)
        val cacheType = if (chunk == null) {
            ReadType.NO_CACHE
        } else if (chunk.last + 1 >= offset + size) {
            ReadType.ALL_CACHE
        } else {
            ReadType.PARTIAL_CACHE
        }

        logcat(LogPriority.VERBOSE) {
            "read $logId: offset=$offset, size=$size, cacheType=$cacheType, cacheChunk=$chunk"
        }

        when (cacheType) {
            ReadType.NO_CACHE -> {
                return getOrCreateChannelFor(offset).read(offset..offset + size, data, 0)
            }

            ReadType.PARTIAL_CACHE -> {
                val fromDisk = ByteArray((chunk!!.last - offset).toInt())
                val fromDiskSize = read(offset, fromDisk.size, fromDisk)
                fromDisk.copyInto(data, 0, 0, fromDiskSize)

                val restRange = offset + fromDiskSize..offset + size
                return getOrCreateChannelFor(restRange.first).read(restRange, data, fromDiskSize)
            }

            ReadType.ALL_CACHE -> {
                return cache.read(
                    offset,
                    size,
                    data
                )
            }
        }
    }

    @Synchronized
    private fun getOrCreateChannelFor(start: Long): FileByteReadChannel {
        if (channel != null) {
            if (channel!!.availableForPosition(start)) {
                return channel!!
            } else {
                val stream = openStream(start)
                channel?.close()
                channel = FileByteReadChannel(
                    stream.channel,
                    cache,
                    effectiveRange(start, stream)
                )
            }
        } else {
            val stream = openStream(start)
            channel = FileByteReadChannel(
                stream.channel,
                cache,
                effectiveRange(start, stream)
            )
        }

        return channel!!
    }

    private fun openStream(start: Long): JellyfinApi.Stream = try {
        runBlocking { fileStreamFactory(start, null) }
    } catch (e: Exception) {
        logcat(LogPriority.ERROR) { "Failed to create stream: ${e.message}" }
        throw RuntimeException("Failed to open stream", e)
    }

    private fun effectiveRange(start: Long, stream: JellyfinApi.Stream): LongRange {
        return stream.range ?: if (stream.length > 0) {
            start..(start + stream.length - 1)
        } else {
            start..Long.MAX_VALUE
        }
    }

    override fun close() {
        currentJob?.cancel(cause = CancelCauseCloseException())
        coroutineContext.cancelChildren()
        channel?.close()
        cacheInfo.close()
    }

    inner class CancelCauseNewRangeException : CancellationException()
    inner class CancelCauseCloseException : CancellationException()
}

private enum class ReadType {
    NO_CACHE, PARTIAL_CACHE, ALL_CACHE
}

class FileByteReadChannel(
    private val channel: ByteReadChannel,
    private val cacheFile: CacheInfo.CacheFile,
    private val channelRange: LongRange,
) : ByteReadChannel by channel, Closeable {
    @Volatile
    private var currentPosition = channelRange.first

    /**
     * @param range range of the actual file
     * @param data [RandomAccess.read]
     * @param dataOffset offset in [data]
     */
    @Synchronized
    fun read(range: LongRange, data: ByteArray, dataOffset: Int): Int {
        val size: Int = min((range.last - range.first), channelRange.last - currentPosition).toInt()
        val buffer = ByteArray(size)
        try {
            runBlocking { channel.readFully(buffer, 0, size) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to read from channel: ${e.message}" }
            return -1
        }

        cacheFile.write(range.first, buffer)
        buffer.copyInto(data, dataOffset, 0, buffer.size)

        return buffer.size
    }

    fun availableForPosition(pos: Long): Boolean {
        return pos in (currentPosition..channelRange.last)
    }

    override fun close() {
        channel.cancel()
    }
}