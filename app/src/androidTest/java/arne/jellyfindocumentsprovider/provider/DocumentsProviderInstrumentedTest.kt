package arne.jellyfindocumentsprovider.provider

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import arne.jellyfindocumentsprovider.vfs.VPath
import arne.jellyfindocumentsprovider.vfs.VPathType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DocumentsProviderInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun providerIsRegistered_inManifest() {
        val providers = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PROVIDERS
        ).providers
        assertNotNull("No providers registered in manifest", providers)
        assertTrue(providers!!.any { it.authority == "arne.jellyfindocumentsprovider" })
    }

    @Test
    fun vPathParse_roundTrip_works() {
        // Verify VPath parsing works (used by DocumentsProvider internally)
        val user = VPath.parse("user1")
        assertNotNull(user)
        assertEquals(VPath.User("user1"), user)

        val library = VPath.parse("user1/lib1")
        assertNotNull(library)
        assertTrue(library is VPath.Library)

        val album = VPath.parse("user1/lib1/a_album1")
        assertNotNull(album)
        assertTrue(album is VPath.Album)

        val file = VPath.parse("user1/lib1/f_file1")
        assertNotNull(file)
        assertTrue(file is VPath.File)

        val fileWithAlbum = VPath.parse("user1/lib1/a_album1/f_song1")
        assertNotNull(fileWithAlbum)
        assertTrue(fileWithAlbum is VPath.File)
    }

    @Test
    fun vPath_toString_works() {
        assertEquals("user1", VPath.User("user1").toString())
        assertEquals("user1/lib1", VPath.Library("user1", "lib1").toString())
        assertEquals("user1/lib1/a_album1", VPath.Album("user1", "lib1", "album1").toString())
        assertEquals("user1/lib1/f_file1", VPath.File("user1", "lib1", null, "file1").toString())
        assertEquals(
            "user1/lib1/a_album1/f_file1",
            VPath.File("user1", "lib1", "album1", "file1").toString()
        )
    }

    @Test
    fun vPath_types_areCorrect() {
        assertEquals(VPathType.USER, VPath.User("x").type)
        assertEquals(VPathType.LIBRARY, VPath.Library("x", "y").type)
        assertEquals(VPathType.ALBUM, VPath.Album("x", "y", "z").type)
        assertEquals(VPathType.FILE, VPath.File("x", "y", null, "z").type)
    }

    @Test
    fun vPath_isChildDocument_logic() {
        // DocumentsProvider.isChildDocument uses startsWith
        val parent = VPath.Library("user1", "lib1").toString()
        val child = VPath.File("user1", "lib1", null, "file1").toString()
        assertTrue(child.startsWith(parent))
    }

    @Test
    fun vPath_rootId_returnsUserId() {
        assertEquals("user1", VPath.User("user1").rootId)
        assertEquals("user1", VPath.Library("user1", "lib1").rootId)
        assertEquals("user1", VPath.Album("user1", "lib1", "album1").rootId)
        assertEquals("user1", VPath.File("user1", "lib1", null, "file1").rootId)
    }

    @Test
    fun vPath_parentChain_works() {
        val file = VPath.File("user1", "lib1", "album1", "file1")
        val album = file.parent() as VPath.Album
        assertEquals("album1", album.albumId)

        val lib = album.parent() as VPath.Library
        assertEquals("lib1", lib.libraryId)

        val user = lib.parent() as VPath.User
        assertEquals("user1", user.userId)

        assertNull(user.parent())
    }

    @Test
    fun projectionMapper_isAvailable() {
        // Verify fundamental projection functions work
        val result = emptyDirProjection("test-id", "Test Name")
        assertTrue(result.isNotEmpty())
        assertEquals("test-id", result.first().second)
    }
}
