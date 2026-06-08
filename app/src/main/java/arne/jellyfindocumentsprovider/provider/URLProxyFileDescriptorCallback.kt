package arne.jellyfindocumentsprovider.provider

import android.os.ProxyFileDescriptorCallback
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
        val read = ra.read(offset, size, data)
        val length = ra.length
        when {
            read > size -> logcat(LogPriority.WARN) {
                "onRead: read > size ($read > $size) [offset=$offset, total=$length]"
            }
            read == 0 && size > 0 && offset < length -> logcat(LogPriority.WARN) {
                "onRead: zero bytes before EOF [offset=$offset, size=$size, total=$length]"
            }
        }
        return if (read <= 0) 0
        else read

    }

    override fun onRelease() {
        release()
    }
}
