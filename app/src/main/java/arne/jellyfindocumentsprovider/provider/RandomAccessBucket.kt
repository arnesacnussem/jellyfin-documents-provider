package arne.jellyfindocumentsprovider.provider

import arne.jellyfindocumentsprovider.hacks.short
import arne.jellyfindocumentsprovider.vfs.FileStreamFactory
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import logcat.LogPriority
import logcat.logcat
import java.nio.file.Path

object RandomAccessBucket {
    fun init(tempFileRoot: Path) {
        this.tempFileRoot = tempFileRoot
    }

    private lateinit var tempFileRoot: Path
    private val mapper = HashMap<String, RandomAccess>()
    private val refCnt = HashMap<String, Int>()

    fun proxy(fsf: FileStreamFactory, vf: VirtualFile, bitrate: Int) =
        URLProxyFileDescriptorCallback(getRA(fsf, vf, bitrate)) {
            releaseRA(vf.documentId)
        }

    private fun getRA(fsf: FileStreamFactory, vf: VirtualFile, bitrate: Int): RandomAccess {
        val key = vf.documentId
        synchronized(this) {
            refCnt[key] = refCnt.getOrDefault(key, 0) + 1
            if (!mapper.containsKey(key))
                mapper[key] = newBufferedRA(fsf, vf, bitrate)

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

    private fun newBufferedRA(
        fsf: FileStreamFactory,
        vf: VirtualFile,
        bitrate: Int
    ) =
        FileByteReadChannelRandomAccess(
            virtualFile = vf,
            fileStreamFactory = fsf,
            file = tempFileRoot.resolve(vf.documentId).toFile().apply {
                createNewFile()
            },
        )
}

