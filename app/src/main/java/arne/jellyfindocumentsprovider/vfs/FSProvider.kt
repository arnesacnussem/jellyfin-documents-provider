package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.database.MatrixCursor
import android.graphics.Point
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.MediaStore.Audio.AudioColumns
import arne.jellyfindocumentsprovider.R
import arne.jellyfindocumentsprovider.common.WaveType
import com.maxmpz.poweramp.player.TrackProviderConsts
import com.maxmpz.poweramp.player.TrackProviderHelper
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat

object FSProvider {
    fun getRoots(): List<List<Pair<String, Any?>>> {
        val servers = ObjectBox.server.all
        logcat {
            "FSProvider.getRoots(): amount of servers = $servers"
        }
        val roots = servers.map {
            val credentialId = VPath.User(it.uuid)
            return@map listOf(
                Root.COLUMN_ROOT_ID to credentialId,
                Root.COLUMN_DOCUMENT_ID to credentialId,
                Root.COLUMN_SUMMARY to it.username,
                Root.COLUMN_TITLE to it.serverName,
                Document.COLUMN_LAST_MODIFIED to it.lastUpdateAt,

                // this provider only support "IS_CHILD" query.
                Root.COLUMN_FLAGS to Root.FLAG_SUPPORTS_IS_CHILD,

                // The child MIME types are used to filter the roots and only present to the user roots
                // that contain the desired type somewhere in their file hierarchy.
                Root.COLUMN_MIME_TYPES to null,


                Root.COLUMN_AVAILABLE_BYTES to 0,
                Root.COLUMN_ICON to R.drawable.ic_launcher_foreground
            )
        }
        return roots
    }

    fun getChildren(document: VPath): List<List<Pair<String, Any?>>> {
        logcat(LogPriority.INFO) {
            "FSProvider.queryChildren(parent = $document)"
        }
        return with(ObjectBox) {
            when (document) {
                is VPath.User -> server.findByUUID(document.id).getLibrariesProjection(document)
                is VPath.Library -> (virtualFile.findAllByLibIdNotInAlbum(libId = document.id)
                    .map { it.asProjection() } + albumInfo.findAllAlbumByLibId(
                    libId = document.id
                ).map { it.asProjection(document) })

                is VPath.Album -> virtualFile.findAllByAlbumId(document.id)
                    .map { it.asProjection() }

                else -> TODO("Not yet implemented")
            }
        }
    }

    fun getOne(doc: VPath) = with(ObjectBox) {
        listOf(
            when (doc) {
                is VPath.File -> virtualFile.findByDocumentId(doc.id).asProjection()
                else -> emptyDirProjection(doc.id, doc.tryResolveName() ?: "")
            }
        )
    }

    fun Context.thumbnailFromCacheOrRemote(doc: VPath, sizeHint: Point?): ByteArray? {
        val vf = ObjectBox.virtualFile.findByDocumentId(doc.id)
        val tc =
            if (vf.albumId == null) vf.thumbCache else ObjectBox.albumInfo.findAlbumByUUID(vf.albumId)
                .first().thumbCache

        if (tc.target.notExists) return null

        val uuid = vf.albumId ?: vf.documentId
        return tc.target.data
            ?: runBlocking {
                vf.server.target.asAccessor(this@thumbnailFromCacheOrRemote)
                    .downloadThumbnail(
                        uuid = uuid,
                        w = sizeHint?.x,
                        h = sizeHint?.y
                    ).also {
                        tc.target.update {
                            this.data = it
                            this.checkedServer = true
                        }
                    }
            }
    }


    fun Context.streamThumbnail(doc: VPath, sizeHint: Point?): JellyfinAccessor.Stream? {
        return with(ObjectBox) {
            when (doc) {
                is VPath.File -> {
                    val vf = virtualFile.findByDocumentId(doc.id)
                    val server = vf.server.target.asAccessor(this@streamThumbnail)
                    runBlocking {
                        server.streamThumbnail(
                            vf.documentId, sizeHint?.x, sizeHint?.y
                        )
                    }
                }

                else -> null
            }
        }
    }

    fun Context.getAudioStreamFactory(
        doc: VPath, bps: Int?
    ): Triple<FileStreamFactory, VirtualFile, Int>? {
        return with(ObjectBox) {
            if (doc is VPath.File) {
                val vf = virtualFile.findByDocumentId(doc.id)
                val server = vf.server.target.asAccessor(this@getAudioStreamFactory)
                val fsf = runBlocking { server.getAudioFileStreamFactory(doc) }
                return Triple(fsf, vf, bps ?: -1)
            } else null
        }
    }
}

val EMPTY_DIR_PROJECTION = listOf(
    Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
    Document.COLUMN_SIZE to 0,
    Document.COLUMN_LAST_MODIFIED to 0,
    Document.COLUMN_FLAGS to 0
)

fun emptyDirProjection(id: String, name: String) = listOf(
    Document.COLUMN_DOCUMENT_ID to id,
    Document.COLUMN_DISPLAY_NAME to name,
    Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
    Document.COLUMN_SIZE to 0,
    Document.COLUMN_LAST_MODIFIED to 0,
    Document.COLUMN_FLAGS to 0
)

fun AlbumInfo.asProjection(library: VPath.Library): List<Pair<String, Any?>> {
    return listOf(
        Document.COLUMN_DOCUMENT_ID to library.album(uuid),
        Document.COLUMN_DISPLAY_NAME to name,
        Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
        Document.COLUMN_SIZE to 0,
        Document.COLUMN_LAST_MODIFIED to 0,
        Document.COLUMN_FLAGS to 0
    )
}

fun VirtualFile.asProjection(): List<Pair<String, Any?>> {
    return listOfNotNull(
        Document.COLUMN_DOCUMENT_ID to providerId,
        Document.COLUMN_DISPLAY_NAME to name,
        Document.COLUMN_SIZE to size,
        Document.COLUMN_MIME_TYPE to mimeType,
        Document.COLUMN_LAST_MODIFIED to lastModified,
        Document.COLUMN_FLAGS to Document.FLAG_SUPPORTS_THUMBNAIL,

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

fun PowerampExtraInfo.asProjection(waveType: WaveType) = listOfNotNull(
    lyrics?.let { TrackProviderConsts.COLUMN_FLAGS to TrackProviderConsts.FLAG_HAS_LYRICS },
    lyrics?.let { TrackProviderConsts.COLUMN_TRACK_LYRICS_SYNCED to it },
    when (waveType) {
        WaveType.NONE -> TrackProviderConsts.COLUMN_TRACK_WAVE to byteArrayOf()
        WaveType.FAKE -> TrackProviderConsts.COLUMN_TRACK_WAVE to TrackProviderHelper.floatsToBytes(
            getFakeWave()
        )

        WaveType.REAL -> {}
    }
)
//
//fun List<Map<String, Any>>.asAndroidMatrixCursor() = MatrixCursor(
//    flatMap { it.keys }.toSet().toTypedArray()
//).also { c -> forEach { c.addRow(it) } }

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

fun List<List<Pair<String, Any?>>>.asAndroidMatrixCursor(projections: Array<String>?) =
    MatrixCursor(
        projections.toProjection()
    ).also { c ->
        forEach {
            c.newRow().also { row ->
                it.forEach { (key, value) -> row.add(key, value) }
            }
        }
    }