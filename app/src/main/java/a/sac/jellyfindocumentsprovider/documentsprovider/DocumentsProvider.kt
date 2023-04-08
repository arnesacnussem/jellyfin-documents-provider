package a.sac.jellyfindocumentsprovider.documentsprovider

import a.sac.jellyfindocumentsprovider.R
import a.sac.jellyfindocumentsprovider.TAG
import a.sac.jellyfindocumentsprovider.database.ObjectBox
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.database.entities.brief
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import a.sac.jellyfindocumentsprovider.short
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

class DocumentsProvider : android.provider.DocumentsProvider() {
    private lateinit var jellyfinProvider: JellyfinProvider
    private val providerContext: Context by lazy { context!! }
    private val storageManager: StorageManager by lazy { providerContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager }
    private val fileHandler: Handler = HandlerThread(TAG + "file")
        .apply { start() }
        .let { Handler(it.looper) }

    override fun onCreate(): Boolean {
        jellyfinProvider = JellyfinProvider(requireContext())
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
        logcat { "queryRoots(): projection = $projection" }
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        if (ObjectBox.credentialBox.all.isEmpty()) {
            return result
        }


        ObjectBox.credentialBox.all.forEach {
            val root = result.newRow()
            val docId = DocType.R.docId(it.uid)
            root.add(Root.COLUMN_ROOT_ID, docId)
            root.add(Root.COLUMN_DOCUMENT_ID, docId)
            root.add(Root.COLUMN_SUMMARY, it.username)
            root.add(Root.COLUMN_TITLE, it.serverName)

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
        logcat { "queryDocument: id=$documentId, projection=$projection" }
        val result = MatrixCursor(resolveDocumentProjection(projection))
        if (documentId == null) return result
        when (getDocTypeByDocId(documentId)) {
            // Root Folder contains included libraries
            DocType.R -> {
                addVirtualDirRow(result, documentId, "/")
            }

            DocType.L -> {
                val id = extractUniqueId(documentId)
                val libName = ObjectBox.credentialBox.all.find {
                    it.library.containsKey(id)
                }?.library?.get(id)
                addVirtualDirRow(result, documentId, libName ?: "THIS NAME SHOULD NOT DISPLAY!")
            }

            DocType.File -> {
                val vFile = ObjectBox.getVirtualFileByDocId(documentId)
                addVirtualFileRow(result, vFile)
            }

            else -> TODO("Not yet implemented")
        }

        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?, projection: Array<out String>?, sortOrder: String?
    ): Cursor {
        logcat(LogPriority.INFO) { "queryChildDocuments: parent=$parentDocumentId, projection=$projection, sort=$sortOrder" }
        val cursor = MatrixCursor(resolveDocumentProjection(projection))
        val parentType = getDocTypeByDocId(parentDocumentId!!)
        val xid = extractUniqueId(parentDocumentId)
        when (parentType) {
            DocType.R -> {
                val credential = ObjectBox.getCredentialByUid(xid)
                credential.library.forEach { (id, name) ->
                    addVirtualDirRow(cursor, DocType.L.docId(id), name)
                }
            }

            DocType.L -> {
                ObjectBox.getAllVirtualFileByLibraryId(xid).forEach {
                    addVirtualFileRow(cursor, it)
                }
            }

            else -> throw RuntimeException("no impl.")
        }

        return cursor
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        logcat { "isChildDocument(): parentDocumentId = $parentDocumentId, documentId = $documentId" }
        getDocTypeByDocId(documentId!!)
        val parentType = getDocTypeByDocId(parentDocumentId!!)
        return when (getDocTypeByDocId(documentId)) {
            DocType.File -> {
                val vf = ObjectBox.getVirtualFileByDocId(documentId)
                if (parentType == DocType.R) {
                    //test against root
                    return isChildDocument(parentDocumentId, DocType.L.docId(vf.libId))
                } else {
                    return vf.libId == extractUniqueId(parentDocumentId)
                }
            }

            DocType.L -> {
                val dId = extractUniqueId(documentId)
                val pId = extractUniqueId(parentDocumentId)
                val cred = ObjectBox.getCredentialByUid(pId)

                cred.library.containsKey(dId)
            }

            else -> false
        }
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        logcat { "openDocumentThumbnail(${documentId.short}): sizeHint = $sizeHint" }
        // Check if documentId is not null, otherwise return null
        return ObjectBox.getVirtualFileByDocId(documentId).let { virtualFile ->
            jellyfinProvider.resolveThumbnailURL(virtualFile, sizeHint)?.let { url ->
                logcat(LogPriority.VERBOSE) { "openDocumentThumbnail(${documentId.short}): resolved url=$url" }
                val connection = URL(url).openConnection() as HttpURLConnection
                val length = connection.contentLengthLong
                val (read, write) = ParcelFileDescriptor.createPipe()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (signal?.isCanceled == true) return@launch
                        ParcelFileDescriptor.AutoCloseOutputStream(write).use { output ->
                            connection.inputStream.use { input ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        // Handle any exceptions that occur while downloading the thumbnail
                        logcat(LogPriority.ERROR) { "openDocumentThumbnail: failed to get thumbnail file=${virtualFile.brief} url=$url \n${e.stackTraceToString()}" }
                    }
                }
                AssetFileDescriptor(read, 0, length)
            }
        }

    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String, mode: String?, signal: CancellationSignal?
    ): ParcelFileDescriptor {
        logcat { "openDocument(): documentId = $documentId, mode = $mode" }
        return ObjectBox.getVirtualFileByDocId(documentId).let { vf ->
            jellyfinProvider.resolveFileURL(vf).let { url ->
                RandomAccessBucket.getProxy(url, vf).let {
                    storageManager.openProxyFileDescriptor(
                        ParcelFileDescriptor.parseMode(mode),
                        it,
                        fileHandler
                    )
                }
            }
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
        row.add(Document.COLUMN_DOCUMENT_ID, virtualFile.documentId)
        row.add(Document.COLUMN_DISPLAY_NAME, virtualFile.displayName)
        row.add(Document.COLUMN_SIZE, virtualFile.size)
        row.add(Document.COLUMN_MIME_TYPE, virtualFile.mimeType)
        row.add(Document.COLUMN_LAST_MODIFIED, virtualFile.lastModified)
        row.add(Document.COLUMN_FLAGS, virtualFile.flags)
        appendAudioInfo(row, virtualFile)
    }

    private fun appendAudioInfo(row: MatrixCursor.RowBuilder, virtualFile: VirtualFile) {
        row.add(AudioColumns.DURATION, virtualFile.duration)
        if (virtualFile.year != -1) row.add(AudioColumns.YEAR, virtualFile.year)
        row.add(AudioColumns.TITLE, virtualFile.title)
        row.add(AudioColumns.ALBUM, virtualFile.album)
        row.add(AudioColumns.TRACK, virtualFile.track)
        row.add(AudioColumns.ARTIST, virtualFile.artist)
        row.add(AudioColumns.BITRATE, virtualFile.bitrate)
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