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
        val randId = (Math.random() * 10000).toInt() // 2char
        logcat(LogPriority.VERBOSE) { "[$randId] onRead() called with: offset = ${offset.readable}, size = ${size.readable}" }
        val start = System.currentTimeMillis()
        val read = ra.read(offset, size, data)
        val length = ra.length
        when {
            read > size -> logcat(LogPriority.WARN) {
                "onRead: read > size ($read > $size) [offset=$offset, total=$length]"
            }
            read == 0 && size > 0 && offset < length -> logcat(LogPriority.WARN) {
                "onRead: zero bytes before EOF [offset=$offset, size=$size, total=$length]"
            }
            read in 1 until size -> logcat(LogPriority.VERBOSE) {
                "onRead: short read ($read < $size) [requestExtendsEOF=${offset + size >= length}, offset=$offset, total=$length]"
            }
        }

        logcat(LogPriority.VERBOSE) {
            "[$randId] onRead: took ${System.currentTimeMillis() - start}ms [offset=$offset, size=$size, total=$length]"
        }
        return if (read <= 0) 0
        else read

    }

    override fun onRelease() {
        release()
    }
}
