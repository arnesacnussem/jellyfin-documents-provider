package arne.jellyfindocumentsprovider.provider

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.Bundle
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
import com.maxmpz.poweramp.player.TrackProviderConsts
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
        logcat(LogPriority.DEBUG) { "queryRoots: ${result.count} roots, ${System.currentTimeMillis() - startTime}ms" }
        return result
    }


    override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
        val startTime = System.currentTimeMillis()
        val vPath =
            documentId?.toVPath() ?: return getEmptyCursor(projection)
        val result = FSProvider.getOne(vPath).asAndroidMatrixCursor(projection)
        logcat(LogPriority.DEBUG) { "queryDocument: ${documentId.short} → ${result.count} rows, ${System.currentTimeMillis() - startTime}ms" }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?, projection: Array<String>?, sortOrder: String?
    ): Cursor {
        val startTime = System.currentTimeMillis()
        if (parentDocumentId.isNullOrBlank()) {
            return getEmptyCursor(projection)
        }
        val vPath = parentDocumentId.toVPath() ?: return getEmptyCursor(projection)
        val result = FSProvider.getChildren(vPath).asAndroidMatrixCursor(projection)
        logcat(LogPriority.DEBUG) { "queryChildDocuments: parent=$parentDocumentId → ${result.count} rows, ${System.currentTimeMillis() - startTime}ms" }
        return result
    }

    override fun isChildDocument(parent: String?, document: String?): Boolean {
        val startTime = System.currentTimeMillis()
        val parentPath = parent.toVPath() ?: return false
        var current = document.toVPath() ?: return false
        while (true) {
            if (current == parentPath) {
                logcat(LogPriority.DEBUG) { "isChildDocument: true, ${System.currentTimeMillis() - startTime}ms" }
                return true
            }
            current = current.parent() ?: break
        }
        logcat(LogPriority.DEBUG) { "isChildDocument: false, ${System.currentTimeMillis() - startTime}ms" }
        return false
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        val startTime = System.currentTimeMillis()
        val vPath = documentId.toVPath() ?: return null
        val thumbData = providerContext.thumbnailFromCacheOrRemote(vPath, sizeHint)
        logcat(LogPriority.DEBUG) { "openDocumentThumbnail(${documentId.short}): result=${thumbData != null} size=${thumbData?.size ?: 0}, ${System.currentTimeMillis() - startTime}ms" }
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

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            TrackProviderConsts.CALL_RESCAN -> {
                logcat { "call: CALL_RESCAN" }
                Bundle()
            }
            else -> super.call(method, arg, extras)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String, mode: String?, signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        val startTime = System.currentTimeMillis()
        val traceId = nextTraceId(documentId)
        val vPath = documentId.toVPath() ?: return null
        val factoryResult = providerContext.getAudioStreamFactory(
            vPath, when (bitrateLimitType) {
                BitrateLimitType.NONE -> null
                BitrateLimitType.CELL,
                BitrateLimitType.ALL -> bitrateLimits.bps
            }
        )
        if (factoryResult == null) {
            logcat(LogPriority.DEBUG) { "openDocument [$traceId] no stream factory, ${System.currentTimeMillis() - startTime}ms" }
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
                logcat(LogPriority.DEBUG) { "openDocument [$traceId] done, ${System.currentTimeMillis() - startTime}ms" }
            }
        } catch (e: Exception) {
            proxy.onRelease()
            logcat(LogPriority.DEBUG) { "openDocument [$traceId] error: ${e.message}, ${System.currentTimeMillis() - startTime}ms" }
            throw e
        }
    }

    private fun getEmptyCursor(projection: Array<String>?) = MatrixCursor(projection.toProjection())
}
