package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Test

class ThumbCacheTest {

    @Test
    fun notExists_dataNullAndCheckedServer() {
        val tc = ThumbCache(data = null, checkedServer = true)
        assertTrue("notExists should be true when data=null and checkedServer=true", tc.notExists)
    }

    @Test
    fun notExists_dataNullNotChecked() {
        val tc = ThumbCache(data = null, checkedServer = false)
        assertFalse("notExists should be false when data=null and checkedServer=false", tc.notExists)
    }

    @Test
    fun notExists_dataPresentNotChecked() {
        val tc = ThumbCache(data = byteArrayOf(1, 2, 3), checkedServer = false)
        assertFalse("notExists should be false when data is present", tc.notExists)
    }

    @Test
    fun notExists_dataPresentAndChecked() {
        val tc = ThumbCache(data = byteArrayOf(1, 2, 3), checkedServer = true)
        assertFalse("notExists should be false when data is present even if checked", tc.notExists)
    }

    @Test
    fun update_setsDataAndInvokesPersistCallback() {
        val tc = ThumbCache()
        var captured: ThumbCache? = null
        tc.persistCallback = { captured = it }

        tc.update { data = byteArrayOf(42) }

        assertArrayEquals(byteArrayOf(42), tc.data)
        assertSame("persistCallback should have been invoked with the same instance", tc, captured)
    }

    @Test
    fun update_setsCheckedServerAndInvokesCallback() {
        val tc = ThumbCache()
        var captured: ThumbCache? = null
        tc.persistCallback = { captured = it }

        tc.update { checkedServer = true }

        assertTrue(tc.checkedServer)
        assertSame(tc, captured)
    }

    @Test
    fun update_multipleUpdatesCumulative() {
        val tc = ThumbCache()
        var callCount = 0
        tc.persistCallback = { callCount++ }

        tc.update { data = byteArrayOf(1) }
        tc.update { checkedServer = true }
        tc.update { data = byteArrayOf(2) }

        assertEquals(3, callCount)
        assertArrayEquals(byteArrayOf(2), tc.data)
        assertTrue(tc.checkedServer)
    }

    @Test
    fun defaultValues() {
        val tc = ThumbCache()
        assertEquals(0L, tc.id)
        assertNull(tc.data)
        assertFalse(tc.checkedServer)
        assertFalse(tc.notExists)
    }
}
