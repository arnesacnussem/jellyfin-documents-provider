package a.sac.jellyfindocumentsprovider.documentsprovider

import android.os.ProxyFileDescriptorCallback
import kotlinx.coroutines.runBlocking
import java.net.URL

class URLProxyFileDescriptorCallback(
    url: URL
) : ProxyFileDescriptorCallback() {
    private val blockDownloader = BlockDownloader(url, 1, 4)
    override fun onGetSize(): Long {
        return blockDownloader.contentLength
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray?): Int {
        return runBlocking {
            val read = blockDownloader.read(offset, size)
            val s = read.size
            System.arraycopy(read, 0, data!!, 0, s)
            s
        }
    }


    override fun onRelease() {
    }
}