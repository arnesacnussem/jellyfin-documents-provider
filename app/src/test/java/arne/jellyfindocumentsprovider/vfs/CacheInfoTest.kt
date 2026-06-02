package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheInfoTest {

    @Rule @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun cacheFile_lazyInit() {
        val file = tempFolder.newFile("cache.dat")
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = file.absolutePath,
            chunks = CacheChunks()
        )
        val cf = cacheInfo.cacheFile
        assertNotNull(cf)
        assertSame(cf, cacheInfo.cacheFile) // lazy, returns same instance
    }

    @Test
    fun cacheFile_writeAndRead_roundtrip() {
        val file = tempFolder.newFile("cache.dat")
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = file.absolutePath,
            chunks = CacheChunks()
        )
        val cf = cacheInfo.cacheFile

        val data = byteArrayOf(1, 2, 3, 4, 5)
        cf.write(0, data)

        val buffer = ByteArray(5)
        val read = cf.read(0, 5, buffer)
        assertEquals(5, read)
        assertArrayEquals(data, buffer)
    }

    @Test
    fun cacheFile_writeAndRead_partialRead() {
        val file = tempFolder.newFile("cache.dat")
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = file.absolutePath,
            chunks = CacheChunks()
        )
        val cf = cacheInfo.cacheFile

        cf.write(10, byteArrayOf(10, 20, 30, 40))

        val buffer = ByteArray(2)
        val read = cf.read(12, 2, buffer)
        assertEquals(2, read)
        assertArrayEquals(byteArrayOf(30, 40), buffer)
    }

    @Test
    fun cacheFile_writeMergesAdjacentChunks() {
        val file = tempFolder.newFile("cache.dat")
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = file.absolutePath,
            chunks = CacheChunks()
        )
        val cf = cacheInfo.cacheFile

        cf.write(0, byteArrayOf(1, 2, 3))
        cf.write(3, byteArrayOf(4, 5, 6))

        assertEquals(1, cf.size)
    }

    @Test
    fun cacheFile_readBeyondWrittenData_returnsShorterLength() {
        val file = tempFolder.newFile("cache.dat")
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = file.absolutePath,
            chunks = CacheChunks()
        )
        val cf = cacheInfo.cacheFile

        cf.write(0, byteArrayOf(1, 2, 3))

        val buffer = ByteArray(10)
        val read = cf.read(0, 10, buffer)
        // Only 3 bytes in file, RandomAccessFile.read returns actual bytes read
        assertEquals(3, read)
        assertEquals(1.toByte(), buffer[0])
        assertEquals(2.toByte(), buffer[1])
        assertEquals(3.toByte(), buffer[2])
    }

    @Test
    fun close_invokesPersistCallbackWithCopy() {
        val file = tempFolder.newFile("cache.dat")
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = file.absolutePath,
            chunks = CacheChunks()
        )
        var captured: CacheInfo? = null
        cacheInfo.persistCallback = { captured = it }

        cacheInfo.close()

        assertNotNull("persistCallback should be called on close", captured)
        assertNotNull(captured!!.chunks)
    }

    @Test
    fun close_withoutCacheFileInitDoesNotThrow() {
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = "/tmp/nonexistent",
            chunks = CacheChunks()
        )
        cacheInfo.persistCallback = {} // prevent ObjectBox fallback
        cacheInfo.close()
    }

    @Test
    fun cacheFile_closeClosesUnderlyingFile() {
        val file = tempFolder.newFile("cache.dat")
        val cacheInfo = CacheInfo(
            vfDocId = "doc-1", localPath = file.absolutePath,
            chunks = CacheChunks()
        )
        val cf = cacheInfo.cacheFile
        cf.write(0, byteArrayOf(1, 2, 3))
        cf.close()

        // After close, double-close should not throw
        cf.close()
    }
}
