package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CacheFileTest {

    @Test
    fun writeAndReadSingleChunk() {
        val tempFile = File.createTempFile("cachefile-test", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        val data = byteArrayOf(1, 2, 3, 4, 5)
        cache.write(0, data)

        val readData = ByteArray(5)
        val bytesRead = cache.read(0, 5, readData)
        assertEquals(5, bytesRead)
        assertArrayEquals(data, readData)

        assertEquals(1, cache.size)
        assertEquals(0L..4L, cache[0])

        cache.close()
    }

    @Test
    fun writeMultipleChunksAndRead() {
        val tempFile = File.createTempFile("cachefile-multi", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        cache.write(0, byteArrayOf(1, 2, 3))
        cache.write(10, byteArrayOf(4, 5, 6))

        val readData = ByteArray(3)
        cache.read(0, 3, readData)
        assertArrayEquals(byteArrayOf(1, 2, 3), readData)

        cache.read(10, 3, readData)
        assertArrayEquals(byteArrayOf(4, 5, 6), readData)

        // Two separate, non-adjacent chunks
        assertEquals(2, cache.size)

        cache.close()
    }

    @Test
    fun writeAdjacentChunksMerge() {
        val tempFile = File.createTempFile("cachefile-merge", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        cache.write(0, byteArrayOf(1, 2, 3))
        cache.write(3, byteArrayOf(4, 5, 6))

        assertEquals(1, cache.size)
        assertEquals(0L..5L, cache[0])

        // Verify data integrity after merge
        val readData = ByteArray(6)
        val bytesRead = cache.read(0, 6, readData)
        assertEquals(6, bytesRead)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), readData)

        cache.close()
    }

    @Test
    fun writeOverlappingChunksMerge() {
        val tempFile = File.createTempFile("cachefile-overlap", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        cache.write(0, byteArrayOf(1, 2, 3, 4, 5))
        cache.write(2, byteArrayOf(6, 7, 8))

        // Chunks 0..4 and 2..4 should merge into 0..4
        assertEquals(1, cache.size)
        assertEquals(0L..4L, cache[0])

        // Read back: second write overwrites at offset 2 with 6,7,8
        // So file has: 1, 2, 6, 7, 8
        val readData = ByteArray(5)
        val bytes = cache.read(0, 5, readData)
        assertEquals(5, bytes)
        assertArrayEquals(byteArrayOf(1, 2, 6, 7, 8), readData)

        cache.close()
    }

    @Test
    fun readBeyondWrittenReturnsAvailableData() {
        val tempFile = File.createTempFile("cachefile-beyond", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        cache.write(0, byteArrayOf(1, 2, 3))

        // Read more than written — should only get what's available
        val readData = ByteArray(100)
        val bytesRead = cache.read(0, 100, readData)
        assertTrue("should read exactly the 3 available bytes", bytesRead == 3)

        cache.close()
    }

    @Test
    fun writeAtNonZeroOffsetAndRead() {
        val tempFile = File.createTempFile("cachefile-offset", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        cache.write(10, byteArrayOf(1, 2, 3, 4, 5))

        val chunk = cache.offsetInChunks(12)
        assertNotNull("offset 12 should be within chunk 10..14", chunk)
        assertEquals(10L..14L, chunk)

        assertNull("offset 5 should not be in any chunk", cache.offsetInChunks(5))
        assertNull("offset 15 is past the end of 10..14", cache.offsetInChunks(15))

        cache.close()
    }

    @Test
    fun closeReleasesFileHandle() {
        val tempFile = File.createTempFile("cachefile-close", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())
        cache.write(0, byteArrayOf(1, 2, 3))
        cache.close()
        // After close, the RandomAccessFile handle is released and file can be deleted
        assertTrue("file should be deletable after cache close", tempFile.delete())
    }

    @Test
    fun largeWriteAndRead() {
        val tempFile = File.createTempFile("cachefile-large", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        val data = ByteArray(1024) { (it % 256).toByte() }
        cache.write(0, data)

        val readData = ByteArray(1024)
        val bytesRead = cache.read(0, 1024, readData)
        assertEquals("should read all 1024 bytes", 1024, bytesRead)
        assertArrayEquals("data should match original", data, readData)

        cache.close()
    }

    @Test
    fun writeToExistingFileAppendsToFilePosition() {
        val tempFile = File.createTempFile("cachefile-append", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        cache.write(0, byteArrayOf(1, 2, 3))
        cache.write(3, byteArrayOf(4, 5, 6))

        // Read the full merged range
        val readData = ByteArray(6)
        val bytesRead = cache.read(0, 6, readData)
        assertEquals(6, bytesRead)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), readData)

        cache.close()
    }

    @Test
    fun readAfterCloseReturnsMinusOne() {
        val tempFile = File.createTempFile("cachefile-afterclose", ".tmp")
        tempFile.deleteOnExit()
        val cache = CacheInfo.CacheFile(tempFile, CacheChunks())

        cache.write(0, byteArrayOf(1, 2, 3))
        cache.close()

        // Reading from a closed RandomAccessFile should throw or return -1
        val readData = ByteArray(3)
        try {
            val result = cache.read(0, 3, readData)
            assertTrue("read after close should be -1 or throw", result == -1 || result < 0)
        } catch (e: Exception) {
            // Exception is also acceptable for closed file reads
            assertTrue(e is java.io.IOException)
        }
    }
}
