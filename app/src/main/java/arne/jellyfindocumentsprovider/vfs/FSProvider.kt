package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.graphics.Point
import android.preference.PreferenceManager
import arne.jellyfindocumentsprovider.common.HighResThumbnailToggle
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.common.getEnum
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
        val result = with(ObjectBox) {
            when (document) {
                is VPath.User -> server.findByUUID(document.id)?.getLibrariesProjection(document)
                    ?: emptyList()
            is VPath.Library -> {
                val serverId = server.findByUUID(document.userId)?.id ?: return emptyList()
                val files = virtualFile.findAllByLibIdNotInAlbum(libId = document.id, serverId = serverId)
                val albums = albumInfo.findAllAlbumByLibId(libId = document.id, serverId = serverId)
                (files.map { it.asDocumentProjection() } + albums.map { it.asDocumentProjection(document) })
                }
                is VPath.Album -> {
                    val serverId = server.findByUUID(document.userId)?.id ?: return emptyList()
                    val files = virtualFile.findAllByAlbumId(document.id, serverId = serverId)
                    files.map { it.asDocumentProjection() }
                }
                else -> TODO("Not yet implemented")
            }
        }
        logcat(LogPriority.DEBUG) { "FSProvider.getChildren($document): ${result.size} rows, ${System.currentTimeMillis() - startTime}ms" }
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

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val highRes = prefs.getEnum<HighResThumbnailToggle>(PrefKeys.HIGH_RES_THUMBNAIL) == HighResThumbnailToggle.ENABLED
        val w = if (highRes) null else sizeHint?.x
        val h = if (highRes) null else sizeHint?.y

        return runBlocking {
            try {
                val api = vf.server.target.asAccessor(this@thumbnailFromCacheOrRemote)
                val data = ThumbnailFetchCoordinator.fetch(uuid) {
                    api.downloadThumbnail(
                        itemId = uuid,
                        width = w,
                        height = h,
                    )
                }
                thumbCache.update {
                    checkedServer = true
                    if (data != null) {
                        this.data = data
                    }
                }
                logcat("Thumbnail", LogPriority.INFO) {
                    if (data != null) "Fetched ${item.name} (${data.size}B, ${System.currentTimeMillis() - startTime}ms)"
                    else "No thumbnail for ${item.name}, ${System.currentTimeMillis() - startTime}ms"
                }
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val highRes = prefs.getEnum<HighResThumbnailToggle>(PrefKeys.HIGH_RES_THUMBNAIL) == HighResThumbnailToggle.ENABLED
        val w = if (highRes) null else sizeHint?.x
        val h = if (highRes) null else sizeHint?.y
        return with(ObjectBox) {
            when (doc) {
                is VPath.File -> {
                    val serverId = server.findByUUID(doc.rootId)?.id ?: return null
                    val vf = virtualFile.findByDocumentId(doc.id, serverId) ?: return null
                    val api = vf.server.target.asAccessor(this@streamThumbnail)
                    runBlocking {
                        api.streamThumbnail(
                            vf.documentId, w, h
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
