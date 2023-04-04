package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.HumanReadable
import a.sac.jellyfindocumentsprovider.TAG
import android.os.ProxyFileDescriptorCallback
import android.util.Log

class URLProxyFileDescriptorCallback(
    private val ra: URLRandomAccess
) : ProxyFileDescriptorCallback() {
    override fun onGetSize(): Long {
        return ra.length
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        val read = ra.read(offset, size, data)
        if (read != size)
            Log.e(
                TAG,
                "onRead: read!=size [offset = ${offset.HumanReadable}, size = ${size.HumanReadable}, total = ${ra.length.HumanReadable}]"
            )
        return if (read <= 0) 0
        else read

    }

    override fun onRelease() {
    }
}