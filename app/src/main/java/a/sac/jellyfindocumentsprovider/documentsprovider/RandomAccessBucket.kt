package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.TAG
import android.util.Log
import java.net.URL
import java.nio.file.Path

object RandomAccessBucket {
    fun init(tempPath: Path) {
        tempFileRoot = tempPath
    }

    private lateinit var tempFileRoot: Path
    private val mapper = HashMap<String, ChunkedURLRandomAccess>()
    private val refCnt = HashMap<String, Int>()


    fun get(url: String, key: String): ChunkedURLRandomAccess {
        synchronized(this) {
            refCnt[key] = refCnt.getOrDefault(key, 0) + 1
            if (mapper.containsKey(key))
                return mapper[key]!!
            else mapper[key] = getNewBlockDownloader(url, key)

            Log.d(TAG, "get() called with: key = $key, refCnt = ${refCnt[key]}")
            return mapper[key]!!
        }
    }

    fun release(key: String) {
        synchronized(this) {
            val orDefault = refCnt.getOrDefault(key, 0)
            Log.d(TAG, "release() called with: id = $key, refCnt = $orDefault")
            if (orDefault == 0) {
                val remove = mapper.remove(key)
                remove?.release()
            } else {
                refCnt[key] = refCnt[key]!! - 1
            }
        }
    }

    private fun newTempFile(id: String) = tempFileRoot.resolve(id).toFile().let {
        it.createNewFile()
        it
    }

    private fun getNewBlockDownloader(url: String, key: String) =
        ChunkedURLRandomAccess(
            id = key,
            url = URL(url),
            chunkSizeMB = 1,
            outputFile = newTempFile(key)
        )
}