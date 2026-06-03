package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.graphics.Point
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
        logcat(LogPriority.INFO) {
            "FSProvider.queryChildren(parent = $document)"
        }
        return with(ObjectBox) {
            when (document) {
                is VPath.User -> server.findByUUID(document.id)?.getLibrariesProjection(document)
                    ?: emptyList()
                is VPath.Library -> (virtualFile.findAllByLibIdNotInAlbum(libId = document.id)
                    .map { it.asDocumentProjection() } + albumInfo.findAllAlbumByLibId(
                    libId = document.id
                ).map { it.asDocumentProjection(document) })

                is VPath.Album -> virtualFile.findAllByAlbumId(document.id)
                    .map { it.asDocumentProjection() }

                else -> TODO("Not yet implemented")
            }
        }
    }

    fun getOne(doc: VPath) = with(ObjectBox) {
        listOf(
            when (doc) {
                is VPath.File -> virtualFile.findByDocumentId(doc.id)?.asDocumentProjection() ?: emptyList()
                else -> emptyDirProjection(doc.id, resolveName(doc) ?: "")
            }
        )
    }

    fun Context.thumbnailFromCacheOrRemote(doc: VPath, sizeHint: Point?): ByteArray? {
        val vf = ObjectBox.virtualFile.findByDocumentId(doc.id) ?: return null
        val tc =
            if (vf.albumId == null) vf.thumbCache else ObjectBox.albumInfo.findAlbumByUUID(vf.albumId)
                .firstOrNull()?.thumbCache

        if (tc == null || tc.target.notExists) return null

        val uuid = vf.albumId ?: vf.documentId
        return tc.target.data
            ?: runBlocking {
                try {
                    vf.server.target.asAccessor(this@thumbnailFromCacheOrRemote)
                        .downloadThumbnail(
                            itemId = uuid,
                            width = sizeHint?.x,
                            height = sizeHint?.y
                        ).also {
                            tc.target.update {
                                this.data = it
                                this.checkedServer = true
                            }
                        }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to download thumbnail: ${e.message}" }
                    null
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
        return with(ObjectBox) {
            if (doc is VPath.File) {
                val vf = virtualFile.findByDocumentId(doc.id) ?: return null
                val server = vf.server.target.asAccessor(this@getAudioStreamFactory)
                val fsf = runBlocking { server.getAudioFileStreamFactory(doc) }
                return Triple(fsf, vf, bps ?: -1)
            } else null
        }
    }
}

// All projection/mapping functions have been moved to
// arne.jellyfindocumentsprovider.provider.ProjectionMapper