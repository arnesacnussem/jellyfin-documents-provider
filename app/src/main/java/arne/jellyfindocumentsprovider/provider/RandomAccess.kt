package arne.jellyfindocumentsprovider.provider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.Closeable

abstract class RandomAccess : Closeable, CoroutineScope {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
    abstract val length: Long
    abstract fun read(offset: Long, size: Int, data: ByteArray): Int
    abstract override fun close()
}