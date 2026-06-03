package arne.jellyfindocumentsprovider.vfs

import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.Assert.*
import org.junit.Test

class VPathAdvancedTest {

    @Test
    fun userTypeIsUSER() {
        assertEquals(VPathType.USER, VPath.User("user1").type)
    }

    @Test
    fun libraryTypeIsLIBRARY() {
        assertEquals(VPathType.LIBRARY, VPath.Library("user1", "lib1").type)
    }

    @Test
    fun albumTypeIsALBUM() {
        assertEquals(VPathType.ALBUM, VPath.Album("user1", "lib1", "album1").type)
    }

    @Test
    fun fileTypeIsFILE() {
        assertEquals(VPathType.FILE, VPath.File("user1", "lib1", null, "file1").type)
    }

    @Test
    fun userIdIsUserId() {
        assertEquals("user1", VPath.User("user1").id)
    }

    @Test
    fun fileRootIdIsUserId() {
        val file = VPath.File("user1", "lib1", null, "file1")
        assertEquals("user1", file.rootId)
    }

    @Test
    fun albumRootIdIsUserId() {
        val album = VPath.Album("user1", "lib1", "album1")
        assertEquals("user1", album.rootId)
    }

    @Test
    fun libraryParentIsUser() {
        val lib = VPath.Library("user1", "lib1")
        val parent = lib.parent()
        assertTrue(parent is VPath.User)
        assertEquals("user1", (parent as VPath.User).userId)
    }

    @Test
    fun albumParentIsLibrary() {
        val album = VPath.Album("user1", "lib1", "album1")
        val parent = album.parent()
        assertTrue(parent is VPath.Library)
        val lib = parent as VPath.Library
        assertEquals("user1", lib.userId)
        assertEquals("lib1", lib.libraryId)
    }

    @Test
    fun fileWithAlbumParentIsAlbum() {
        val file = VPath.File("user1", "lib1", "album1", "file1")
        val parent = file.parent()
        assertTrue(parent is VPath.Album)
        val album = parent as VPath.Album
        assertEquals("album1", album.albumId)
    }

    @Test
    fun fileWithoutAlbumParentIsLibrary() {
        val file = VPath.File("user1", "lib1", null, "file1")
        val parent = file.parent()
        assertTrue(parent is VPath.Library)
    }

    @Test
    fun userParentIsNull() {
        assertNull(VPath.User("user1").parent())
    }

    @Test
    fun userLibraryCreatesCorrectPath() {
        val user = VPath.User("user1")
        val lib = user.library("lib1")
        assertEquals("user1/lib1", lib.toString())
    }

    @Test
    fun libraryAlbumCreatesCorrectPath() {
        val lib = VPath.Library("user1", "lib1")
        val album = lib.album("album1")
        assertEquals("user1/lib1/a_album1", album.toString())
    }

    @Test
    fun toVPathOnValidString() {
        val vpath = "user1/lib1".toVPath()
        assertTrue(vpath is VPath.Library)
    }

    @Test
    fun toVPathOnNullReturnsNull() {
        assertNull(null.toVPath())
    }
}
