package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.HumanReadable
import a.sac.jellyfindocumentsprovider.TAG
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.ceil
import kotlin.math.min

/**
 *
 * This class download file from URL by X-MB chunks
 * 1. add all chunks into a queue (optional, controlled by lazy = true)
 * 2. allow retrieve a certain set of chunks immediately by their id
 * 3. downloaded chunks stored in a file (passed from constructor)
 * 4. file can be accessed while writing, read function signature: read(offset:Long, length:Int, data:ByteArray):Int
 */
class ChunkedURLRandomAccess(
    val id: String,
    private val url: URL,
    chunkSizeMB: Int = 1, // X-MB chunks
    private val outputFile: File,
    private val threadCnt: Int = 4,
    private val preFetchChunk: Int = 0,
) : URLRandomAccess(url) {
    private val executor = Executors.newFixedThreadPool(threadCnt) { Thread(it, TAG) }
    private val downloading = HashMap<Int, Future<Int>>()
    private val completed = HashSet<Int>()
    private val chunkSize = chunkSizeMB * 1024 * 1024
    private val totalChunks: Int
    private val contentLength: Long

    init {
        outputFile.createNewFile()
        val connection = createConnection()
        connection.connect()
        contentLength = connection.contentLengthLong
        connection.disconnect()
        totalChunks = ceil(contentLength.toDouble() / chunkSize).toInt() - 1
    }

    private fun enqueue(chunkId: Int): Future<Int>? {
        if (completed.contains(chunkId)) return null

        if (!downloading.containsKey(chunkId)) {
            val submit = executor.submit(Callable {
                downloadChunk((chunkId))
                downloading.remove(chunkId)
                completed.add(chunkId)

                chunkId
            })
            downloading[chunkId] = submit
        }
        return downloading[chunkId]
    }


    @Synchronized
    private fun downloadChunk(chunkId: Int) {
        val startByte = chunkId * chunkSize
        val endByte = startByte + chunkSize - 1
        val connection = createConnection()
        connection.setRequestProperty("Range", "bytes=$startByte-$endByte")
        connection.connect()

        val inputStream = connection.inputStream
//        val buffer = ByteArray(chunkSize)
        val outputStream = ByteArrayOutputStream(chunkSize)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        val buffer = outputStream.toByteArray()
        val bytesRead = buffer.size
        val data = if (bytesRead < chunkSize) buffer.copyOf(bytesRead) else buffer

        writeSync(
            (chunkId * chunkSize).toLong(), data
        )
        Log.d(
            TAG, "downloadChunk: id=${
                id.subSequence(
                    0, 8
                )
            }, chunk=$chunkId/$totalChunks size=${bytesRead.HumanReadable} status=${connection.responseCode}."
        )
    }

    @Synchronized
    private fun writeSync(offset: Long, data: ByteArray) {
        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data)
        }
    }

    override val length: Long
        get() = contentLength

    override fun read(offset: Long, length: Int, data: ByteArray): Int {
        val startChunkId = (offset / chunkSize).toInt()
        val endChunkId = min(totalChunks, ((offset + length - 1) / chunkSize).toInt())


        (startChunkId..endChunkId).mapNotNull { chunkId ->
            enqueue(chunkId)
        }.forEach {
            it.get()
        }

        val lastChunkFetched = completed.max()
        (lastChunkFetched until lastChunkFetched + preFetchChunk).forEach { chunkId ->
            enqueue(chunkId)
        }
        val result = RandomAccessFile(outputFile, "r").use { raf ->
            raf.seek(offset)
            raf.read(data, 0, length)
        }
        return result
    }

    override fun release() {
        executor.shutdown()
        RandomAccessBucket.release(id)
    }

    private fun createConnection(): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 10000
            requestMethod = "GET"
        }
}