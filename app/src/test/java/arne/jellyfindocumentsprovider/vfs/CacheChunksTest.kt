package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Test

class CacheChunksTest {

    @Test
    fun cacheChunksAdd() {
        val chunks = CacheChunks()
        chunks.add(5L..10L)
        chunks.add(1L..3L)
        assertEquals(2, chunks.size)
        assertEquals(1L..3L, chunks[0])
        assertEquals(5L..10L, chunks[1])
    }

    @Test
    fun cacheChunksMerge() {
        val chunks = CacheChunks()
        chunks.add(5L..10L)
        chunks.add(8L..15L)
        assertEquals(1, chunks.size)
        assertEquals(5L..15L, chunks[0])
    }

    @Test
    fun cacheChunksNoGapsIn() {
        val chunks = CacheChunks()
        chunks.add(1L..10L)
        assertTrue(chunks.noGapsIn(2L..5L))
        assertFalse(chunks.noGapsIn(5L..15L))
    }

    @Test
    fun cacheChunksOffsetInChunks() {
        val chunks = CacheChunks()
        chunks.add(10L..20L)
        assertEquals(10L..20L, chunks.offsetInChunks(15L))
        assertNull(chunks.offsetInChunks(5L))
        assertNull(chunks.offsetInChunks(25L))
    }

    @Test
    fun cacheChunksMergeAdjacent() {
        val chunks = CacheChunks()
        chunks.add(1L..5L)
        chunks.add(6L..10L)
        assertEquals(1, chunks.size)
        assertEquals(1L..10L, chunks[0])
    }
}
