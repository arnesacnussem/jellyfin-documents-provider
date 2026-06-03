package arne.jellyfindocumentsprovider.hacks

import org.junit.Assert.*
import org.junit.Test

class ObjectExtensionsTest {

    data class TestData(val name: String, val age: Int, val active: Boolean)

    data class DefaultedData(val value: String = "default", val count: Int = 0)

    // ─── toPropertyMap ─────────────────────────────────────────

    @Test
    fun toPropertyMap_basicDataClass() {
        val obj = TestData(name = "Alice", age = 30, active = true)
        val map = obj.toPropertyMap()

        assertEquals("Alice", map["name"])
        assertEquals(30, map["age"])
        assertEquals(true, map["active"])
    }

    @Test
    fun toPropertyMap_withDefaults() {
        val obj = DefaultedData()
        val map = obj.toPropertyMap()

        assertEquals("default", map["value"])
        assertEquals(0, map["count"])
    }

    // ─── fromMap ───────────────────────────────────────────────

    @Test
    fun fromMap_reconstructsDataClass() {
        val map = mapOf("name" to "Bob", "age" to 25, "active" to false)
        val obj = fromMap<TestData>(map)

        assertEquals("Bob", obj.name)
        assertEquals(25, obj.age)
        assertFalse(obj.active)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromMap_partialMap_missingRequiredParamThrows() {
        val map = mapOf("name" to "Charlie")
        fromMap<TestData>(map) // missing age and active
    }

    @Test
    fun fromMap_defaultedDataClass_allParams() {
        val map = mapOf("value" to "custom", "count" to 42)
        val obj = fromMap<DefaultedData>(map)

        assertEquals("custom", obj.value)
        assertEquals(42, obj.count)
    }

    @Test
    fun roundTrip_toPropertyMapThenFromMap() {
        val original = TestData(name = "Diana", age = 42, active = true)
        val map = original.toPropertyMap()
        @Suppress("UNCHECKED_CAST")
        val restored = fromMap<TestData>(map as Map<String, Any>)

        assertEquals(original, restored)
    }
}
