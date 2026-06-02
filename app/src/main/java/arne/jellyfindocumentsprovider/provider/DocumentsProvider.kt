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
import arne.jellyfindocumentsprovider.hacks.MemoryFileFD
import arne.jellyfindocumentsprovider.hacks.short
import arne.jellyfindocumentsprovider.vfs.FSProvider
import arne.jellyfindocumentsprovider.vfs.FSProvider.getAudioStreamFactory
import arne.jellyfindocumentsprovider.vfs.FSProvider.thumbnailFromCacheOrRemote
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.toVPath
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

    companion object {
        private var traceCounter = 0L
        fun nextTraceId(docId: String): String {
            traceCounter++
            return "${docId.short}-$traceCounter"
        }
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
        val startTime = System.currentTimeMillis()
        logcat { "queryRoots(): projection = $projection" }
        val result = if (ObjectBox.server.all.isEmpty()) MatrixCursor(
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
        logcat(LogPriority.DEBUG) { "queryRoots: done, took ${System.currentTimeMillis() - startTime}ms" }
        return result
    }


    override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
        val startTime = System.currentTimeMillis()
        logcat { "queryDocument: id=$documentId, projection=${projection?.joinToString()}" }
        val vPath =
            documentId?.toVPath() ?: return getEmptyCursor(projection).also { logcat(LogPriority.DEBUG) { "queryDocument: done (empty), took ${System.currentTimeMillis() - startTime}ms" } }
        val result = FSProvider.getOne(vPath).asAndroidMatrixCursor(projection)
        logcat(LogPriority.DEBUG) { "queryDocument: done, took ${System.currentTimeMillis() - startTime}ms" }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?, projection: Array<String>?, sortOrder: String?
    ): Cursor {
        val startTime = System.currentTimeMillis()
        logcat(LogPriority.INFO) { "queryChildDocuments: parent=$parentDocumentId, projection=${projection?.joinToString()}, sort=$sortOrder" }
        return if (parentDocumentId.isNullOrBlank()) {
            logcat(LogPriority.WARN) {
                "queryChildDocuments: parent id is null or blank"
            }
            getEmptyCursor(projection).also {
                logcat(LogPriority.DEBUG) { "queryChildDocuments: done (empty), took ${System.currentTimeMillis() - startTime}ms" }
            }
        } else {
            val vPath = parentDocumentId.toVPath() ?: return getEmptyCursor(projection).also {
                logcat(LogPriority.DEBUG) { "queryChildDocuments: done (no vpath), took ${System.currentTimeMillis() - startTime}ms" }
            }
            val result = FSProvider.getChildren(vPath).asAndroidMatrixCursor(projection)
            logcat(LogPriority.DEBUG) { "queryChildDocuments: done (${result.getCount()} rows), took ${System.currentTimeMillis() - startTime}ms" }
            result
        }
    }

    override fun isChildDocument(parent: String?, document: String?): Boolean {
        val startTime = System.currentTimeMillis()
        logcat { "isChildDocument(): parent = $parent, document = $document" }
        val parentPath = parent.toVPath() ?: return false.also { logcat(LogPriority.DEBUG) { "isChildDocument: false (no parent), took ${System.currentTimeMillis() - startTime}ms" } }
        var current = document.toVPath() ?: return false.also { logcat(LogPriority.DEBUG) { "isChildDocument: false (no doc), took ${System.currentTimeMillis() - startTime}ms" } }
        while (true) {
            if (current == parentPath) {
                logcat(LogPriority.DEBUG) { "isChildDocument: true, took ${System.currentTimeMillis() - startTime}ms" }
                return true
            }
            current = current.parent() ?: return false.also { logcat(LogPriority.DEBUG) { "isChildDocument: false (no parent climb), took ${System.currentTimeMillis() - startTime}ms" } }
        }
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        val startTime = System.currentTimeMillis()
        logcat { "openDocumentThumbnail(${documentId.short}): sizeHint = $sizeHint" }
        val vPath = documentId.toVPath() ?: return null
        val thumbData = providerContext.thumbnailFromCacheOrRemote(vPath, sizeHint)
        logcat(LogPriority.DEBUG) { "openDocumentThumbnail: result from FSProvider = ${thumbData != null} (size=${thumbData?.size ?: 0}) took ${System.currentTimeMillis() - startTime}ms" }
        return thumbData
            ?.let { data ->
                MemoryFileFD(data).use { mf ->
                    AssetFileDescriptor(
                        ParcelFileDescriptor.dup(mf.fd), 0, data.size.toLong()
                    )
                }
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
        val startTime = System.currentTimeMillis()
        val traceId = nextTraceId(documentId)
        logcat(LogPriority.INFO) {
            "openDocument [$traceId] id=$documentId mode=$mode thread=${Thread.currentThread().name}"
        }
        val vPath = documentId.toVPath() ?: return null
        val factoryResult = providerContext.getAudioStreamFactory(
            vPath, when (bitrateLimitType) {
                BitrateLimitType.NONE -> null
                BitrateLimitType.CELL,
                BitrateLimitType.ALL -> bitrateLimits.bps
            }
        )
        if (factoryResult == null) {
            logcat(LogPriority.WARN) { "openDocument [$traceId] no stream factory returned, took ${System.currentTimeMillis() - startTime}ms" }
            return null
        }
        val (fsf, vf, bps) = factoryResult
        val handlerThread = HandlerThread("fdProxyHandler-${documentId.short}").apply { start() }
        val proxy = RandomAccessBucket.proxy(fsf, vf, bps, traceId) {
            handlerThread.quitSafely()
        }
        try {
            return storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.parseMode(mode),
                proxy,
                Handler(handlerThread.looper)
            ).also {
                logcat(LogPriority.DEBUG) { "openDocument [$traceId] done, took ${System.currentTimeMillis() - startTime}ms" }
            }
        } catch (e: Exception) {
            proxy.onRelease()
            logcat(LogPriority.DEBUG) { "openDocument [$traceId] error: ${e.message}, took ${System.currentTimeMillis() - startTime}ms" }
            throw e
        }
    }

    private fun getEmptyCursor(projection: Array<String>?) = MatrixCursor(projection.toProjection())
}
