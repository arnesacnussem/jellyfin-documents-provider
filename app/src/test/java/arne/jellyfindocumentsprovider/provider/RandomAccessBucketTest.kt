package arne.jellyfindocumentsprovider.provider

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [RandomAccessBucket] ref counting and proxy registration logic.
 *
 * Since [RandomAccessBucket.newBufferedRA] touches ObjectBox / Ktor / Android
 * dependencies, we avoid calling it. Instead we populate the internal
 * [HashMap] fields (`mapper`, `refCnt`) via reflection and test the
 * public [RandomAccessBucket.proxy]  and private [RandomAccessBucket.releaseRA]
 * in isolation.
 */
class RandomAccessBucketTest {

    @Before
    fun setUp() {
        // Pick a temp root so [RandomAccessBucket] is fully initialized
        RandomAccessBucket.init(java.nio.file.Files.createTempDirectory("rab-test"))
    }

    @After
    fun tearDown() {
        // Clear internal state via reflection
        refCnt().clear()
        mapper().clear()
    }

    // ─── Reflection helpers ────────────────────────────────────────

    private val refCntField by lazy {
        RandomAccessBucket::class.java.getDeclaredField("refCnt").also { it.isAccessible = true }
    }

    private val mapperField by lazy {
        RandomAccessBucket::class.java.getDeclaredField("mapper").also { it.isAccessible = true }
    }

    @Suppress("UNCHECKED_CAST")
    private fun refCnt(): MutableMap<String, Int> =
        refCntField.get(RandomAccessBucket) as MutableMap<String, Int>

    @Suppress("UNCHECKED_CAST")
    private fun mapper(): MutableMap<String, RandomAccess> =
        mapperField.get(RandomAccessBucket) as MutableMap<String, RandomAccess>

    /**
     * Invoke the private [RandomAccessBucket.releaseRA] via reflection.
     */
    private fun invokeReleaseRA(key: String) {
        val method = RandomAccessBucket::class.java.declaredMethods
            .first { it.name == "releaseRA" }
        method.isAccessible = true
        method.invoke(RandomAccessBucket, key)
    }

    // ─── Ref count [refCnt] map tests ─────────────────────────────

    @Test
    fun refCntStartsEmpty() {
        assertTrue("refCnt should start empty", refCnt().isEmpty())
    }

    @Test
    fun refCntManualIncrement() {
        refCnt()["doc-1"] = 3
        assertEquals(3, refCnt()["doc-1"])
    }

    @Test
    fun refCntMultipleDocsSeparate() {
        refCnt()["a"] = 1
        refCnt()["b"] = 5
        assertEquals(1, refCnt()["a"])
        assertEquals(5, refCnt()["b"])
    }

    @Test
    fun refCntRemoveEntry() {
        refCnt()["doc"] = 2
        refCnt().remove("doc")
        assertNull(refCnt()["doc"])
        assertTrue(refCnt().isEmpty())
    }

    // ─── Release logic tests ──────────────────────────────────────

    @Test
    fun releaseDecrementsRefCountFromTwoToOne() {
        refCnt()["doc"] = 2
        mapper()["doc"] = mockk(relaxed = true)

        invokeReleaseRA("doc")

        assertEquals("ref count decremented", 1, refCnt()["doc"])
        assertTrue("mapper entry preserved", mapper().containsKey("doc"))
    }

    @Test
    fun releaseRemovesMapperEntryWhenRefCountReachesZero() {
        refCnt()["doc"] = 1
        val ra = mockk<RandomAccess>(relaxed = true)
        mapper()["doc"] = ra

        invokeReleaseRA("doc")

        assertNull("ref cnt removed", refCnt()["doc"])
        assertFalse("mapper entry removed", mapper().containsKey("doc"))
    }

    @Test
    fun releaseClosesRandomAccessWhenRefCountReachesZero() {
        refCnt()["doc"] = 1
        val ra = mockk<RandomAccess>(relaxed = true)
        every { ra.close() } just Runs
        mapper()["doc"] = ra

        invokeReleaseRA("doc")

        verify(exactly = 1) { ra.close() }
    }

    @Test
    fun releaseOnlyClosesOnceOnLastRelease() {
        refCnt()["doc"] = 3
        val ra = mockk<RandomAccess>(relaxed = true)
        every { ra.close() } just Runs
        mapper()["doc"] = ra

        // Partial releases → close NOT called
        invokeReleaseRA("doc") // 3 → 2
        invokeReleaseRA("doc") // 2 → 1
        verify(exactly = 0) { ra.close() }

        // Final release → close called once
        invokeReleaseRA("doc") // 1 → 0
        verify(exactly = 1) { ra.close() }
    }

    @Test
    fun releaseDoesNotCloseOnPartialRelease() {
        refCnt()["doc"] = 2
        val ra = mockk<RandomAccess>(relaxed = true)
        every { ra.close() } just Runs
        mapper()["doc"] = ra

        invokeReleaseRA("doc")

        verify(exactly = 0) { ra.close() }
        assertEquals(1, refCnt()["doc"])
    }

