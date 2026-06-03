package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Test

class CacheChunksConverterTest {
    private val converter = CacheChunksConverter()

    @Test
    fun convertSingleChunk() {
        val original = CacheChunks().apply { add(1L..10L) }
        val serialized = converter.convertToDatabaseValue(original)
        val deserialized = converter.convertToEntityProperty(serialized)

        assertEquals(original.size, deserialized.size)
        assertEquals(original[0], deserialized[0])
    }

    @Test
    fun convertMultipleChunks() {
        val original = CacheChunks().apply {
            add(1L..5L)
            add(10L..20L)
            add(30L..40L)
        }
        val serialized = converter.convertToDatabaseValue(original)
        val deserialized = converter.convertToEntityProperty(serialized)

        assertEquals(3, deserialized.size)
        assertEquals(1L..5L, deserialized[0])
        assertEquals(10L..20L, deserialized[1])
        assertEquals(30L..40L, deserialized[2])
    }

    @Test
    fun convertEmpty() {
        val original = CacheChunks()
        val serialized = converter.convertToDatabaseValue(original)
        val deserialized = converter.convertToEntityProperty(serialized)

        assertEquals(0, deserialized.size)
    }

    @Test
    fun convertNull() {
        val deserialized = converter.convertToEntityProperty(null)
        assertTrue(deserialized.isEmpty())
    }

    @Test
    fun convertBlankString() {
        val deserialized = converter.convertToEntityProperty("")
        assertTrue(deserialized.isEmpty())
    }
}
