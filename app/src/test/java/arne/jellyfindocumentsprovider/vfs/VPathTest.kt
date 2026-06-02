package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Test

class VPathTest {

    // VPath.parse tests
    @Test
    fun vPathParseUser() {
        val path = VPath.parse("user1")
        assertTrue(path is VPath.User)
        assertEquals("user1", (path as VPath.User).userId)
    }

    @Test
    fun vPathParseLibrary() {
        val path = VPath.parse("user1/lib1")
        assertTrue(path is VPath.Library)
        val lib = path as VPath.Library
        assertEquals("user1", lib.userId)
        assertEquals("lib1", lib.libraryId)
    }

    @Test
    fun vPathParseAlbum() {
        val path = VPath.parse("user1/lib1/a_album1")
        assertTrue(path is VPath.Album)
        val album = path as VPath.Album
        assertEquals("user1", album.userId)
        assertEquals("lib1", album.libraryId)
        assertEquals("album1", album.albumId)
    }

    @Test
    fun vPathParseFileNoAlbum() {
        val path = VPath.parse("user1/lib1/f_file1")
        assertTrue(path is VPath.File)
        val file = path as VPath.File
        assertEquals("user1", file.userId)
        assertEquals("lib1", file.libraryId)
        assertNull(file.albumId)
        assertEquals("file1", file.fileId)
    }

    @Test
    fun vPathParseFileWithAlbum() {
        val path = VPath.parse("user1/lib1/a_album1/f_file1")
        assertTrue(path is VPath.File)
        val file = path as VPath.File
        assertEquals("user1", file.userId)
        assertEquals("lib1", file.libraryId)
        assertEquals("album1", file.albumId)
        assertEquals("file1", file.fileId)
    }

    @Test
    fun vPathParseNomediaReturnsNull() {
        val path = VPath.parse("user1/.nomedia")
        assertNull(path)
    }

    @Test
    fun vPathParseInvalidReturnsNull() {
        val path = VPath.parse("a/b/c/d/e")
        assertNull(path)
    }

    @Test
    fun vPathParseFileWithAlbumPrefix() {
        val path = VPath.parse("user1/lib1/a_album1/f_file1")
        assertTrue(path is VPath.File)
        assertEquals("a_album1/f_file1", "${(path as VPath.File).let { "a_${it.albumId}/f_${it.fileId}" }}")
    }

    // VPath.toString + round-trip tests
    @Test
    fun vPathToStringAndParseRoundTrip() {
        val filePath = VPath.File("user1", "lib1", "album1", "file1")
        val str = filePath.toString()
        val parsed = VPath.parse(str)
        assertEquals(filePath, parsed)
    }

    @Test
    fun vPathUserToString() {
        assertEquals("user1", VPath.User("user1").toString())
    }

    @Test
    fun vPathLibraryToString() {
        assertEquals("user1/lib1", VPath.Library("user1", "lib1").toString())
    }

    @Test
    fun vPathAlbumToString() {
        assertEquals("user1/lib1/a_album1", VPath.Album("user1", "lib1", "album1").toString())
    }

    @Test
    fun vPathFileNoAlbumToString() {
        assertEquals("user1/lib1/f_file1", VPath.File("user1", "lib1", null, "file1").toString())
    }

    @Test
    fun vPathFileWithAlbumToString() {
        assertEquals("user1/lib1/a_album1/f_file1", VPath.File("user1", "lib1", "album1", "file1").toString())
    }

    @Test
    fun uuidAsString() {
        val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", uuid.asString())
    }
}
