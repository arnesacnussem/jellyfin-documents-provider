package arne.jellyfindocumentsprovider.provider

import android.os.ProxyFileDescriptorCallback
import arne.jellyfindocumentsprovider.hacks.readable
import logcat.LogPriority
import logcat.logcat

class URLProxyFileDescriptorCallback(
    private val ra: RandomAccess,
    private val release: () -> Unit
) : ProxyFileDescriptorCallback() {
    override fun onGetSize(): Long {
        return ra.length
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        logcat(LogPriority.VERBOSE) { "onRead() called with: offset = ${offset.readable}, size = ${size.readable}" }
        val read = ra.read(offset, size, data)
        if (read != size)
            logcat(LogPriority.WARN) { "onRead: read!=size ($read!=$size) [EOF=${offset + size >= ra.length}, offset = ${offset}, size = ${size}, total = ${ra.length}]" }
        return if (read <= 0) 0
        else read

    }

    override fun onRelease() {
        release()
    }
}