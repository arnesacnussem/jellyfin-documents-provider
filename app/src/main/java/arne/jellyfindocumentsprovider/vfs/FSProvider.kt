package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.graphics.Point
import arne.jellyfindocumentsprovider.common.InMemoryLogBuffer
import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.provider.asDocumentProjection
import arne.jellyfindocumentsprovider.provider.emptyDirProjection
import arne.jellyfindocumentsprovider.provider.getLibrariesProjection
import arne.jellyfindocumentsprovider.provider.rootProjection
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat

object FSProvider {
    private fun resolveName(vpath: VPath): String? = with(ObjectBox) {
        when (vpath) {
            is VPath.User -> server.findByUUID(vpath.id)?.let { "${it.name}@${it.serverName}" }
            is VPath.Library -> server.findByLibraryId(vpath.id)?.library?.get(vpath.id)
            is VPath.Album -> albumInfo.findAlbumByUUID(vpath.id).firstOrNull()?.name
            is VPath.File -> virtualFile.findByDocumentId(vpath.id)?.name
        }
    }

    fun getRoots(): List<List<Pair<String, Any?>>> {
        val servers = ObjectBox.server.all
        logcat {
            "FSProvider.getRoots(): amount of servers = ${servers.size}"
        }
        return servers.map {
            rootProjection(VPath.User(it.uuid), it.serverName, it.username, it.lastUpdateAt)
        }
    }

    fun getChildren(document: VPath): List<List<Pair<String, Any?>>> {
        val startTime = System.currentTimeMillis()
        logcat(LogPriority.INFO) {
            "FSProvider.getChildren(parent = $document)"
        }
        val result = with(ObjectBox) {
            when (document) {
                is VPath.User -> server.findByUUID(document.id)?.getLibrariesProjection(document)
                    ?: emptyList()
                is VPath.Library -> (virtualFile.findAllByLibIdNotInAlbum(libId = document.id)
                    .map { it.asDocumentProjection() } + albumInfo.findAllAlbumByLibId(
                    libId = document.id
                ).map { it.asDocumentProjection(document) })

                is VPath.Album -> {
                    val files = virtualFile.findAllByAlbumId(document.id)
                    logcat { "Album ${document.id}: ${files.size} files, types=${files.map { "${it.name}=${it.mimeType}" }}" }
                    files.map { it.asDocumentProjection() }
                }

                else -> TODO("Not yet implemented")
            }
        }
        logcat(LogPriority.DEBUG) { "FSProvider.getChildren: done, took ${System.currentTimeMillis() - startTime}ms, rows=${result.size}" }
        return result
    }

    fun getOne(doc: VPath) = with(ObjectBox) {
        val startTime = System.currentTimeMillis()
        val result = listOf(
            when (doc) {
                is VPath.File -> virtualFile.findByDocumentId(doc.id)?.asDocumentProjection() ?: emptyList()
                else -> emptyDirProjection(doc.id, resolveName(doc) ?: "")
            }
        )
        logcat(LogPriority.DEBUG) { "FSProvider.getOne($doc): took ${System.currentTimeMillis() - startTime}ms" }
        result
    }

    fun Context.thumbnailFromCacheOrRemote(doc: VPath, sizeHint: Point?): ByteArray? {
        val startTime = System.currentTimeMillis()
        val vf = ObjectBox.virtualFile.findByDocumentId(doc.id) ?: return null.also { logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: no vf, took ${System.currentTimeMillis() - startTime}ms" } }
        logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: vf found name=${vf.name} documentId=${vf.documentId}" }
        val tc =
            if (vf.albumId == null) vf.thumbCache else ObjectBox.albumInfo.findAlbumByUUID(vf.albumId)
                .firstOrNull()?.thumbCache

        logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: tc=${tc}, tc.target=${tc?.target}, notExists=${tc?.target?.notExists}" }
        if (tc == null) {
            logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: tc null, returning null, took ${System.currentTimeMillis() - startTime}ms" }
            return null
        }

        val uuid = vf.albumId ?: vf.documentId
        logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: data in cache=${tc.target.data != null}, attempting download for uuid=$uuid" }
        val thumbCache = tc.target
        if (thumbCache.notExists) return null

        val cached = thumbCache.data
        if (cached != null) {
            logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: cached hit, took ${System.currentTimeMillis() - startTime}ms" }
            return cached
        }

        val metaId = "thumb_$uuid"
        StatusEventManager.startMetadata(metaId, "Fetching thumbnail for ${vf.name}")
        InMemoryLogBuffer.log(LogPriority.INFO, "Thumbnail", "Fetching thumbnail for ${vf.name} (uuid=$uuid)")

        return runBlocking {
            try {
                val api = vf.server.target.asAccessor(this@thumbnailFromCacheOrRemote)
                val data = ThumbnailFetchCoordinator.fetch(uuid) {
                    api.downloadThumbnail(
                        itemId = uuid,
                        width = sizeHint?.x,
                        height = sizeHint?.y,
                    )
                }
                thumbCache.update {
                    checkedServer = true
                    if (data != null) {
                        this.data = data
                    }
                }
                if (data != null) {
                    InMemoryLogBuffer.log(LogPriority.INFO, "Thumbnail", "Thumbnail fetched for ${vf.name} (${data.size} bytes)")
                } else {
                    InMemoryLogBuffer.log(LogPriority.WARN, "Thumbnail", "No thumbnail available for ${vf.name}")
                }
                logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: remote fetch done, took ${System.currentTimeMillis() - startTime}ms, size=${data?.size ?: 0}" }
                data
            } catch (e: Exception) {
                InMemoryLogBuffer.log(LogPriority.ERROR, "Thumbnail", "Failed to download thumbnail for ${vf.name}: ${e.message}")
                logcat(LogPriority.ERROR) { "Failed to download thumbnail for $uuid: ${e.message}" }
                null
            } finally {
                StatusEventManager.finishMetadata(metaId)
            }
        }
    }


    fun Context.streamThumbnail(doc: VPath, sizeHint: Point?): JellyfinApi.Stream? {
        return with(ObjectBox) {
            when (doc) {
                is VPath.File -> {
                    val vf = virtualFile.findByDocumentId(doc.id) ?: return null
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
        val startTime = System.currentTimeMillis()
        return with(ObjectBox) {
            if (doc is VPath.File) {
                val vf = virtualFile.findByDocumentId(doc.id) ?: return null
                val server = vf.server.target.asAccessor(this@getAudioStreamFactory)
                val fsf = runBlocking { server.getAudioFileStreamFactory(doc) }
                logcat(LogPriority.DEBUG) { "FSProvider.getAudioStreamFactory: done, took ${System.currentTimeMillis() - startTime}ms" }
                Triple(fsf, vf, bps ?: -1)
            } else null
        }
    }
}

// All projection/mapping functions have been moved to
// arne.jellyfindocumentsprovider.provider.ProjectionMapper
