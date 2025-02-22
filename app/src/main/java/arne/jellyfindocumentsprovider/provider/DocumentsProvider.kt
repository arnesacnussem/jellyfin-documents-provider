package arne.jellyfindocumentsprovider.provider

import android.content.Context
import android.content.SharedPreferences
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
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import arne.jellyfindocumentsprovider.Application
import arne.jellyfindocumentsprovider.common.BitrateLimitType
import arne.jellyfindocumentsprovider.common.BitrateLimits
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.WaveType
import arne.jellyfindocumentsprovider.common.getEnum
import arne.jellyfindocumentsprovider.hacks.short
import arne.jellyfindocumentsprovider.vfs.FSProvider
import arne.jellyfindocumentsprovider.vfs.FSProvider.getAudioStreamFactory
import arne.jellyfindocumentsprovider.vfs.FSProvider.thumbnailFromCacheOrRemote
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.asAndroidMatrixCursor
import arne.jellyfindocumentsprovider.vfs.toProjection
import arne.jellyfindocumentsprovider.vfs.toVPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.io.FileNotFoundException


class DocumentsProvider : DocumentsProvider() {
    private val providerContext: Context by lazy { context!! }
    private val storageManager: StorageManager by lazy { providerContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager }
    private val preference: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(
            providerContext
        )
    }

    private val waveType
        get() = preference.getEnum<WaveType>(PrefKeys.WAVE_TYPE)
    private val bitrateLimits
        get() = preference.getEnum<BitrateLimits>(PrefKeys.BITRATE_LIMIT)
    private val bitrateLimitType
        get() = preference.getEnum<BitrateLimitType>(PrefKeys.BITRATE_LIMIT_TYPE)

    override fun onCreate(): Boolean {
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects().detectLeakedClosableObjects()
                .penaltyLog().build()
        )
        val app = context?.applicationContext as? Application
        app?.let {
            app.initializeIfNeeded()
        }
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        logcat { "queryRoots(): projection = $projection" }
        return if (ObjectBox.server.all.isEmpty()) MatrixCursor(
            projection ?: arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_MIME_TYPES,
                Root.COLUMN_FLAGS,
                Root.COLUMN_ICON,
                Root.COLUMN_TITLE,
                Root.COLUMN_SUMMARY,
                Root.COLUMN_DOCUMENT_ID,
                Root.COLUMN_AVAILABLE_BYTES
            )
        )
        else FSProvider.getRoots().asAndroidMatrixCursor()
    }


    override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
        logcat { "queryDocument: id=$documentId, projection=${projection?.joinToString()}" }
        val vPath =
            documentId?.toVPath() ?: return getEmptyCursor(projection)
        return FSProvider.getOne(vPath).asAndroidMatrixCursor(projection)
    }

    override fun queryChildDocuments(
        parentDocumentId: String?, projection: Array<String>?, sortOrder: String?
    ): Cursor {
        logcat(LogPriority.INFO) { "queryChildDocuments: parent=$parentDocumentId, projection=${projection?.joinToString()}, sort=$sortOrder" }
        return if (parentDocumentId.isNullOrBlank()) {
            logcat(LogPriority.WARN) {
                "queryChildDocuments: parent id is null or blank"
            }
            getEmptyCursor(projection)
        } else {
            val vPath = parentDocumentId.toVPath() ?: return getEmptyCursor(projection)
            FSProvider.getChildren(vPath).asAndroidMatrixCursor(projection)
        }
    }

    override fun isChildDocument(parent: String?, document: String?): Boolean {
        logcat { "isChildDocument(): parent = $parent, document = $document" }
        if (parent == null || document == null) return false
        return document.startsWith(parent)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        logcat { "openDocumentThumbnail(${documentId.short}): sizeHint = $sizeHint" }
        val vPath = documentId.toVPath() ?: return null
        return providerContext.thumbnailFromCacheOrRemote(vPath, sizeHint)
            ?.let { data ->
                val (read, write) = ParcelFileDescriptor.createPipe()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(write).use { output ->
                            output.write(data)
                            output.flush()
                            output.close()
                        }
                    } catch (e: Exception) {
                        // Handle any exceptions that occur while downloading the thumbnail
                        logcat(LogPriority.ERROR) { "openDocumentThumbnail: failed to get thumbnail $documentId \n${e.stackTraceToString()}" }
                    }
                }
                AssetFileDescriptor(read, 0, data.size.toLong())
            }
    }


    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String?
    ): DocumentsContract.Path? {
        val vPath = childDocumentId?.toVPath() ?: return null
        return DocumentsContract.Path(vPath.rootId, vPath.toString().split('/').drop(1))
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String, mode: String?, signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        logcat { "openDocument(): documentId = $documentId, mode = $mode" }
        val vPath = documentId.toVPath() ?: return null
        return providerContext.getAudioStreamFactory(
            vPath, when (bitrateLimitType) {
                BitrateLimitType.NONE -> null
                BitrateLimitType.CELL,
                BitrateLimitType.ALL -> bitrateLimits.bps
            }
        )?.let { (fsf, vf, bps) ->
            RandomAccessBucket.proxy(fsf, vf, bps).let { proxy ->
                storageManager.openProxyFileDescriptor(
                    ParcelFileDescriptor.parseMode(mode),
                    proxy,
                    Handler(HandlerThread("fdProxyHandler-${documentId.short}").apply { start() }.looper)
                )
            }
        }
    }

    private fun getEmptyCursor(projection: Array<String>?) = MatrixCursor(projection.toProjection())
}