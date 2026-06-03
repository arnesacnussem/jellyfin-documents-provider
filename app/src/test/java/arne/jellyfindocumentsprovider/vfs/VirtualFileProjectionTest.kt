package arne.jellyfindocumentsprovider.vfs

import android.provider.DocumentsContract.Document
import android.provider.MediaStore.Audio.AudioColumns
import arne.jellyfindocumentsprovider.provider.asDocumentProjection
import org.junit.Assert.*
import org.junit.Test

class VirtualFileProjectionTest {

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
        val projection = file.asDocumentProjection()
        val projectionMap = projection.toMap()
        assertEquals("", projectionMap[Document.COLUMN_DOCUMENT_ID])
        assertEquals("test.mp3", projectionMap[Document.COLUMN_DISPLAY_NAME])
        assertEquals("audio/mpeg", projectionMap[Document.COLUMN_MIME_TYPE])
        assertEquals(5000L, projectionMap[Document.COLUMN_SIZE])
        assertEquals(1000L, projectionMap[Document.COLUMN_LAST_MODIFIED])
        assertEquals(200L, projectionMap[AudioColumns.DURATION])
        assertEquals("Test Song", projectionMap[AudioColumns.TITLE])
        assertEquals("Test Album", projectionMap[AudioColumns.ALBUM])
        assertEquals(3, projectionMap[AudioColumns.TRACK])
        assertEquals("Test Artist", projectionMap[AudioColumns.ARTIST])
        assertEquals(320, projectionMap[AudioColumns.BITRATE])
        assertEquals(2024, projectionMap[AudioColumns.YEAR])
    }
}