    @Test
    fun releaseOnNonExistentKeyDoesNotThrow() {
        invokeReleaseRA("non-existent")
    }

    @Test
    fun releaseOnDeletedKeyDoesNotThrow() {
        refCnt()["doc"] = 1
        mapper()["doc"] = mockk(relaxed = true)
        invokeReleaseRA("doc")
        // Already removed — releasing again should be safe
        invokeReleaseRA("doc")
    }

    @Test
    fun releaseOnKeyWithRefCntOnlyNoMapperEntry() {
        // Edge case: refCnt has entry but mapper doesn't
        refCnt()["orphan"] = 1

        invokeReleaseRA("orphan")

        assertNull("orphan ref cnt removed", refCnt()["orphan"])
    }

    // ─── Mapper state tests ───────────────────────────────────────

    @Test
    fun mapperEntryIsPreservedAfterPartialRelease() {
        refCnt()["doc"] = 2
        mapper()["doc"] = mockk(relaxed = true)

        invokeReleaseRA("doc")

        assertTrue("mapper entry must exist after partial release", mapper().containsKey("doc"))
        assertNotNull("mapper entry value preserved", mapper()["doc"])
    }

    @Test
    fun mapperEntryRemovedAfterFullRelease() {
        refCnt()["doc"] = 1
        mapper()["doc"] = mockk(relaxed = true)

        invokeReleaseRA("doc")

        assertFalse("mapper entry gone after full release", mapper().containsKey("doc"))
    }

    @Test
    fun multipleDocsIndependentRelease() {
        refCnt()["a"] = 2
        refCnt()["b"] = 1
        mapper()["a"] = mockk(relaxed = true)
        mapper()["b"] = mockk(relaxed = true)

        invokeReleaseRA("a") // a: 2 → 1
        invokeReleaseRA("b") // b: 1 → 0

        assertTrue("doc-a mapper entry preserved", mapper().containsKey("a"))
        assertFalse("doc-b mapper entry removed", mapper().containsKey("b"))
        assertEquals("doc-a ref cnt", 1, refCnt()["a"])
        assertNull("doc-b ref cnt removed", refCnt()["b"])
    }

    // ─── Combined scenarios ──────────────────────────────────────

    @Test
    fun fullySimulatedGetAndReleaseCycle() {
        // Simulate two getRA calls followed by two releaseRA calls
        val doc = "doc-cycle"
        val ra = mockk<RandomAccess>(relaxed = true)

        // First get
        refCnt()[doc] = (refCnt()[doc] ?: 0) + 1
        if (!mapper().containsKey(doc)) mapper()[doc] = ra

        // Second get
        refCnt()[doc] = (refCnt()[doc] ?: 0) + 1

        assertEquals(2, refCnt()[doc])
        assertSame(ra, mapper()[doc])

        // First release (ref 2 → 1)
        invokeReleaseRA(doc)
        assertEquals(1, refCnt()[doc])
        assertTrue(mapper().containsKey(doc))

        // Second release (ref 1 → 0 → removed)
        invokeReleaseRA(doc)
        assertNull(refCnt()[doc])
        assertFalse(mapper().containsKey(doc))
    }

    @Test
    fun multipleDocsGetAndReleaseScenario() {
        val raA = mockk<RandomAccess>(relaxed = true)
        val raB = mockk<RandomAccess>(relaxed = true)

        // Simulate getRA for doc-a twice, doc-b once
        refCnt()["a"] = 2
        mapper()["a"] = raA
        refCnt()["b"] = 1
        mapper()["b"] = raB

        // Release doc-a once (2→1), then doc-b once (1→0)
        invokeReleaseRA("a")
        invokeReleaseRA("b")

        assertEquals(1, refCnt()["a"])
        assertNull(refCnt()["b"])
        assertTrue(mapper().containsKey("a"))
        assertFalse(mapper().containsKey("b"))
    }

    @Test
    fun refCountEntirelyFromExternalManipulation() {
        // Directly manipulate refCnt without involving mapper
        refCnt()["x"] = 10
        refCnt()["x"] = refCnt()["x"]!! - 1
        assertEquals(9, refCnt()["x"])

        refCnt()["x"] = refCnt()["x"]!! - 9
        assertEquals(0, refCnt()["x"])

        // refCnt at 0 but no mapper entry — releaseRA won't error
        invokeReleaseRA("x")
    }

    @Test
    fun refCountPreventsPrematureRemoval() {
        val doc = "doc-protected"
        refCnt()[doc] = 5
        mapper()[doc] = mockk(relaxed = true)

        // 4 partial releases — entry survives each
        repeat(4) { invokeReleaseRA(doc) }
        assertEquals(1, refCnt()[doc])
        assertTrue("mapper survives 4 of 5 releases", mapper().containsKey(doc))

        // Final release removes it
        invokeReleaseRA(doc)
        assertNull(refCnt()[doc])
        assertFalse("mapper gone after 5th release", mapper().containsKey(doc))
    }
}
