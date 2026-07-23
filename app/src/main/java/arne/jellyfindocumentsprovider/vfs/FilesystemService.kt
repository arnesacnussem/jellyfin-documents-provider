package arne.jellyfindocumentsprovider.vfs

import android.graphics.Point
import arne.jellyfindocumentsprovider.data.AppRepos
import arne.jellyfindocumentsprovider.provider.emptyDirProjection
import arne.jellyfindocumentsprovider.provider.asDocumentProjection
import arne.jellyfindocumentsprovider.provider.getLibrariesProjection
import arne.jellyfindocumentsprovider.provider.rootProjection
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat

class FilesystemService(
    private val repos: AppRepos,
    private val apiFactory: (JellyfinServer) -> JellyfinApi,
    private val highResThumbnails: () -> Boolean = { false },
) {
    fun resolveName(vpath: VPath): String? {
        return when (vpath) {
            is VPath.User -> repos.server.findByUUID(vpath.id)?.let { "${it.name}@${it.serverName}" }
            is VPath.Library -> repos.server.findByLibraryId(vpath.id)?.library?.get(vpath.id)
            is VPath.Album -> {
                val serverId = repos.server.findByUUID(vpath.rootId)?.id ?: return null
                repos.albumInfo.findAlbumByUUID(vpath.id, serverId).firstOrNull()?.name
            }
            is VPath.File -> {
                val serverId = repos.server.findByUUID(vpath.rootId)?.id ?: return null
                repos.virtualFile.findByDocumentId(vpath.id, serverId)?.item?.target?.name
            }
        }
    }

    fun getRoots(): List<List<Pair<String, Any?>>> {
        val servers = repos.server.findAll()
        logcat { "FilesystemService.getRoots(): amount of servers = ${servers.size}" }
        return servers.map { rootProjection(VPath.User(it.uuid), it.serverName, it.username, it.lastUpdateAt) }
    }

    fun getChildren(document: VPath): List<List<Pair<String, Any?>>> {
        logcat(LogPriority.DEBUG) { "FilesystemService.getChildren(parent = $document)" }
        return when (document) {
            is VPath.User -> repos.server.findByUUID(document.id)
                ?.getLibrariesProjection(document) ?: emptyList()

            is VPath.Library -> {
                val serverId = repos.server.findByUUID(document.userId)?.id ?: return emptyList()
                (
                    repos.virtualFile.findAllByLibIdNotInAlbum(libId = document.id, serverId = serverId)
                        .map { it.asDocumentProjection() } +
                    repos.albumInfo.findAllByLibId(libId = document.id, serverId = serverId)
                        .map { it.asDocumentProjection(document) }
                )
            }

            is VPath.Album -> {
                val serverId = repos.server.findByUUID(document.userId)?.id ?: return emptyList()
                repos.virtualFile.findAllByAlbumId(document.id, serverId = serverId)
                    .map { it.asDocumentProjection() }
            }

            is VPath.File -> emptyList()
        }
    }

    fun getOne(doc: VPath): List<List<Pair<String, Any?>>> {
        return listOf(
            when (doc) {
                is VPath.File -> {
                    val serverId = repos.server.findByUUID(doc.rootId)?.id ?: 0L
                    repos.virtualFile.findByDocumentId(doc.id, serverId)
                        ?.asDocumentProjection() ?: emptyList()
                }
                else -> emptyDirProjection(doc.id, resolveName(doc) ?: "")
            }
        )
    }

    fun thumbnailFromCacheOrRemote(doc: VPath, sizeHint: Point?): ByteArray? {
        if (doc !is VPath.File) return null
        val serverId = repos.server.findByUUID(doc.rootId)?.id ?: return null
        val vf = repos.virtualFile.findByDocumentId(doc.id, serverId) ?: return null

        val item = vf.item.target
        val tc = if (item.albumId == null) item.thumbCache
            else repos.albumInfo.findAlbumByUUID(item.albumId, vf.serverId).firstOrNull()?.thumbCache

        if (tc == null) return null

        val uuid = item.albumId ?: vf.documentId
        val thumbCache = tc.target
        if (thumbCache.notExists) return null

        return thumbCache.data ?: runBlocking {
            try {
                val api = apiFactory(vf.server.target)
                val w = if (highResThumbnails()) null else sizeHint?.x
                val h = if (highResThumbnails()) null else sizeHint?.y
                val data = ThumbnailFetchCoordinator.fetch(uuid) {
                    api.downloadThumbnail(
                        itemId = uuid,
                        width = w,
                        height = h
                    )
                }
                thumbCache.update {
                    checkedServer = true
                    if (data != null) this.data = data
                }
                data
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to download thumbnail: ${e.message}" }
                null
            }
        }
    }

    fun streamThumbnail(doc: VPath, sizeHint: Point?): JellyfinApi.Stream? {
        val w = if (highResThumbnails()) null else sizeHint?.x
        val h = if (highResThumbnails()) null else sizeHint?.y
        return when (doc) {
            is VPath.File -> {
                val serverId = repos.server.findByUUID(doc.rootId)?.id ?: return null
                val vf = repos.virtualFile.findByDocumentId(doc.id, serverId) ?: return null
                val api = apiFactory(vf.server.target)
                runBlocking {
                    api.streamThumbnail(vf.documentId, w, h)
                }
            }
            else -> null
        }
    }

    fun getAudioStreamFactory(doc: VPath, bps: Int?): Triple<FileStreamFactory, VirtualFile, Int>? {
        if (doc !is VPath.File) return null
        val serverId = repos.server.findByUUID(doc.rootId)?.id ?: return null
        val vf = repos.virtualFile.findByDocumentId(doc.id, serverId) ?: return null
        val api = apiFactory(vf.server.target)
        val fsf = runBlocking { api.getAudioStreamFactory(doc.id, bps ?: -1) }
        return Triple(fsf, vf, bps ?: -1)
    }
}
