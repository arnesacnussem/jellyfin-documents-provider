package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.graphics.Point
import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.provider.asDocumentProjection
import arne.jellyfindocumentsprovider.provider.emptyDirProjection
import arne.jellyfindocumentsprovider.provider.getLibrariesProjection
import arne.jellyfindocumentsprovider.provider.rootProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat

object FSProvider {
    private fun resolveName(vpath: VPath): String? = with(ObjectBox) {
        when (vpath) {
            is VPath.User -> server.findByUUID(vpath.id)?.let { "${it.name}@${it.serverName}" }
            is VPath.Library -> server.findByLibraryId(vpath.id)?.library?.get(vpath.id)
            is VPath.Album -> {
                val serverId = server.findByUUID(vpath.rootId)?.id ?: return null
                albumInfo.findAlbumByUUID(vpath.id, serverId).firstOrNull()?.name
            }
            is VPath.File -> {
                val serverId = server.findByUUID(vpath.rootId)?.id ?: return null
                virtualFile.findByDocumentId(vpath.id, serverId)?.item?.target?.name
            }
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
        logcat(LogPriority.DEBUG) {
            "FSProvider.getChildren(parent = $document)"
        }
        val result = with(ObjectBox) {
            when (document) {
                is VPath.User -> server.findByUUID(document.id)?.getLibrariesProjection(document)
                    ?: emptyList()
            is VPath.Library -> {
                val serverId = server.findByUUID(document.userId)?.id ?: return emptyList()
                val files = virtualFile.findAllByLibIdNotInAlbum(libId = document.id, serverId = serverId)
                val albums = albumInfo.findAllAlbumByLibId(libId = document.id, serverId = serverId)
                logcat { "FSProvider.getChildren Library: userId=${document.userId} serverId=$serverId files=${files.size} albums=${albums.size}" }
                (files.map { it.asDocumentProjection() } + albums.map { it.asDocumentProjection(document) })
                }

                is VPath.Album -> {
                    val serverId = server.findByUUID(document.userId)?.id ?: return emptyList()
                    val files = virtualFile.findAllByAlbumId(document.id, serverId = serverId)
                    logcat { "Album ${document.id}: ${files.size} files, types=${files.map { "${it.item.target.name}=${it.item.target.mimeType}" }}" }
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
                is VPath.File -> {
                    val serverId = server.findByUUID(doc.rootId)?.id ?: 0L
                    virtualFile.findByDocumentId(doc.id, serverId)?.asDocumentProjection() ?: emptyList()
                }
                else -> emptyDirProjection(doc.id, resolveName(doc) ?: "")
            }
        )
        logcat(LogPriority.DEBUG) { "FSProvider.getOne($doc): took ${System.currentTimeMillis() - startTime}ms" }
        result
    }

    fun Context.thumbnailFromCacheOrRemote(doc: VPath, sizeHint: Point?): ByteArray? {
        val startTime = System.currentTimeMillis()
        val vf = with(ObjectBox) {
            if (doc is VPath.File) {
                val serverId = server.findByUUID(doc.rootId)?.id ?: return null
                virtualFile.findByDocumentId(doc.id, serverId)
            } else null
        } ?: return null
        val item = vf.item.target
        val tc = if (item.albumId == null) item.thumbCache else ObjectBox.albumInfo.findAlbumByUUID(
            item.albumId, vf.serverId
        ).firstOrNull()?.thumbCache

        if (tc == null) return null

        val uuid = item.albumId ?: vf.documentId
        val thumbCache = tc.target
        if (thumbCache.notExists) return null

        val cached = thumbCache.data
        if (cached != null) return cached

        val metaId = "thumb_$uuid"
        StatusEventManager.startMetadata(metaId, "Fetching thumbnail for ${item.name}")
        logcat("Thumbnail", LogPriority.INFO) { "Fetching thumbnail for ${item.name} (uuid=$uuid)" }

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
                    logcat("Thumbnail", LogPriority.INFO) { "Thumbnail fetched for ${item.name} (${data.size} bytes)" }
                } else {
                    logcat("Thumbnail", LogPriority.INFO) { "No thumbnail available for ${item.name}" }
                }
                logcat(LogPriority.DEBUG) { "thumbnailFromCacheOrRemote: remote fetch done, took ${System.currentTimeMillis() - startTime}ms, size=${data?.size ?: 0}" }
                data
            } catch (e: Exception) {
                logcat("Thumbnail", LogPriority.ERROR) { "Failed to download thumbnail for ${item.name}: ${e.message}" }
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
                    val serverId = server.findByUUID(doc.rootId)?.id ?: return null
                    val vf = virtualFile.findByDocumentId(doc.id, serverId) ?: return null
                    val api = vf.server.target.asAccessor(this@streamThumbnail)
                    runBlocking {
                        api.streamThumbnail(
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
                val serverId = server.findByUUID(doc.rootId)?.id ?: return null
                val vf = virtualFile.findByDocumentId(doc.id, serverId) ?: return null
                val srv = vf.server.target.asAccessor(this@getAudioStreamFactory)
                val fsf = runBlocking { srv.getAudioFileStreamFactory(doc) }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (lyricsCache.findByVfDocId(vf.documentId) == null) {
                            val lyrics = srv.getLyrics(doc.id)
                            lyricsCache.put(LyricsCache(vfDocId = vf.documentId, lyrics = lyrics))
                            logcat(LogPriority.DEBUG) { "Lyrics cached for ${vf.documentId}" }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.DEBUG) { "Failed to cache lyrics for ${vf.documentId}: ${e.message}" }
                    }
                }

                logcat(LogPriority.DEBUG) { "FSProvider.getAudioStreamFactory: done, took ${System.currentTimeMillis() - startTime}ms" }
                Triple(fsf, vf, bps ?: -1)
            } else null
        }
    }
}

// All projection/mapping functions have been moved to
// arne.jellyfindocumentsprovider.provider.ProjectionMapper
