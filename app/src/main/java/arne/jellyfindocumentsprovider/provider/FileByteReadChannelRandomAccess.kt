package arne.jellyfindocumentsprovider.provider

import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.hacks.readable
import arne.jellyfindocumentsprovider.vfs.FileStreamFactory
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import arne.jellyfindocumentsprovider.vfs.getOrCreate
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
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
        private const val IDLE_TIMEOUT_MS = 30_000L
        private const val BUFFER_AHEAD = 16L * 1024L * 1024L
    }

    override val length: Long = virtualFile.item.target.size
    private val cacheInfo =
        ObjectBox.cacheInfo.getOrCreate(virtualFile, file.absolutePath).also {
            it.persistCallback = { ci -> ObjectBox.cacheInfo.put(ci) }
        }
    private val cache = cacheInfo.cacheFile

    private val name: String = virtualFile.item.target.name
    private val docId = virtualFile.documentId
    private val bytesCached = AtomicLong(
        cache.sumOf { it.last - it.first + 1 }
    )
    @Volatile private var activeDownloaders = 0

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
                        if (offset + bytesRead > maxReadOffset) {
                            maxReadOffset = offset + bytesRead
                        }
                        return bytesRead
                    }
                }
            }

            synchronized(lock) {
                if (closed) return -1
                scheduleSeekDownload(offset)
                lock.wait(2000)
            }
            waited += 2000

            if (waited >= 2000 && waited % 2000 == 0) {
                val elapsed = System.currentTimeMillis() - startTime
                logcat(LogPriority.DEBUG) {
                    "[$traceId] read offset=${offset.readable} size=$size STALLED ${elapsed}ms, primary=${
                        primaryPos.readable
                    }, chunks=${
                        synchronized(cache) { cache.toList().map { "${it.first.readable}..${it.last.readable}" } }
                    }"
                }
            }
        }
    }

    private fun scheduleSeekDownload(offset: Long) {
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

        if (downloading.values.any { chunkStart in it }) return

        if (seekers.size >= MAX_SEEK_DOWNLOADERS) {
            val oldest = seekers.keys.min()
            seekers[oldest]?.cancel()
            seekers.remove(oldest)
            downloading.remove(oldest)
        }

        downloading[chunkStart] = chunkStart..minOf(chunkStart + PREFETCH_SIZE - 1, length - 1)

        seekers[chunkStart] = launch {
            StatusEventManager.startNetwork(docId, "Downloading $name")
            activeDownloaders++
            try {
                runSeekDownloader(chunkStart)
            } finally {
                activeDownloaders--
                StatusEventManager.finishNetwork(docId)
                synchronized(lock) {
                    downloading.remove(chunkStart)
                    seekers.remove(chunkStart)
                }
            }
        }
    }

    private suspend fun runPrimaryDownloader() {
        StatusEventManager.startNetwork(docId, "Downloading $name")
        activeDownloaders++
        try {
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
                logcat(LogPriority.DEBUG) {
                    "[$traceId] primary requesting stream at pos=${pos.readable}"
                }
                val stream = fileStreamFactory(pos, null)
                val inputStream = stream.inputStream
                val end = length - 1
                currentPrimaryRange = pos..end
                logcat(LogPriority.INFO) {
                    "[$traceId] primary stream opened pos=${pos.readable} serverRange=${
                        stream.range?.let { "${it.first.readable}..${it.last.readable}" } ?: "none"
                    } contentLen=${stream.length.readable} end=${end.readable}"
                }
                val buf = ByteArray(32 * 1024)

                var writeCount = 0L
                var bytesThisSegment = 0L
                var seekerDeferred = false
                while (isActive && pos <= end) {
                    val n = inputStream.read(buf)
                    if (n < 0) break
                    cache.write(pos, buf, n)
                    bytesCached.addAndGet(n.toLong())
                    writeCount++
                    bytesThisSegment += n
                    synchronized(lock) { lock.notifyAll() }
                    if (writeCount % 32L == 0L) {
                        persistCacheInfo()
                        val cached = bytesCached.get()
                        val pct = if (length > 0) cached.toFloat() / length else -1f
                        StatusEventManager.updateNetwork(
                            docId, "${name} ${cached.readable} / ${length.readable}", pct
                        )
                    }
                    pos += n
                    primaryPos = pos
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
                inputStream.close()
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
            val finalCached = bytesCached.get()
            val finalPct = if (length > 0) finalCached.toFloat() / length else -1f
            StatusEventManager.updateNetwork(
                docId, "${name} ${finalCached.readable} / ${length.readable}", finalPct
            )
    } finally {
        persistCacheInfo()
        activeDownloaders--
        StatusEventManager.finishNetwork(docId)
    }
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
            val inputStream = stream.inputStream
            logcat(LogPriority.INFO) {
                "[$traceId] seek stream opened pos=${pos.readable} serverRange=${
                    stream.range?.let { "${it.first.readable}..${it.last.readable}" } ?: "none"
                } contentLen=${stream.length.readable}"
            }
            val buf = ByteArray(32 * 1024)

            var writeCount = 0L
            var bytesThisSeg = 0L
            while (isActive && pos <= prefetchEnd) {
                val n = inputStream.read(buf)
                if (n < 0) break
                cache.write(pos, buf, n)
                bytesCached.addAndGet(n.toLong())
                writeCount++
                bytesThisSeg += n
                synchronized(lock) { lock.notifyAll() }
                if (writeCount % 32L == 0L) {
                    persistCacheInfo()
                    val cached = bytesCached.get()
                    val pct = if (length > 0) cached.toFloat() / length else -1f
                    StatusEventManager.updateNetwork(
                        docId, "${name} ${cached.readable} / ${length.readable}", pct
                    )
                }
                pos += n
            }
            inputStream.close()
            logcat(LogPriority.DEBUG) {
                "[$traceId] seek done pos=${pos.readable} writes=$writeCount bytes=${bytesThisSeg.readable}"
            }
            val finalCached = bytesCached.get()
            val pct = if (length > 0) finalCached.toFloat() / length else -1f
            StatusEventManager.updateNetwork(
                docId, "${name} ${finalCached.readable} / ${length.readable}", pct
            )
        } catch (e: CancellationException) {
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "[$traceId] Seek error at ${chunkStart.readable}: ${e.message}"
            }
        }
        persistCacheInfo()
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
            logcat(LogPriority.DEBUG) { "[$traceId] persistCacheInfo failed: ${e.javaClass.simpleName}: ${e.message}" }
        }
    }

    private suspend fun runIdleWatchdog() {
        while (isActive) {
            delay(30_000)
            val idleMs = System.currentTimeMillis() - lastReadTime
            if (idleMs > IDLE_TIMEOUT_MS) {
                logcat(LogPriority.DEBUG) {
                    "[$traceId] idle for ${idleMs}ms (> ${IDLE_TIMEOUT_MS}ms), cancelling all downloads"
                }
                coroutineContext[Job]?.cancel()
                break
            }
        }
    }
}
