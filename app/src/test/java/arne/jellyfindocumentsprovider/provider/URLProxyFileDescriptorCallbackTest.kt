package arne.jellyfindocumentsprovider.provider

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class URLProxyFileDescriptorCallbackTest {

    @Test
    fun onGetSize_delegatesToRandomAccess() {
        val ra = mockk<RandomAccess>(relaxed = true) {
            every { length } returns 5000L
        }
        val callback = URLProxyFileDescriptorCallback(ra) {}

        assertEquals(5000L, callback.onGetSize())
        verify(exactly = 1) { ra.length }
    }

    @Test
    fun onGetSize_zero() {
        val ra = mockk<RandomAccess>(relaxed = true) {
            every { length } returns 0L
        }
        val callback = URLProxyFileDescriptorCallback(ra) {}

        assertEquals(0L, callback.onGetSize())
    }

    @Test
    fun onRead_delegatesToRandomAccess() {
        val data = ByteArray(10)
        val ra = mockk<RandomAccess>(relaxed = true) {
            every { read(100, 10, data) } returns 10
        }
        val callback = URLProxyFileDescriptorCallback(ra) {}

        val result = callback.onRead(100, 10, data)
        assertEquals(10, result)
        verify(exactly = 1) { ra.read(100, 10, data) }
    }

    @Test
    fun onRead_partialRead_returnsActual() {
        val data = ByteArray(10)
        val ra = mockk<RandomAccess>(relaxed = true) {
            every { read(0, 10, data) } returns 7
        }
        val callback = URLProxyFileDescriptorCallback(ra) {}

        val result = callback.onRead(0, 10, data)
        assertEquals(7, result)
    }

    @Test
    fun onRead_negativeReturnsZero() {
        val data = ByteArray(10)
        val ra = mockk<RandomAccess>(relaxed = true) {
            every { read(0, 10, data) } returns -1
        }
        val callback = URLProxyFileDescriptorCallback(ra) {}

        val result = callback.onRead(0, 10, data)
        assertEquals(0, result)
    }

    @Test
    fun onRead_zeroReturnsZero() {
        val data = ByteArray(10)
        val ra = mockk<RandomAccess>(relaxed = true) {
            every { read(0, 10, data) } returns 0
        }
        val callback = URLProxyFileDescriptorCallback(ra) {}

        val result = callback.onRead(0, 10, data)
        assertEquals(0, result)
    }

    @Test
    fun onRelease_callsReleaseLambda() {
        var released = false
        val ra = mockk<RandomAccess>(relaxed = true)
        val callback = URLProxyFileDescriptorCallback(ra) { released = true }

        callback.onRelease()
        assertTrue("release lambda should be called", released)
    }
}
