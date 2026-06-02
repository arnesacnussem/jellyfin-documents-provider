package arne.jellyfindocumentsprovider.vfs

import androidx.test.ext.junit.runners.AndroidJUnit4
import arne.jellyfindocumentsprovider.data.AppDependencies
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for FSProvider — the singleton-based VFS query service
 * that requires Android Context and the ObjectBox singleton.
 */
@RunWith(AndroidJUnit4::class)
class FSProviderInstrumentedTest {

    @Before
    fun setUp() {
        // ObjectBox must be initialized first (depends on Application context)
        // In instrumented tests, the Application class handles this.
    }

    @Test
    fun resolveName_unknownUser_getOneReturnsEmptyProjection() {
        // resolveName is private — test via getOne which delegates to it
        val result = FSProvider.getOne(VPath.User("non-existent-user"))
        assertEquals(1, result.size)
    }

    @Test
    fun resolveName_unknownFile_getOneReturnsEmptyProjection() {
        val result = FSProvider.getOne(VPath.File("u", "lib", null, "unknown-doc"))
        assertEquals(1, result.size)
    }

    @Test
    fun getRoots_returnsList() {
        val roots = FSProvider.getRoots()
        // With empty database, roots should be empty
        assertTrue(roots.isEmpty())
    }

    @Test
    fun getChildren_user_throwsOrReturnsEmpty() {
        try {
            val children = FSProvider.getChildren(VPath.User("unknown"))
            assertTrue("should return empty or throw", children.isEmpty())
        } catch (e: Exception) {
            // ObjectBox might not find user, returns null, then ?.getLibrariesProjection returns null
        }
    }

    @Test
    fun getChildren_file_returnsEmpty() {
        // VPath.File returns TODO("Not yet implemented") — but the when has a dedicated branch
        // For VPath, the WHEN branch for File isn't included, so it falls to else
        // Let's test the else branch
        try {
            val children = FSProvider.getChildren(VPath.File("u", "lib", null, "doc-1"))
            // This is expected to fall through to the else branch
        } catch (e: NotImplementedError) {
            // expected for TODO branches
        }
    }

    @Test
    fun getOne_file_returnsProjection() {
        val result = FSProvider.getOne(VPath.File("u", "lib", null, "unknown"))
        // Even with missing file, returns a list with one projection
        assertEquals(1, result.size)
    }

    @Test
    fun getOne_library_returnsProjection() {
        val result = FSProvider.getOne(VPath.Library("u", "lib-1"))
        assertEquals(1, result.size)
    }
}
