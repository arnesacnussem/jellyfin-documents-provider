package arne.jellyfindocumentsprovider.vfs

import android.provider.DocumentsContract.Document
import android.provider.MediaStore.Audio.AudioColumns
import arne.jellyfindocumentsprovider.provider.asDocumentProjection
import org.junit.Assert.*
import org.junit.Test

class VirtualFileProjectionTest {

    private fun createVirtualFile(
        name: String,
        documentId: String,
        mimeType: String,
        displayName: String,
        lastModified: Long,
        size: Long,
        duration: Long?,
        year: Int?,
        title: String?,
        album: String?,
        track: Int?,
        artist: String?,
        bitrate: Int?,
        albumId: String?,
        albumCoverTag: String?,
    ): VirtualFile {
        val item = ItemRecord(
            name = name,
            documentId = documentId,
            mimeType = mimeType,
            displayName = displayName,
            lastModified = lastModified,
            size = size,
            duration = duration,
            year = year,
            title = title,
            album = album,
            track = track,
            artist = artist,
            bitrate = bitrate,
            albumId = albumId,
            albumCoverTag = albumCoverTag,
        )
        val vf = VirtualFile(
            id = 1,
            documentId = documentId,
            libId = "lib1",
            serverId = 1L,
            albumId = albumId,
        )
        vf.item.target = item
        return vf
    }

    @Test
    fun virtualFileAsProjectionBasic() {
        val file = createVirtualFile(
            name = "test.mp3",
            documentId = "123",
            mimeType = "audio/mpeg",
            displayName = "Test",
            lastModified = 1000L,
            size = 5000L,
            duration = 200L,
            year = 2024,
            title = "Test Song",
            album = "Test Album",
            track = 3,
            artist = "Test Artist",
            bitrate = 320,
            albumId = "album1",
            albumCoverTag = "tag1",
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
