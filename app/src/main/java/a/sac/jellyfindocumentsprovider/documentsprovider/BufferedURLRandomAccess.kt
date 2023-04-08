package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.SortedLongRangeList
import a.sac.jellyfindocumentsprovider.TAG
import a.sac.jellyfindocumentsprovider.database.ObjectBox
import a.sac.jellyfindocumentsprovider.database.entities.CacheInfo
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.logcat
import a.sac.jellyfindocumentsprovider.readable
import a.sac.jellyfindocumentsprovider.short
import io.ktor.util.reflect.instanceOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.*
import logcat.LogPriority
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * TODO: add the ability to limit buffer file size
 */
class BufferedURLRandomAccess(
    vf: VirtualFile,
    private val url: URL,
    bufferSizeKB: Int = 0,
    private val bufferFile: File,
    private val maxRetry: Int = 3
) : URLRandomAccess(url), CoroutineScope, Closeable {

    override val coroutineContext = Dispatchers.IO + SupervisorJob()
    private val bufferSize = bufferSizeKB * 1024
    private val docId = vf.documentId

    private val contentLengthLong = getContentLengthLong()
    private val bufferedRanges: SortedLongRangeList
    private val cacheInfo: CacheInfo = ObjectBox.getOrCreateFileCacheInfo(vf, bufferFile)


    private var currentJob: Job? = null
    private val lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    @Volatile
    private var currentPosition: Long = -1L
        @Synchronized get
        @Synchronized set

    @Volatile
    private var lastLunchPosition = -1L

    init {
        bufferedRanges = cacheInfo.bufferedRanges
        if (cacheInfo.isCompleted) {
            logcat(INFO) { "init(${docId.short}): File already downloaded." }
            currentPosition = length
        } else {
            requestData(0)
        }
    }

    @Synchronized
    private fun requestData(from: Long) {
        logcat(VERBOSE) { "requestData(): from=$from, previousJob=$currentJob" }
        if (currentPosition == length) {
            logcat(TAG, VERBOSE) {
                "requestData(): noop cause already downloaded"
            }
            return
        }
        if (from <= currentPosition || lastLunchPosition >= from) {
            logcat(
                TAG, VERBOSE
            ) { "requestData(): noop cause range overlap from=$from, current=$currentPosition, lastLaunch=$lastLunchPosition." }
            lastLunchPosition = from
            return
        }

        if (bufferedRanges.noGapsIn(from..length)) {
            logcat(TAG, VERBOSE) { "requestData(): noop cause no gap in between [$from..$length]." }
            return
        }

        logcat(
            TAG, VERBOSE
        ) { "requestData(): lunching new request due to range changed newStart=$from" }
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


    private fun saveCacheInfo() {
        val copy = cacheInfo.copy(
            isCompleted = currentPosition == length, bufferedRanges = bufferedRanges
        )
        ObjectBox.setFileCacheInfo(copy)
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
            logcat(INFO) { "requestDataSingleThread(${docId.short}): start new request from $from" }
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Range", "bytes=${from}-$contentLengthLong")

            connection.inputStream.use { inputStream ->
                RandomAccessFile(bufferFile, "rws").use { outputStream ->

                    var bytesRead: Int
                    val buffer = ByteArray(bufferSize) //128k

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.seek(from + totalBytesRead)
                        outputStream.write(buffer, 0, bytesRead)

                        currentPosition += bytesRead
                        totalBytesRead += bytesRead

                        updateBufferedRange()
                    }
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
        if (currentPosition != length) {
            requestData(offset)
            while (currentPosition < length) {
                condition.await()

                if (bufferedRanges.noGapsIn(offset..size)) {
                    break
                }
            }
        }

        return RandomAccessFile(bufferFile, "r").use { file ->
            file.seek(offset)
            val readSize = file.read(data, 0, size)
            logcat(VERBOSE) { "read(${docId.short}): read ${readSize.readable}" }
            if (readSize < 0) 0 else readSize
        }
    }


    override val length: Long
        get() = contentLengthLong


    override fun close() {
        saveCacheInfo()
        currentJob?.cancel(cause = CancelCauseCloseException())
        coroutineContext.cancelChildren()
    }

    @Throws(IOException::class)
    fun getContentLengthLong(): Long {
        val connection = url.openConnection()
        return connection.contentLengthLong
    }

    inner class CancelCauseNewRangeException : CancellationException()
    inner class CancelCauseCloseException : CancellationException()
}
