package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.MediaInfo
import a.sac.jellyfindocumentsprovider.R
import a.sac.jellyfindocumentsprovider.TAG
import a.sac.jellyfindocumentsprovider.database.AppDatabase
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.os.storage.StorageManager
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.MediaStore.Audio.AudioColumns
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import kotlin.concurrent.thread

class DocumentsProvider : android.provider.DocumentsProvider() {
    private lateinit var jellyfinProvider: JellyfinProvider
    private lateinit var db: AppDatabase
    private val providerContext: Context by lazy { context!! }
    private val storageManager: StorageManager by lazy { providerContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager }
    private val fileHandler: Handler = HandlerThread(TAG)
        .apply { start() }
        .let { Handler(it.looper) }

    private fun <T> runOnFileHandler(function: suspend () -> T): T {
        return runBlocking(fileHandler.asCoroutineDispatcher()) {
            function()
        }
    }

    override fun onCreate(): Boolean {
        db = AppDatabase.getDatabase(requireContext())
        jellyfinProvider = JellyfinProvider(requireContext())
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.i(TAG, "queryRoots: projection=$projection")
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        if (db.credentialDao().loadAllUsers().isEmpty()) {
            return result
        }

        db.credentialDao().loadAllUsers().forEach {
            val root = result.newRow()
            val docId = DocType.R.docId(it.uid)
            root.add(Root.COLUMN_ROOT_ID, docId)
            root.add(Root.COLUMN_DOCUMENT_ID, docId)
            root.add(Root.COLUMN_SUMMARY, "已选${it.library.size}媒体库")
            root.add(Root.COLUMN_TITLE, "${it.username} - ${it.serverName}")

            // this provider only support "IS_CHILD" query.
            root.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)


            // The child MIME types are used to filter the roots and only present to the user roots
            // that contain the desired type somewhere in their file hierarchy.
            root.add(
                Root.COLUMN_MIME_TYPES, setOf(Document.MIME_TYPE_DIR, Root.MIME_TYPE_ITEM, "*/*")
            )
            root.add(Root.COLUMN_AVAILABLE_BYTES, 0)
            root.add(Root.COLUMN_ICON, R.drawable.ic_launcher_foreground)
        }
        return result
    }


    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        Log.i(TAG, "queryDocument: id=$documentId, projection=$projection")
        val result = MatrixCursor(resolveDocumentProjection(projection))
        if (documentId == null) return result
        when (getDocTypeByDocId(documentId)) {
            // Root Folder contains included libraries
            DocType.R -> {
                addVirtualDirRow(result, documentId, "/")
            }

            DocType.L -> {
                val id = extractUniqueId(documentId)
                val libName = db.credentialDao().loadAllUsers().find {
                    it.library.containsKey(id)
                }?.library?.get(id)
                addVirtualDirRow(result, documentId, libName ?: "THIS NAME SHOULD NOT DISPLAY!")
            }

            DocType.File -> {
                val vFile = db.virtualFileDao().getById(documentId)
                addVirtualFileRow(result, vFile)
            }

            else -> TODO("Not yet implemented")
        }

        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?, projection: Array<out String>?, sortOrder: String?
    ): Cursor {
        Log.i(
            TAG,
            "queryChildDocuments: parent=$parentDocumentId, projection=$projection, sort=$sortOrder"
        )
        val cursor = MatrixCursor(resolveDocumentProjection(projection))
        val parentType = getDocTypeByDocId(parentDocumentId!!)
        val xid = extractUniqueId(parentDocumentId)
        when (parentType) {
            DocType.R -> {
                val credential = db.credentialDao().getByUid(xid)
                credential.library.forEach { (id, name) ->
                    addVirtualDirRow(cursor, DocType.L.docId(id), name)
                }
            }

            DocType.L -> {
                db.virtualFileDao().getAllByLibraryId(xid).forEach {
                    addVirtualFileRow(cursor, it)
                }
            }

            else -> throw RuntimeException("no impl.")
        }

        return cursor
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        Log.i(TAG, "isChildDocument: parent=$parentDocumentId, document=$documentId")
        getDocTypeByDocId(documentId!!)
        val parentType = getDocTypeByDocId(parentDocumentId!!)
        return when (getDocTypeByDocId(documentId)) {
            DocType.File -> {
                val vf = db.virtualFileDao().getById(documentId)
                if (parentType == DocType.R) {
                    //test against root
                    return isChildDocument(parentDocumentId, DocType.L.docId(vf.lid))
                } else {
                    return vf.lid == extractUniqueId(parentDocumentId)
                }
            }

            DocType.L -> {
                val dId = extractUniqueId(documentId)
                val pId = extractUniqueId(parentDocumentId)
                val cred = db.credentialDao().getByUid(pId)

                cred.library.containsKey(dId)
            }

            else -> false
        }
    }

    @WorkerThread
    override fun openDocumentThumbnail(
        documentId: String?,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        Log.d(
            TAG,
            "openDocumentThumbnail() called with: documentId = $documentId, sizeHint = $sizeHint, signal = $signal"
        )
        // Check if documentId is not null, otherwise return null
        if (documentId == null) return null
        val vf = db.virtualFileDao().getById(documentId)
        val url = jellyfinProvider.resolveThumbnailURL(vf)

        try {
            val pipe = ParcelFileDescriptor.createPipe()

            // Start a new thread to download the thumbnail
            thread(start = true) {
                var inputStream: InputStream? = null
                val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

                try {
                    // Download the thumbnail from the given URL
                    inputStream = URL(url).openStream()

                    // Check cancellation before proceeding further
                    if (signal?.isCanceled == true) return@thread

                    // Save the downloaded thumbnail to the output side of the pipe
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    // Handle any exceptions that occur while downloading the thumbnail
                    stacktraceWithVirtualFile(e, documentId)
                    Log.e(
                        TAG,
                        "openDocumentThumbnail: failed with exception in io bridge thread file=$documentId url=$url"
                    )
                } finally {
                    outputStream.close()
                    inputStream?.close()
                }
            }

            // Create and return an AssetFileDescriptor for the read end of the pipe
            return AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)

        } catch (e: Exception) {
            // Handle any exceptions that occur while creating the pipe
            stacktraceWithVirtualFile(e, documentId)
            Log.e(
                TAG,
                "openDocumentThumbnail: failed with exception in main scope file=$documentId url=$url"
            )
            return null
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String?, mode: String?, signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        Log.d(
            TAG,
            "openDocument() called with: documentId = $documentId, mode = $mode, signal = $signal"
        )
        return runOnFileHandler {
            if (documentId == null) return@runOnFileHandler null
            val vf = db.virtualFileDao().getById(documentId)
            val url = URL(jellyfinProvider.resolveStreamingURL(vf))
            URLProxyFileDescriptorCallback(url)
        }?.let { callback ->
            storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.parseMode(mode),
                callback,
                fileHandler
            )
        }

    }

    private fun addVirtualDirRow(
        cursor: MatrixCursor, id: String, name: String
    ) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, id)
        row.add(Document.COLUMN_DISPLAY_NAME, name)
        row.add(Document.COLUMN_SIZE, 0)
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        row.add(Document.COLUMN_LAST_MODIFIED, 0)
        row.add(Document.COLUMN_FLAGS, 0)
    }

    private fun addVirtualFileRow(
        cursor: MatrixCursor, virtualFile: VirtualFile
    ) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, virtualFile.id)
        row.add(Document.COLUMN_DISPLAY_NAME, virtualFile.displayName)
        row.add(Document.COLUMN_SIZE, virtualFile.size)
        row.add(Document.COLUMN_MIME_TYPE, virtualFile.mimeType)
        row.add(Document.COLUMN_LAST_MODIFIED, virtualFile.lastModified)
        row.add(Document.COLUMN_FLAGS, virtualFile.flags)
        appendAudioInfo(row, virtualFile.mediaInfo)
    }

    private fun appendAudioInfo(row: MatrixCursor.RowBuilder, mediaInfo: MediaInfo) {
        row.add(AudioColumns.DURATION, mediaInfo.duration)
        if (mediaInfo.year != -1) row.add(AudioColumns.YEAR, mediaInfo.year)
        row.add(AudioColumns.TITLE, mediaInfo.title)
        row.add(AudioColumns.ALBUM, mediaInfo.album)
        row.add(AudioColumns.TRACK, mediaInfo.track)
        row.add(AudioColumns.ARTIST, mediaInfo.artist)
        row.add(AudioColumns.BITRATE, mediaInfo.bitrate)
    }

    private fun stacktraceWithVirtualFile(e: Exception, documentId: String) {
        try {
            val vf = db.virtualFileDao().getById(documentId)
            Exception(vf.toString(), e).printStackTrace()
        } catch (a: Exception) {
            a.printStackTrace()
        }
    }

    private fun resolveDocumentProjection(projection: Array<out String>?): Array<out String> {
        return projection ?: DEFAULT_DOCUMENT_PROJECTION
    }

    private fun extractUniqueId(docId: String): String {
        return docId.substring(2)
    }


    private fun getDocTypeByDocId(documentId: String): DocType {
        if (documentId[0] == '-') {
            return DocType.valueOf("${documentId[1]}")
        }
        return DocType.File
    }

    enum class DocType {
        /**
         * Root
         */
        R,

        /**
         * Library
         */
        L,

        /**
         * Album
         */
        A,

        /**
         * File
         */
        File;

        fun docId(uniqueId: String): String {
            return "-${this}${uniqueId}"
        }
    }

    companion object {
        private val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
        )
        private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
        )
    }
}