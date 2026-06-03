package com.example.jellyfindocumentsprovider

import arne.jellyfindocumentsprovider.vfs.CacheChunks
import arne.jellyfindocumentsprovider.vfs.VPath
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import arne.jellyfindocumentsprovider.vfs.asString
import org.jellyfin.sdk.model.UUID
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    // CacheChunks tests
    @Test
    fun cacheChunksAdd() {
        val chunks = CacheChunks()
        chunks.add(5L..10L)
        chunks.add(1L..3L)
        assertEquals(2, chunks.size)
        assertEquals(1L..3L, chunks[0])
        assertEquals(5L..10L, chunks[1])
    }

    @Test
    fun cacheChunksMerge() {
        val chunks = CacheChunks()
        chunks.add(5L..10L)
        chunks.add(8L..15L)
        assertEquals(1, chunks.size)
        assertEquals(5L..15L, chunks[0])
    }

    @Test
    fun cacheChunksNoGapsIn() {
        val chunks = CacheChunks()
        chunks.add(1L..10L)
        assertTrue(chunks.noGapsIn(2L..5L))
        assertFalse(chunks.noGapsIn(5L..15L))
    }

    @Test
    fun cacheChunksOffsetInChunks() {
        val chunks = CacheChunks()
        chunks.add(10L..20L)
        assertEquals(10L..20L, chunks.offsetInChunks(15L))
        assertNull(chunks.offsetInChunks(5L))
        assertNull(chunks.offsetInChunks(25L))
    }

    @Test
    fun cacheChunksMergeAdjacent() {
        val chunks = CacheChunks()
        chunks.add(1L..5L)
        chunks.add(6L..10L)
        assertEquals(1, chunks.size)
        assertEquals(1L..10L, chunks[0])
    }

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

    // VirtualFile.asProjection tests
    @Test
    fun virtualFileAsProjectionBasic() {
        val file = VirtualFile(
            id = 1,
            name = "test.mp3",
            documentId = "123",
            mimeType = "audio/mpeg",
            displayName = "Test",
            lastModified = 1000L,
            size = 5000L,
            libId = "lib1",
            serverId = 1L,
            duration = 200L,
            year = 2024,
            title = "Test Song",
            album = "Test Album",
            track = 3,
            artist = "Test Artist",
            bitrate = 320,
            albumId = "album1",
            albumCoverTag = "tag1"
        )
        val projection = file.asProjection()
        val projectionMap = projection.toMap()
        assertEquals("test.mp3", projectionMap["display_name"])
        assertEquals("audio/mpeg", projectionMap["mime_type"])
        assertEquals(5000L, projectionMap["_size"])
        assertEquals(1000L, projectionMap["last_modified"])
        assertEquals(200L, projectionMap["duration"])
        assertEquals("Test Song", projectionMap["title"])
        assertEquals("Test Album", projectionMap["album"])
        assertEquals(3, projectionMap["track"])
        assertEquals("Test Artist", projectionMap["artist"])
        assertEquals(320, projectionMap["bitrate"])
        assertEquals(2024, projectionMap["year"])
    }

    // VirtualFile.toVirtualFile / MIME type tests
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
        val uuid = UUID("550e8400-e29b-41d4-a716-446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", uuid.asString())
    }
}
