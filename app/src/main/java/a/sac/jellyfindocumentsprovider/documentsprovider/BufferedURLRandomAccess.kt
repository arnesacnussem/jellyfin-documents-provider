package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.database.ObjectBox
import a.sac.jellyfindocumentsprovider.database.entities.CacheInfo
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.utils.SortedLongRangeList
import a.sac.jellyfindocumentsprovider.utils.TAG
import a.sac.jellyfindocumentsprovider.utils.logcat
import a.sac.jellyfindocumentsprovider.utils.readable
import a.sac.jellyfindocumentsprovider.utils.short
import io.ktor.util.reflect.instanceOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.*
import logcat.LogPriority
import logcat.LogPriority.DEBUG
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * TODO: add the ability to limit buffer file size
 */
class BufferedURLRandomAccess(
    val vf: VirtualFile,
    private val url: URL,
    bufferSizeKB: Int = 128,
    private val bufferFile: File,
    private val maxRetry: Int = 3,
    private val bitrate: Int = -1
) : URLRandomAccess(url), CoroutineScope, Closeable {

    override val coroutineContext = Dispatchers.IO + SupervisorJob()
    private val docId = vf.documentId

    private val minimalBufferLength = bufferSizeKB * 1024L
    private var urlContentLengthLong: Long
    private val bufferedRanges: SortedLongRangeList
        get() = cacheInfo.bufferedRanges
    private val cacheInfo: CacheInfo = ObjectBox.getOrCreateFileCacheInfo(vf, bufferFile)

    @Volatile
    var isCompleted = false

    private var currentJob: Job? = null
    private val lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    @Volatile
    private var currentPosition: Long = 0L
        @Synchronized get
        @Synchronized set

    @Volatile
    private var lastLunchPosition = -1L

    init {
        if (cacheInfo.isComplete) {
            logcat(INFO) { "init(${docId.short}): File already downloaded." }
            isCompleted = cacheInfo.isCompleted
            currentPosition = cacheInfo.localLength
            urlContentLengthLong = cacheInfo.localLength
        } else {
            urlContentLengthLong = getURLContentLengthLong()
            if (bufferedRanges.isNotEmpty()) {
                requestData(bufferedRanges.first().first)
            } else {
                requestData(0)
            }
        }
    }

    @Synchronized
    private fun requestData(from: Long) {
        logcat(VERBOSE) { "requestData(${docId.short}): from=$from, previousJob=$currentJob" }
        if (!needRequest(from)) return

        logcat(
            TAG, DEBUG
        ) { "requestData(${docId.short}): lunching new request due to range changed newStart=$from" }
        lastLunchPosition = from
        currentJob?.cancel(cause = CancelCauseNewRangeException())
        currentJob = launch(Dispatchers.IO) {
            run retryLoop@{
                repeat(maxRetry + 1) { attempt ->
                    if (attempt > 0) {
                        logcat(LogPriority.WARN) {
                            "requestData(${docId.short}): Retrying $attempt/$maxRetry"
                        }
                        delay(1000)
                    }
                    try {
                        requestDataSingleThread(from)
                        if (isCompleted) logcat(INFO) { "requestData(${docId.short}): Finished download, length=$urlContentLengthLong" }
                        saveCacheInfo()
                        return@retryLoop
                    } catch (e: CancellationException) {
                        if (e.cause?.instanceOf(CancelCauseCloseException::class) == true) {
                            logcat { "requestData(${docId.short}): Canceled due to closing." }
                            saveCacheInfo()
                        }
                    } catch (e: Exception) {
                        logcat(TAG, ERROR) {
                            "requestData(${docId.short}): Run into error ${e.message}, retrying..."
                        }
                        if (attempt == maxRetry) {
                            logcat(ERROR) {
                                "requestData(${docId.short}): All retry failed cause ${e.stackTraceToString()}"
                            }
                            lock.withLock {
                                condition.signalAll()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun needRequest(from: Long): Boolean {
        if (isCompleted) {
            logcat(TAG, VERBOSE) {
                "needRequest(${docId.short}): noop cause already completed"
            }
            return false
        }

        if (unknownLength && currentJob != null && currentJob!!.isActive) {
            logcat(TAG, VERBOSE) {
                "needRequest(${docId.short}): Current running a unknown length stream, skip any request"
            }
            return false
        }

        if (from in 1..currentPosition || lastLunchPosition >= from) {
            logcat(
                TAG, VERBOSE
            ) { "needRequest(${docId.short}): noop cause range overlap from=$from, current=$currentPosition, lastLaunch=$lastLunchPosition." }
            lastLunchPosition = from
            return false
        }

        if (bufferedRanges.noGapsIn(from..urlContentLengthLong)) {
            logcat(
                TAG,
                VERBOSE
            ) { "needRequest(${docId.short}): noop cause no gap in between [$from..$urlContentLengthLong]." }
            return false
        }
        return true
    }


    private fun saveCacheInfo() {
        ObjectBox.setFileCacheInfo(
            cacheInfo.copy(
                localLength = urlContentLengthLong,
                isCompleted = isCompleted,
                bitrate = bitrate
            )
        )
    }

    /**
     * Download the content start at **from** tail the end
     *
     * TODO: add the ability to skip downloaded areas
     */
    @Throws(Exception::class)
    private fun requestDataSingleThread(from: Long) {
        var totalBytesRead = 0L
        fun updateBufferedRange() {
            if (totalBytesRead != 0L) {
                bufferedRanges.add(from until from + totalBytesRead)
                bufferedRanges.merge()
                lock.withLock {
                    condition.signalAll()
                }
            }
        }
        try {
            logcat(INFO) { "requestDataSingleThread(${docId.short}): start new request from $from, unknownLength=$unknownLength" }
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Range", "bytes=${from}-$urlContentLengthLong")

            connection.inputStream.use { inputStream ->
                RandomAccessFile(bufferFile, "rws").use { outputStream ->

                    var bytesRead: Int
                    val buffer = ByteArray(8 * 1024)
                    var accumulatedRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.seek(from + totalBytesRead)
                        outputStream.write(buffer, 0, bytesRead)

                        accumulatedRead += bytesRead
                        totalBytesRead += bytesRead
                        currentPosition = from + totalBytesRead

                        if (accumulatedRead >= minimalBufferLength) {
                            updateBufferedRange()
                            accumulatedRead = 0
                        }
                    }

                    // bytesRead == -1, EOF of stream
                    urlContentLengthLong = currentPosition + 1
                    isCompleted = true
                }
            }
            updateBufferedRange()
        } catch (e: CancellationException) {
            if (e.cause!!.instanceOf(CancelCauseNewRangeException::class)) {
                logcat { "requestDataSingleThread(${docId.short}): Canceled due to new request range." }
                updateBufferedRange()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun read(offset: Long, size: Int, data: ByteArray): Int = lock.withLock {
        if (!isCompleted) {
            requestData(offset)
            while (!isCompleted) {
                condition.await(500L, TimeUnit.MILLISECONDS)
                /**
                 * Break if we have the required amount of data
                 * or finished download.
                 */
                val dataRequested = bufferedRanges.noGapsIn(offset..offset + size)
                if (isCompleted || dataRequested) {
                    logcat(VERBOSE) {
                        "read(): break wait loop dataRequested=$dataRequested, isCompleted=$isCompleted"
                    }
                    break
                }
            }
        }
        /**
         * We will reach here if we have enough data
         * or the download is finished.
         *
         * We had to ensure the size to seek is not exceed
         * real file size if we have used Long.MAX_VALUE
         *
         * The isCompleted check is redundant I think, but...
         */
        //
        if (isCompleted && offset >= currentPosition) {
            return@withLock 0
        }
        //FIXME: invalid cache when local cache file size is not the same as cacheInfo.localLength
        return RandomAccessFile(bufferFile, "r").use { file ->
            file.seek(offset)
            val readSize = file.read(data, 0, size)
            logcat(VERBOSE) { "read(${docId.short}): read ${readSize.readable}" }
            if (readSize < 0) 0 else readSize
        }
    }


    override val length: Long
        get() {
            if (isCompleted) return cacheInfo.localLength
            return if (unknownLength) {
                if (bitrate != -1) {
                    vf.mediaInfo.target.duration!! / 8000 * bitrate
                } else {
                    Long.MAX_VALUE
                }
            } else {
                urlContentLengthLong
            }
        }


    override fun close() {
        saveCacheInfo()
        currentJob?.cancel(cause = CancelCauseCloseException())
        coroutineContext.cancelChildren()
    }

    @Throws(IOException::class)
    fun getURLContentLengthLong(): Long {
        val connection = url.openConnection()
        return connection.contentLengthLong
    }

    private val unknownLength
        get() = urlContentLengthLong == -1L

    inner class CancelCauseNewRangeException : CancellationException()
    inner class CancelCauseCloseException : CancellationException()
}
