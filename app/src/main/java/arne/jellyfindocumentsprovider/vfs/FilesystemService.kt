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

/**
 * Injectable service for VFS queries. Uses repositories for persistence
 * and a JellyfinApi factory for remote data. No Context dependency.
 */
class FilesystemService(
    private val repos: AppRepos,
    private val apiFactory: (JellyfinServer) -> JellyfinApi,
) {
    fun resolveName(vpath: VPath): String? {
        return when (vpath) {
            is VPath.User -> repos.server.findByUUID(vpath.id)?.let { "${it.name}@${it.serverName}" }
            is VPath.Library -> repos.server.findByLibraryId(vpath.id)?.library?.get(vpath.id)
            is VPath.Album -> repos.albumInfo.findAlbumByUUID(vpath.id).firstOrNull()?.name
            is VPath.File -> repos.virtualFile.findByDocumentId(vpath.id)?.name
        }
    }

    fun getRoots(): List<List<Pair<String, Any?>>> {
        val servers = repos.server.findAll()
        logcat { "FilesystemService.getRoots(): amount of servers = ${servers.size}" }
        return servers.map { rootProjection(VPath.User(it.uuid), it.serverName, it.username, it.lastUpdateAt) }
    }

    fun getChildren(document: VPath): List<List<Pair<String, Any?>>> {
        logcat(LogPriority.INFO) { "FilesystemService.getChildren(parent = $document)" }
        return when (document) {
            is VPath.User -> repos.server.findByUUID(document.id)
                ?.getLibrariesProjection(document) ?: emptyList()

            is VPath.Library -> (
                repos.virtualFile.findAllByLibIdNotInAlbum(libId = document.id)
                    .map { it.asDocumentProjection() } +
                repos.albumInfo.findAllByLibId(libId = document.id)
                    .map { it.asDocumentProjection(document) }
            )

            is VPath.Album -> repos.virtualFile.findAllByAlbumId(document.id)
                .map { it.asDocumentProjection() }

            is VPath.File -> emptyList()
        }
    }

    fun getOne(doc: VPath): List<List<Pair<String, Any?>>> {
        return listOf(
            when (doc) {
                is VPath.File -> repos.virtualFile.findByDocumentId(doc.id)
                    ?.asDocumentProjection() ?: emptyList()
                else -> emptyDirProjection(doc.id, resolveName(doc) ?: "")
            }
        )
    }

    fun thumbnailFromCacheOrRemote(doc: VPath, sizeHint: Point?): ByteArray? {
        val vf = repos.virtualFile.findByDocumentId(doc.id) ?: return null
        val tc = if (vf.albumId == null) vf.thumbCache
            else repos.albumInfo.findAlbumByUUID(vf.albumId).firstOrNull()?.thumbCache

        if (tc == null) return null

        val uuid = vf.albumId ?: vf.documentId
        val thumbCache = tc.target
        if (thumbCache.notExists) return null

        return thumbCache.data ?: runBlocking {
            try {
                val api = apiFactory(vf.server.target)
                val data = ThumbnailFetchCoordinator.fetch(uuid) {
                    api.downloadThumbnail(
                        itemId = uuid,
                        width = sizeHint?.x,
                        height = sizeHint?.y
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
        return when (doc) {
            is VPath.File -> {
                val vf = repos.virtualFile.findByDocumentId(doc.id) ?: return null
                val api = apiFactory(vf.server.target)
                runBlocking {
                    api.streamThumbnail(vf.documentId, sizeHint?.x, sizeHint?.y)
                }
            }
            else -> null
        }
    }

    fun getAudioStreamFactory(doc: VPath, bps: Int?): Triple<FileStreamFactory, VirtualFile, Int>? {
        if (doc is VPath.File) {
            val vf = repos.virtualFile.findByDocumentId(doc.id) ?: return null
            val api = apiFactory(vf.server.target)
            val fsf = runBlocking { api.getAudioStreamFactory(doc.id, bps ?: -1) }
            return Triple(fsf, vf, bps ?: -1)
        }
        return null
    }
}
