package arne.jellyfindocumentsprovider.hacks

import org.junit.Assert.*
import org.junit.Test

class HacksExtensionTest {

    // ─── String.short ──────────────────────────────────────────

    @Test
    fun short_returnsFirst8Chars() {
        assertEquals("abcdefgh", "abcdefghijklmnop".short)
    }

    @Test
    fun short_onExactly8Chars() {
        assertEquals("12345678", "12345678".short)
    }

    @Test(expected = StringIndexOutOfBoundsException::class)
    fun short_onShorterThan8() {
        "abc".short // substring(0, 8) on 3-char string throws
    }

    // ─── convertBytesToHumanReadable ───────────────────────────

    @Test
    fun convertBytes_zeroBytes() {
        assertEquals("0 B", convertBytesToHumanReadable(0))
    }

    @Test
    fun convertBytes_under1024() {
        assertEquals("500 B", convertBytesToHumanReadable(500))
        assertEquals("1023 B", convertBytesToHumanReadable(1023))
    }

    @Test
    fun convertBytes_exactly1024() {
        assertEquals("1.00 KB", convertBytesToHumanReadable(1024))
    }

    @Test
    fun convertBytes_kilobytes() {
        assertEquals("1.50 KB", convertBytesToHumanReadable(1536))
        assertEquals("2.00 KB", convertBytesToHumanReadable(2048))
    }

    @Test
    fun convertBytes_megabytes() {
        assertEquals("1.00 MB", convertBytesToHumanReadable(1048576))
        assertEquals("2.50 MB", convertBytesToHumanReadable(2621440))
    }

    @Test
    fun convertBytes_gigabytes() {
        assertEquals("1.00 GB", convertBytesToHumanReadable(1073741824))
    }

    @Test
    fun convertBytes_terabytes() {
        assertEquals("1.00 TB", convertBytesToHumanReadable(1099511627776))
    }

    @Test
    fun convertBytes_maxExactBoundary() {
        // 1024 TB = 1024^5 = 1125899906842624
        assertEquals("1024.00 TB", convertBytesToHumanReadable(1125899906842624))
    }

    // ─── Long.readable ─────────────────────────────────────────

    @Test
    fun longReadable_usesConvertBytes() {
        assertEquals("0 B", 0L.readable)
        assertEquals("1.00 KB", 1024L.readable)
    }

    // ─── Int.readable ──────────────────────────────────────────

    @Test
    fun intReadable_usesConvertBytes() {
        assertEquals("0 B", 0.readable)
        assertEquals("1.00 KB", 1024.readable)
    }

    // ─── Any.TAG ───────────────────────────────────────────────

    @Test
    fun tagForNamedClass_returnsSimpleName() {
        val named = NamedClass()
        assertEquals("NamedClass", named.TAG)
    }

    @Test
    fun tagForAnonymousClass_returnsLast23CharsOrLess() {
        val anon = object : Any() {}
        val tag = anon.TAG
        // Anonymous class name format: package.OuterClass$1$InnerClass
        assertTrue(tag.length <= 23)
    }

    @Test
    fun tagForLongAnonymousClassName_truncated() {
        // Create an anonymous class with a long name via double nesting
        class Outer {
            fun create() = object : Any() {
                val nested = object : Any() {}
            }
        }
        val obj = Outer().create().nested
        val tag = obj.TAG
        assertTrue("TAG should be <= 23 chars, got: $tag (${tag.length})", tag.length <= 23)
    }

    private class NamedClass
}
