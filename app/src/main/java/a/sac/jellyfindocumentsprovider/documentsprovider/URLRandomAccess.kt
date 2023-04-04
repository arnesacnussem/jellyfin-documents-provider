package a.sac.jellyfindocumentsprovider.documentsprovider

import java.io.Closeable
import java.net.URL

abstract class URLRandomAccess(url: URL) : Closeable {
    abstract val length: Long
    abstract fun read(offset: Long, length: Int, data: ByteArray): Int
    abstract override fun close()
}