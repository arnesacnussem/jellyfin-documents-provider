package a.sac.jellyfindocumentsprovider.documentsprovider

import java.net.URL

abstract class URLRandomAccess(url: URL) {
    abstract val length: Long
    abstract fun read(offset: Long, length: Int, data: ByteArray): Int
    abstract fun release()
}