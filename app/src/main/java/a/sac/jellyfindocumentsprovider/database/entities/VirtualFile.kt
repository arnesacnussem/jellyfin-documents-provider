package a.sac.jellyfindocumentsprovider.database.entities

import a.sac.jellyfindocumentsprovider.utils.readable
import a.sac.jellyfindocumentsprovider.utils.short
import android.database.MatrixCursor
import android.provider.DocumentsContract
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne

@Entity
data class VirtualFile(
    @Id var id: Long = 0,
    @Index val documentId: String,
    val mimeType: String,
    val displayName: String,
    val lastModified: Long,
    val size: Long,

    /**
     * Library ID
     */
    @Index val libId: String,

    /**
     * User ID
     */
    @Index val uid: String,

    // Relation to extra info
    val credentialId: Long,
    val mediaInfoId: Long = 0,
    val powerampExtraInfoId: Long = 0
) {
    lateinit var credential: ToOne<Credential>
    lateinit var mediaInfo: ToOne<MediaInfo>
    lateinit var powerampExtraInfo: ToOne<PowerampExtraInfo>
    val brief
        get() = "VirtualFile(docId=${documentId.short}, name=$displayName, size=${size.readable}, flag=$flag)"

    val flag
        get() = if (mediaInfo.target.hasThumbnail)
            DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        else 0

    fun appendVirtualFileRow(
        cursor: MatrixCursor
    ) {
        val row = cursor.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_SIZE, size)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flag)

        if (this::mediaInfo.isInitialized && mediaInfo.isResolvedAndNotNull)
            mediaInfo.target.appendTo(row)
        if (this::powerampExtraInfo.isInitialized && powerampExtraInfo.isResolvedAndNotNull)
            powerampExtraInfo.target.appendTo(row)
    }
}
