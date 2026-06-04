package arne.jellyfindocumentsprovider.provider

import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.hacks.readable
import arne.jellyfindocumentsprovider.vfs.FileStreamFactory
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import arne.jellyfindocumentsprovider.vfs.getOrCreate
import io.ktor.utils.io.readAvailable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat

class FileByteReadChannelRandomAccess(
    file: File,
    virtualFile: VirtualFile,
    private val fileStreamFactory: FileStreamFactory,
    private val traceId: String,
) : RandomAccess() {
    companion object {
        var chunkSize: Long = 1048576L
        private const val MAX_SEEK_DOWNLOADERS = 10
        private val PREFETCH_SIZE
            get() = 8 * chunkSize
        private const val INITIAL_CHUNK_SIZE = 512L * 1024L
        private const val PRIMARY_CHUNK_SIZE = 4L * 1024L * 1024L
        private const val IDLE_TIMEOUT_MS = 30_000L
        private const val BUFFER_AHEAD = 16L * 1024L * 1024L
    }

    override val length: Long = virtualFile.size
    private val cacheInfo =
        ObjectBox.cacheInfo.getOrCreate(virtualFile, file.absolutePath).also {
            it.persistCallback = { ci -> ObjectBox.cacheInfo.put(ci) }
        }
    private val cache = cacheInfo.cacheFile

    private val docId = virtualFile.documentId
    private var totalBytesRead = 0L
    private var lastNotificationTime = 0L
    private val notificationIntervalMs = 1000L

    private val lock = java.lang.Object()
    @Volatile private var closed = false

    private val seekers = HashMap<Long, Job>()
    private val downloading = HashMap<Long, LongRange>()
    @Volatile private var primaryPos = 0L
    @Volatile private var lastReadTime = System.currentTimeMillis()
    @Volatile private var maxReadOffset = 0L
    @Volatile private var currentPrimaryRange: LongRange? = null

    init {
        logcat(LogPriority.INFO) {
            "[$traceId] RA created, size=${length.readable}, chunks=${cacheInfo.chunks}"
        }
        launch { runPrimaryDownloader() }
        launch { runIdleWatchdog() }
    }

    override fun read(offset: Long, size: Int, data: ByteArray): Int {
        if (offset < 0 || size < 0 || offset > length) return -1

        lastReadTime = System.currentTimeMillis()
        var waited = 0
        val startTime = System.currentTimeMillis()

        while (true) {
            val chunk = cache.offsetInChunks(offset)
            if (chunk != null) {
                val canFulfill = chunk.last + 1 >= offset + size
                val isEof = chunk.last + 1 >= length
                if (canFulfill || isEof) {
                    val availableEnd = minOf(chunk.last + 1, offset + size)
                    val available = (availableEnd - offset).toInt()
                    if (available > 0) {
                        val bytesRead = cache.read(offset, available, data)
                        totalBytesRead += bytesRead
                        if (offset + bytesRead > maxReadOffset) {
                            maxReadOffset = offset + bytesRead
                        }
                        maybeNotify()
                        if (waited > 0) {
                            logcat(LogPriority.INFO) {
                                "[$traceId] read offset=${offset.readable} size=$size → $bytesRead (waited ${waited}ms, cache chunk ${chunk.first.readable}..${chunk.last.readable})"
                            }
                        } else {
                            logcat(LogPriority.VERBOSE) {
                                "[$traceId] read offset=${offset.readable} size=$size → $bytesRead (cache hit)"
                            }
                        }
                        return bytesRead
                    }
                }
            }

            synchronized(lock) {
                if (closed) return -1
                scheduleSeekDownload(offset, force = true)
                lock.wait(200)
            }
            waited += 200

            if (waited >= 2000 && waited % 2000 == 0) {
                val elapsed = System.currentTimeMillis() - startTime
                logcat(LogPriority.WARN) {
                    "[$traceId] read offset=${offset.readable} size=$size STALLED ${elapsed}ms, primary=${
                        primaryPos.readable
                    }, chunks=${
                        synchronized(cache) { cache.toList().map { "${it.first.readable}..${it.last.readable}" } }
                    }"
                }
            }
        }
    }

    private fun scheduleSeekDownload(offset: Long, force: Boolean = false) {
        var chunkStart = (offset / chunkSize) * chunkSize
        if (chunkStart == 0L) return

        val primaryRange = currentPrimaryRange
        if (primaryRange != null && chunkStart <= primaryRange.last) {
            if (primaryRange.last - chunkStart < PREFETCH_SIZE) {
                val adjusted = ((primaryRange.last + 1) / chunkSize) * chunkSize
                if (adjusted >= length) return
                if (adjusted > chunkStart) chunkStart = adjusted
            }
        }

        if (seekers.containsKey(chunkStart)) return

        if (!force && primaryPos <= chunkStart && chunkStart - primaryPos < PREFETCH_SIZE) return

        if (downloading.values.any { chunkStart in it }) return

        if (seekers.size >= MAX_SEEK_DOWNLOADERS) {
            val oldest = seekers.keys.min()
            seekers[oldest]?.cancel()
            seekers.remove(oldest)
            downloading.remove(oldest)
        }

        downloading[chunkStart] = chunkStart..minOf(chunkStart + PREFETCH_SIZE - 1, length - 1)

        seekers[chunkStart] = launch {
            try {
                runSeekDownloader(chunkStart)
            } finally {
                synchronized(lock) {
                    downloading.remove(chunkStart)
                    seekers.remove(chunkStart)
                }
            }
        }
    }

    private suspend fun runPrimaryDownloader() {
        var pos = 0L
        logcat(LogPriority.INFO) { "[$traceId] primary starting, length=${length.readable}" }
        while (isActive && pos < length) {
            while (isActive && pos > maxReadOffset + BUFFER_AHEAD) {
                delay(250)
            }
            if (!isActive) break

            val cached = cache.offsetInChunks(pos)
            if (cached != null) {
                pos = maxOf(pos, cached.last + 1)
                primaryPos = pos
                if (pos >= length) break
                continue
            }

            val skipped =
                    synchronized(lock) {
                        downloading.values.firstOrNull { pos in it }?.let { it.last + 1 }
                    }
            if (skipped != null) {
                pos = skipped
                primaryPos = pos
                continue
            }

            try {
                val chunkEnd = if (pos == 0L) INITIAL_CHUNK_SIZE else PRIMARY_CHUNK_SIZE
                val endBound = minOf(pos + chunkEnd - 1, length - 1)
                logcat(LogPriority.DEBUG) {
                    "[$traceId] primary requesting stream at pos=${pos.readable} end=${endBound.readable}"
                }
                val stream = fileStreamFactory(pos, endBound)
                val channel = stream.channel
                val end =
                        stream.range?.last
                                ?: (pos + stream.length - 1).let {
                                    if (it < pos) length - 1 else it
                                }
                currentPrimaryRange = pos..end
                logcat(LogPriority.INFO) {
                    "[$traceId] primary stream opened pos=${pos.readable} serverRange=${
                        stream.range?.let { "${it.first.readable}..${it.last.readable}" } ?: "none"
                    } contentLen=${stream.length.readable} end=${end.readable}"
                }
                val buf = ByteArray(32 * 1024)

                var nextNotifyChunk = (pos / chunkSize).toLong()
                var writeCount = 0L
                var bytesThisSegment = 0L
                var seekerDeferred = false
                while (isActive && pos <= end) {
                    val n = channel.readAvailable(buf)
                    if (n < 0) break
                    if (n == 0) {
                        delay(10)
                        continue
                    }
                    cache.write(pos, buf, n)
                    val writeEnd = pos + n - 1
                    writeCount++
                    bytesThisSegment += n
                    synchronized(lock) { lock.notifyAll() }
                    if (writeCount % 8L == 0L) {
                        persistCacheInfo()
                    }
                    pos += n
                    primaryPos = pos
                    val currentChunk = pos / chunkSize
                    if (currentChunk != nextNotifyChunk) {
                        nextNotifyChunk = currentChunk
                        logcat(LogPriority.VERBOSE) {
                            "[$traceId] primary ${pos.readable} (chunk $currentChunk, ${writeCount}w/${bytesThisSegment.readable} this segment)"
                        }
                    }
                    val seekerEnd =
                            synchronized(lock) {
                                downloading.values.firstOrNull { pos in it }?.last
                            }
                    if (seekerEnd != null && pos <= seekerEnd && pos < end) {
                        seekerDeferred = true
                        logcat(LogPriority.DEBUG) {
                            "[$traceId] primary deferred seek at pos=${pos.readable} end=${seekerEnd.readable}"
                        }
                        break
                    }
                }
                currentPrimaryRange = null
                logcat(LogPriority.DEBUG) {
                    "[$traceId] primary stream done pos=${pos.readable} writes=$writeCount bytes=${bytesThisSegment.readable}" +
                            if (seekerDeferred) {
                                " (seeker deferred)"
                            } else ""
                }
            } catch (e: CancellationException) {
                currentPrimaryRange = null
                break
            } catch (e: Exception) {
                currentPrimaryRange = null
                logcat(LogPriority.ERROR) {
                    "[$traceId] Primary error at ${pos.readable}: ${e.message}"
                }
                delay(1000)
            }
        }
        logcat(LogPriority.INFO) { "[$traceId] primary finished, pos=${pos.readable}" }
    }

    private suspend fun runSeekDownloader(chunkStart: Long) {
        val prefetchEnd = minOf(chunkStart + PREFETCH_SIZE - 1, length - 1)
        var pos = chunkStart
        logcat(LogPriority.DEBUG) {
            "[$traceId] seek starting range ${pos.readable}..${prefetchEnd.readable}"
        }
        try {
            val cached = cache.offsetInChunks(pos)
            if (cached != null) {
                pos = maxOf(pos, cached.last + 1)
                if (pos > prefetchEnd) return
            }

            val cachedAfter = cache.offsetInChunks(pos)
            if (cachedAfter != null) {
                pos = maxOf(pos, cachedAfter.last + 1)
                if (pos > prefetchEnd) return
            }

            logcat(LogPriority.DEBUG) {
                "[$traceId] seek requesting stream pos=${pos.readable} end=${prefetchEnd.readable}"
            }
            val stream = fileStreamFactory(pos, prefetchEnd)
            val channel = stream.channel
            logcat(LogPriority.INFO) {
                "[$traceId] seek stream opened pos=${pos.readable} serverRange=${
                    stream.range?.let { "${it.first.readable}..${it.last.readable}" } ?: "none"
                } contentLen=${stream.length.readable}"
            }
            val buf = ByteArray(32 * 1024)

            var writeCount = 0L
            var bytesThisSeg = 0L
            while (isActive && pos <= prefetchEnd) {
                val n = channel.readAvailable(buf)
                if (n < 0) break
                if (n == 0) {
                    delay(10)
                    continue
                }
                cache.write(pos, buf, n)
                writeCount++
                bytesThisSeg += n
                synchronized(lock) { lock.notifyAll() }
                if (writeCount % 8L == 0L) {
                    persistCacheInfo()
                }
                pos += n
            }
            logcat(LogPriority.DEBUG) {
                "[$traceId] seek done pos=${pos.readable} writes=$writeCount bytes=${bytesThisSeg.readable}"
            }
        } catch (e: CancellationException) {
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "[$traceId] Seek error at ${chunkStart.readable}: ${e.message}"
            }
        }
    }

    override fun close() {
        logcat(LogPriority.INFO) {
            "[$traceId] RA close() called, primaryPos=${primaryPos.readable}"
        }
        synchronized(lock) {
            closed = true
            lock.notifyAll()
        }
        runBlocking { coroutineContext[Job]?.cancelAndJoin() }
        cacheInfo.close()
        logcat(LogPriority.INFO) { "[$traceId] RA close() done" }
    }

    private fun persistCacheInfo() {
        try {
            cacheInfo.persistCallback?.invoke(cacheInfo.copy(chunks = cache))
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "[$traceId] persistCacheInfo failed: ${e.message}" }
        }
    }

    private fun maybeNotify() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationTime >= notificationIntervalMs) {
            lastNotificationTime = now
            val pct = if (length > 0) totalBytesRead.toFloat() / length else -1f
            StatusEventManager.updateNetwork(
                    docId,
                    "Streamed ${totalBytesRead.readable} / ${length.readable}",
                    pct
            )
        }
    }

    private suspend fun runIdleWatchdog() {
        while (isActive) {
            delay(10_000)
            val idleMs = System.currentTimeMillis() - lastReadTime
            if (idleMs > IDLE_TIMEOUT_MS) {
                logcat(LogPriority.WARN) {
                    "[$traceId] idle for ${idleMs}ms (> ${IDLE_TIMEOUT_MS}ms), cancelling all downloads"
                }
                coroutineContext[Job]?.cancel()
                break
            }
        }
    }
}
