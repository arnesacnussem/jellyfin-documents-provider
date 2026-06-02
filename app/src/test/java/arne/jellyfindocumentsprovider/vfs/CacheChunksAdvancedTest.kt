package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Test

class CacheChunksAdvancedTest {

    @Test
    fun addSingleRangeAddsOne() {
        val chunks = CacheChunks()
        chunks.add(0L..100L)
        assertEquals(1, chunks.size)
        assertEquals(0L..100L, chunks[0])
    }

    @Test
    fun addExactOverlapMergesToSame() {
        val chunks = CacheChunks()
        chunks.add(10L..20L)
        chunks.add(10L..20L)
        assertEquals(1, chunks.size)
        assertEquals(10L..20L, chunks[0])
    }

    @Test
    fun addInsideExistingDoesNotCreateNew() {
        val chunks = CacheChunks()
        chunks.add(0L..100L)
        chunks.add(20L..50L)
        assertEquals(1, chunks.size)
        assertEquals(0L..100L, chunks[0])
    }

    @Test
    fun addIntoGapSplitsCorrectly() {
        val chunks = CacheChunks()
        chunks.add(0L..10L)
        chunks.add(30L..40L)
        chunks.add(15L..25L)
        // Should merge: 0..10 + 15..25 + 30..40 (note: 10+1=11 ≠ 15, so no merge)
        assertEquals(3, chunks.size)
        assertEquals(0L..10L, chunks[0])
        assertEquals(15L..25L, chunks[1])
        assertEquals(30L..40L, chunks[2])
    }

    @Test
    fun offsetInChunksExactStart() {
        val chunks = CacheChunks()
        chunks.add(10L..20L)
        val result = chunks.offsetInChunks(10L)
        assertNotNull(result)
        assertEquals(10L..20L, result)
    }

    @Test
    fun offsetInChunksExactEndMinusOne() {
        val chunks = CacheChunks()
        chunks.add(10L..20L)
        val result = chunks.offsetInChunks(19L)
        assertNotNull(result)
        assertEquals(10L..20L, result)
    }

    @Test
    fun offsetInChunksExactEndReturnsRange() {
        val chunks = CacheChunks()
        chunks.add(10L..20L)
        assertEquals(10L..20L, chunks.offsetInChunks(20L))
    }

    @Test
    fun noGapsInExactMatch() {
        val chunks = CacheChunks()
        chunks.add(0L..100L)
        assertTrue(chunks.noGapsIn(0L..100L))
    }

    @Test
    fun noGapsInWithinRange() {
        val chunks = CacheChunks()
        chunks.add(0L..100L)
        assertTrue(chunks.noGapsIn(20L..50L))
    }

    @Test
    fun isEmptyInitially() {
        val chunks = CacheChunks()
        assertTrue(chunks.isEmpty())
    }
}
