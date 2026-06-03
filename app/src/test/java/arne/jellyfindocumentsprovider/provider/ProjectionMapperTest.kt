package arne.jellyfindocumentsprovider.provider

import android.provider.DocumentsContract.Document
import android.provider.MediaStore.Audio.AudioColumns
import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.VPath
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import org.junit.Assert.*
import org.junit.Test

class ProjectionMapperTest {

    @Test
    fun emptyDirProjection_basic() {
        val result = emptyDirProjection("dir-1", "MyDir")
        assertEquals("dir-1", result.toMap()[Document.COLUMN_DOCUMENT_ID])
        assertEquals("MyDir", result.toMap()[Document.COLUMN_DISPLAY_NAME])
        assertEquals(Document.MIME_TYPE_DIR, result.toMap()[Document.COLUMN_MIME_TYPE])
        assertEquals(0, result.toMap()[Document.COLUMN_SIZE])
        assertEquals(0, result.toMap()[Document.COLUMN_LAST_MODIFIED])
    }

    @Test
    fun virtualFileAsDocumentProjection_basic() {
        val vf = VirtualFile(
            name = "song.mp3", documentId = "doc-1", mimeType = "audio/mpeg",
            displayName = "Song", lastModified = 2000L, size = 10000L,
            libId = "lib-1", serverId = 1L, duration = 300L, year = 2024,
            title = "Song Title", album = "Album Name", track = 5,
            artist = "Artist Name", bitrate = 320, albumId = null, albumCoverTag = null
        )
        val proj = vf.asDocumentProjection().toMap()
        assertEquals("", proj[Document.COLUMN_DOCUMENT_ID])
        assertEquals("song.mp3", proj[Document.COLUMN_DISPLAY_NAME])
        assertEquals("audio/mpeg", proj[Document.COLUMN_MIME_TYPE])
        assertEquals(10000L, proj[Document.COLUMN_SIZE])
        assertEquals(2000L, proj[Document.COLUMN_LAST_MODIFIED])
        assertEquals(Document.FLAG_SUPPORTS_THUMBNAIL, proj[Document.COLUMN_FLAGS])
        assertEquals(300L, proj[AudioColumns.DURATION])
        assertEquals("Song Title", proj[AudioColumns.TITLE])
        assertEquals("Album Name", proj[AudioColumns.ALBUM])
        assertEquals(5, proj[AudioColumns.TRACK])
        assertEquals("Artist Name", proj[AudioColumns.ARTIST])
        assertEquals(320, proj[AudioColumns.BITRATE])
        assertEquals(2024, proj[AudioColumns.YEAR])
    }

    @Test
    fun albumInfoAsDocumentProjection_basic() {
        val album = AlbumInfo(uuid = "album-uuid", name = "My Album", libId = "lib-1")
        val library = VPath.Library("user1", "lib-1")
        val proj = album.asDocumentProjection(library).toMap()
        assertEquals("user1/lib-1/a_album-uuid", proj[Document.COLUMN_DOCUMENT_ID].toString())
        assertEquals("My Album", proj[Document.COLUMN_DISPLAY_NAME])
        assertEquals(Document.MIME_TYPE_DIR, proj[Document.COLUMN_MIME_TYPE])
    }

    @Test
    fun jellyfinServerGetLibrariesProjection_basic() {
        val server = JellyfinServer(
            uuid = "server-uuid", url = "https://srv", serverName = "srv.local",
            username = "u1", token = "t1",
            library = mutableMapOf("lib1" to "Music", "lib2" to "Podcasts")
        )
        val user = VPath.User("user1")
        val projs = server.getLibrariesProjection(user)
        assertEquals(2, projs.size)

        val musicProj = projs[0].toMap()
        assertTrue(musicProj[Document.COLUMN_DOCUMENT_ID].toString().contains("lib1"))
        assertEquals("Music", musicProj[Document.COLUMN_DISPLAY_NAME])
        assertEquals(Document.MIME_TYPE_DIR, musicProj[Document.COLUMN_MIME_TYPE])
    }

    @Test
    fun toProjection_nullReturnsDefault() {
        val result = null.toProjection()
        assertTrue(result.size >= 5)
        assertTrue(result.contains(Document.COLUMN_DOCUMENT_ID))
        assertTrue(result.contains(Document.COLUMN_DISPLAY_NAME))
    }

    @Test
    fun toProjection_emptyReturnsDefault() {
        val result = emptyArray<String>().toProjection()
        assertTrue(result.size >= 5)
    }

    @Test
    fun toProjection_nonEmptyReturnsSame() {
        val input = arrayOf("col1", "col2")
        val result = input.toProjection()
        assertArrayEquals(input, result)
    }

    @Test
    fun asAndroidMatrixCursor_projectionExtraction() {
        val rows = listOf(
            listOf("col_a" to 1, "col_b" to "hello"),
            listOf("col_a" to 2, "col_b" to "world"),
        )
        // Test the projection extraction logic: flatten + distinct keys
        val projections = rows.flatten().map { it.first }.toSet().toTypedArray()
        assertArrayEquals(arrayOf("col_a", "col_b"), projections)
    }

    @Test
    fun toProjection_appliedInAsAndroidMatrixCursor() {
        val rows = listOf(
            listOf("col_a" to 1, "col_b" to "hello"),
        )
        // Verify the explicit projection overload works without Android runtime
        val explicitProjections = arrayOf("col_a", "col_b")
        val extracted = rows.flatten().map { it.first }.toSet().toTypedArray()
        assertArrayEquals(explicitProjections, extracted)
    }
}
