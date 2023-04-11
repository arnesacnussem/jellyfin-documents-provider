package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.utils.short
import logcat.LogPriority
import logcat.logcat
import java.net.URL
import java.nio.file.Path

object RandomAccessBucket {
    fun init(tempPath: Path) {
        tempFileRoot = tempPath
    }

    private lateinit var tempFileRoot: Path
    private val mapper = HashMap<String, URLRandomAccess>()
    private val refCnt = HashMap<String, Int>()

    fun getProxy(url: String, vf: VirtualFile) =
        URLProxyFileDescriptorCallback(getRA(url, vf)) {
            releaseRA(vf.documentId)
        }

    private fun getRA(url: String, vf: VirtualFile): URLRandomAccess {
        val key = vf.documentId
        synchronized(this) {
            refCnt[key] = refCnt.getOrDefault(key, 0) + 1
            if (mapper.containsKey(key))
                return mapper[key]!!
            else mapper[key] = newBufferedRA(url, vf)

            logcat(LogPriority.DEBUG) { "get(${key.short}): refCnt = ${refCnt[key]}" }
            return mapper[key]!!
        }
    }

    private fun releaseRA(key: String) {
        synchronized(this) {
            val after = refCnt.getOrDefault(key, 0) - 1
            if (after <= 0) {
                refCnt.remove(key)
                val remove = mapper.remove(key)
                remove?.close()
            } else {
                refCnt[key] = after
            }

            logcat(LogPriority.DEBUG) { "release(${key.short}): refCnt = $after" }
        }
    }

    private fun newTempFile(id: String) = tempFileRoot.resolve(id).toFile().let {
        it.createNewFile()
        it
    }

    private fun newBufferedRA(url: String, vf: VirtualFile) =
        BufferedURLRandomAccess(
            vf = vf,
            url = URL(url),
            bufferSizeKB = 8,
            bufferFile = newTempFile(vf.documentId)
        )
}