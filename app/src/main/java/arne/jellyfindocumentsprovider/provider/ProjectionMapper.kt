package arne.jellyfindocumentsprovider.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.MediaStore.Audio.AudioColumns
import arne.jellyfindocumentsprovider.R
import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.VPath
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import com.maxmpz.poweramp.player.TrackProviderConsts

fun emptyDirProjection(id: String, name: String) = listOf(
    Document.COLUMN_DOCUMENT_ID to id,
    Document.COLUMN_DISPLAY_NAME to name,
    Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
    Document.COLUMN_SIZE to 0,
    Document.COLUMN_LAST_MODIFIED to 0,
    Document.COLUMN_FLAGS to 0
)

fun AlbumInfo.asDocumentProjection(library: VPath.Library): List<Pair<String, Any?>> {
    return listOf(
        Document.COLUMN_DOCUMENT_ID to library.album(uuid),
        Document.COLUMN_DISPLAY_NAME to name,
        Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
        Document.COLUMN_SIZE to 0,
        Document.COLUMN_LAST_MODIFIED to 0,
        Document.COLUMN_FLAGS to 0
    )
}

fun VirtualFile.asDocumentProjection(): List<Pair<String, Any?>> {
    return listOfNotNull(
        Document.COLUMN_DOCUMENT_ID to providerId,
        Document.COLUMN_DISPLAY_NAME to name,
        Document.COLUMN_SIZE to size,
        Document.COLUMN_MIME_TYPE to mimeType,
        Document.COLUMN_LAST_MODIFIED to lastModified,
        Document.COLUMN_FLAGS to Document.FLAG_SUPPORTS_THUMBNAIL,
        TrackProviderConsts.COLUMN_FLAGS to TrackProviderConsts.FLAG_HAS_LYRICS,

        // media info
        AudioColumns.DURATION to duration,
        AudioColumns.TITLE to title,
        AudioColumns.ALBUM to album,
        AudioColumns.TRACK to track,
        AudioColumns.ARTIST to artist,
        AudioColumns.BITRATE to bitrate,
        AudioColumns.YEAR to year,
    )
}

fun JellyfinServer.getLibrariesProjection(user: VPath.User) = library.entries.map { (id, name) ->
    listOf(
        Document.COLUMN_DOCUMENT_ID to user.library(id),
        Document.COLUMN_DISPLAY_NAME to name,
        Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
        Document.COLUMN_SIZE to 0,
        Document.COLUMN_LAST_MODIFIED to 0,
        Document.COLUMN_FLAGS to 0
    )
}

fun rootProjection(credentialId: VPath, serverName: String, username: String, lastUpdate: Long) = listOf(
    Root.COLUMN_ROOT_ID to credentialId,
    Root.COLUMN_DOCUMENT_ID to credentialId,
    Root.COLUMN_SUMMARY to username,
    Root.COLUMN_TITLE to serverName,
    Document.COLUMN_LAST_MODIFIED to lastUpdate,
    Root.COLUMN_FLAGS to Root.FLAG_SUPPORTS_IS_CHILD,
    Root.COLUMN_MIME_TYPES to null,
    Root.COLUMN_AVAILABLE_BYTES to 0,
    Root.COLUMN_ICON to R.drawable.ic_launcher_foreground
)

private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_LAST_MODIFIED,
    Document.COLUMN_FLAGS,
    Document.COLUMN_SIZE,
    Document.COLUMN_ICON,
)

fun Array<String>?.toProjection(): Array<String> {
    return if (this.isNullOrEmpty()) {
        DEFAULT_DOCUMENT_PROJECTION
    } else {
        this
    }
}

fun List<List<Pair<String, Any?>>>.asAndroidMatrixCursor() =
    this.asAndroidMatrixCursor(flatten().map { it.first }.toSet().toTypedArray())

fun List<List<Pair<String, Any?>>>.asAndroidMatrixCursor(projections: Array<String>?): Cursor {
    val cols = projections.toProjection()
    return MatrixCursor(cols).also { c ->
        val colSet = cols.toSet()
        forEach { row ->
            c.newRow().also { rowBuilder ->
                row.forEach { (key, value) ->
                    if (key in colSet) {
                        rowBuilder.add(key, value)
                    }
                }
            }
        }
    }
}
