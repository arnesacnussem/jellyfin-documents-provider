package arne.jellyfindocumentsprovider.provider

import arne.jellyfindocumentsprovider.common.InMemoryLogBuffer
import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.hacks.readable
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

    fun proxy(fsf: FileStreamFactory, vf: VirtualFile, bitrate: Int, traceId: String, onReleased: () -> Unit = {}) =
        URLProxyFileDescriptorCallback(getRA(fsf, vf, bitrate, traceId)) {
            try {
                releaseRA(vf.documentId)
            } finally {
                onReleased()
            }
        }

    private fun getRA(fsf: FileStreamFactory, vf: VirtualFile, bitrate: Int, traceId: String): RandomAccess {
        val key = vf.documentId
        synchronized(this) {
            val ra = mapper[key]
            if (ra != null) {
                refCnt[key] = refCnt.getOrDefault(key, 0) + 1
                logcat(LogPriority.DEBUG) { "get(${key.short}): refCnt = ${refCnt[key]}" }
                return ra
            }
        }

        val newRA = newBufferedRA(fsf, vf, bitrate, traceId)
        synchronized(this) {
            val existing = mapper[key]
            if (existing != null) {
                newRA.close()
                refCnt[key] = refCnt.getOrDefault(key, 0) + 1
                logcat(LogPriority.DEBUG) { "get(${key.short}): refCnt = ${refCnt[key]}" }
                return existing
            }
            mapper[key] = newRA
            refCnt[key] = 1
            StatusEventManager.startNetwork(key, "Streaming ${vf.name}")
            InMemoryLogBuffer.log(LogPriority.INFO, "Network", "Start streaming ${vf.name} (${vf.size.readable})")
            logcat(LogPriority.DEBUG) { "get(${key.short}): created [$traceId]" }
            return newRA
        }
    }

    private fun releaseRA(key: String) {
        synchronized(this) {
            val current = refCnt.getOrDefault(key, 0)
            val after = current - 1
            logcat(LogPriority.INFO) { "release(${key.short}): refCnt $current → $after" }
            if (after <= 0) {
                refCnt.remove(key)
                val remove = mapper.remove(key)
                remove?.close()
                StatusEventManager.finishNetwork(key)
                InMemoryLogBuffer.log(LogPriority.INFO, "Network", "Stop streaming $key")
            } else {
                refCnt[key] = after
            }
        }
    }

    private fun newBufferedRA(
        fsf: FileStreamFactory,
        vf: VirtualFile,
        bitrate: Int,
        traceId: String,
    ) =
        FileByteReadChannelRandomAccess(
            virtualFile = vf,
            fileStreamFactory = fsf,
            file = tempFileRoot.resolve(vf.documentId).toFile().apply {
                createNewFile()
            },
            traceId = traceId,
        )
}
