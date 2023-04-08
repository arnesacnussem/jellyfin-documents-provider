package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.readable
import android.os.ProxyFileDescriptorCallback
import logcat.LogPriority
import logcat.logcat

class URLProxyFileDescriptorCallback(
    private val ra: URLRandomAccess,
    private val release: () -> Unit
) : ProxyFileDescriptorCallback() {
    override fun onGetSize(): Long {
        return ra.length
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        logcat(LogPriority.VERBOSE) { "onRead() called with: offset = $offset, size = $size" }
        val read = ra.read(offset, size, data)
        if (read != size)
            logcat(LogPriority.WARN) { "onRead: read!=size ($read!=$size) [EOF=${offset + size == onGetSize()}, offset = ${offset.readable}, size = ${size.readable}, total = ${ra.length.readable}]" }
        return if (read <= 0) 0
        else read

    }

    override fun onRelease() {
        release()
    }
}