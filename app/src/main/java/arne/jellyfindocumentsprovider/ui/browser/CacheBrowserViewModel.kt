package arne.jellyfindocumentsprovider.ui.browser

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import arne.jellyfindocumentsprovider.data.AppDependencies
import arne.jellyfindocumentsprovider.hacks.readable
import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.AlbumInfo_
import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.CacheInfo_
import arne.jellyfindocumentsprovider.vfs.ItemRecord
import arne.jellyfindocumentsprovider.vfs.ItemRecord_
import arne.jellyfindocumentsprovider.vfs.JellyfinAccessor
import arne.jellyfindocumentsprovider.vfs.LyricsCache
import arne.jellyfindocumentsprovider.vfs.LyricsCache_
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.ThumbCache
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import java.io.File

enum class BrowserFilter(val label: String) {
    ALL("All"),
    CACHED("Cached"),
    NOT_CACHED("Not Cached"),
}

class CacheBrowserViewModel(application: Application) : AndroidViewModel(application) {
    var entries by mutableStateOf<List<CacheBrowserEntry>>(emptyList())
        private set
    var filteredEntries by mutableStateOf<List<CacheBrowserEntry>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var filterMode by mutableStateOf(BrowserFilter.ALL)
        private set
    var jumpArtist by mutableStateOf<String?>(null)
        private set
    var jumpAlbum by mutableStateOf<String?>(null)
        private set

    var downloadingIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var totalCacheSize by mutableStateOf(0L)
        private set

    private var allArtists by mutableStateOf<List<String>>(emptyList())
    private var allAlbums by mutableStateOf<List<Pair<String, String?>>>(emptyList())

    val artists: List<String> get() = allArtists
    val albums: List<Pair<String, String?>> get() = allAlbums

    init {
        loadEntries()
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                val needsReload = withContext(Dispatchers.IO) {
                    ObjectBox.virtualFile.count() != entries.size.toLong() || entries.isEmpty()
                }
                if (needsReload) {
                    withContext(Dispatchers.IO) { reloadData() }
                    applyFilters()
                } else {
                    val updated = withContext(Dispatchers.IO) { refreshStatusesOnIO() }
                    if (updated != null) {
                        entries = updated
                        totalCacheSize = updated.sumOf { it.fileCacheStatus.cachedBytes() }
                        applyFilters()
                    }
                }
            }
        }
    }

    fun loadEntries() {
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) { reloadData() }
            applyFilters()
            isLoading = false
        }
    }

    private fun reloadData() {
        val lyricsMap = ObjectBox.lyricsCache.all
            .mapNotNull { entry -> entry.lyrics?.let { entry.vfDocId to true } }
            .toMap()

        val thumbMap = ObjectBox.thumbCache.all
            .filter { it.data != null }
            .map { it.id to true }
            .toMap()

        val cacheInfoMap = ObjectBox.cacheInfo.all.associateBy { it.vfDocId }

        val albumThumbMap = mutableMapOf<Pair<String, Long>, Long>()
        ObjectBox.albumInfo.all.forEach { ai ->
            ai.thumbCache.target?.let { tc ->
                albumThumbMap[ai.uuid to ai.serverId] = tc.id
            }
        }

        val mapped = ObjectBox.virtualFile.all.mapNotNull { vf ->
            val item = vf.item.target ?: return@mapNotNull null
            val ci = cacheInfoMap[vf.documentId]
            val fileCacheStatus = ci?.toCacheStatus(item.size) ?: CacheStatus.NotCached
            val lyricsStatus = if (lyricsMap[vf.documentId] == true) LyricsStatus.CACHED
            else LyricsStatus.NOT_CACHED

            CacheBrowserEntry(
                virtualFileId = vf.id,
                documentId = vf.documentId,
                itemId = vf.itemId.toString(),
                serverId = vf.serverId,
                title = item.title ?: item.name,
                artist = item.artist ?: "",
                album = item.album ?: "",
                albumId = vf.albumId,
                duration = item.duration,
                size = item.size,
                fileCacheStatus = fileCacheStatus,
                thumbStatus = resolveThumbStatus(vf, item, thumbMap, albumThumbMap),
                lyricsStatus = lyricsStatus,
                cacheInfoId = ci?.id,
                localPath = ci?.localPath,
            )
        }

        allArtists = mapped.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted()
        allAlbums = mapped.map { it.album to it.albumId }.filter { it.first.isNotBlank() }.distinct().sortedBy { it.first }

        entries = mapped
        totalCacheSize = mapped.sumOf { it.fileCacheStatus.cachedBytes() }
    }

    fun applyFilters() {
        var result = entries

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }

        jumpArtist?.let { artist ->
            result = result.filter { it.artist.equals(artist, ignoreCase = true) }
        }

        jumpAlbum?.let { album ->
            result = result.filter { it.album.equals(album, ignoreCase = true) }
        }

        result = when (filterMode) {
            BrowserFilter.ALL -> result
            BrowserFilter.CACHED -> result.filter { it.fileCacheStatus !is CacheStatus.NotCached }
            BrowserFilter.NOT_CACHED -> result.filter { it.fileCacheStatus is CacheStatus.NotCached }
        }

        filteredEntries = result
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        applyFilters()
    }

    fun updateFilterMode(mode: BrowserFilter) {
        filterMode = mode
        applyFilters()
    }

    fun updateJumpArtist(artist: String?) {
        jumpArtist = artist
        jumpAlbum = null
        applyFilters()
    }

    fun updateJumpAlbum(album: String?) {
        jumpAlbum = album
        jumpArtist = null
        applyFilters()
    }

    fun clearJumps() {
        jumpArtist = null
        jumpAlbum = null
        applyFilters()
    }

    fun startCache(entry: CacheBrowserEntry) {
        if (entry.virtualFileId in downloadingIds) return
        downloadingIds = downloadingIds + entry.virtualFileId

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val server = ObjectBox.server[entry.serverId] ?: return@withContext
                    val api = AppDependencies.apiFactory(server) as JellyfinAccessor
                    val cacheDir = File(getApplication<Application>().cacheDir, "cache_browser")
                    cacheDir.mkdirs()
                    val cacheFile = File(cacheDir, entry.documentId)
                    val docId = entry.documentId
                    val vf = ObjectBox.virtualFile[entry.virtualFileId]

                    val fileDl = async { downloadFile(api, docId, cacheFile) }
                    val thumbDl = async { downloadThumb(api, entry, vf) }
                    val lyricsDl = async { downloadLyrics(api, docId) }

                    val finalSize = fileDl.await()
                    thumbDl.await()
                    lyricsDl.await()

                    if (finalSize > 0) {
                        val ci = ObjectBox.cacheInfo.query {
                            equal(CacheInfo_.vfDocId, docId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        }.findFirst()

                        if (ci != null) {
                            ObjectBox.cacheInfo.put(ci.copy(
                                localLength = finalSize,
                                isCompleted = true,
                                localPath = cacheFile.absolutePath,
                                bitrate = vf?.item?.target?.bitrate ?: ci.bitrate,
                            ))
                        } else {
                            ObjectBox.cacheInfo.put(CacheInfo(
                                vfDocId = docId,
                                localPath = cacheFile.absolutePath,
                                localLength = finalSize,
                                bitrate = vf?.item?.target?.bitrate ?: -1,
                                isCompleted = true,
                                virtualFileId = entry.virtualFileId,
                            ))
                        }
                    }

                    logcat(LogPriority.INFO) { "Cached: ${entry.title} (${finalSize.readable})" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Cache failed for ${entry.title}: ${e.message}" }
            } finally {
                downloadingIds = downloadingIds - entry.virtualFileId
                refreshEntry(entry.virtualFileId)
            }
        }
    }

    private suspend fun downloadFile(api: JellyfinAccessor, docId: String, cacheFile: File): Long {
        return try {
            val streamFactory = api.getDownloadStreamFactory(docId)
            val stream = streamFactory(0, null)
            val contentLength = stream.length

            cacheFile.outputStream().use { out ->
                stream.inputStream.copyTo(out)
            }
            cacheFile.setLastModified(System.currentTimeMillis())

            if (contentLength > 0) contentLength else cacheFile.length()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "File download failed: ${e.message}" }
            0
        }
    }

    private suspend fun downloadThumb(api: JellyfinAccessor, entry: CacheBrowserEntry, vf: VirtualFile?): Boolean {
        return try {
            val item = vf?.item?.target

            if (entry.albumId != null) {
                downloadAlbumThumb(api, entry, item)
            } else {
                downloadFileThumb(api, entry.documentId, item)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Thumb download failed: ${e.message}" }
            false
        }
    }

    private suspend fun downloadAlbumThumb(api: JellyfinAccessor, entry: CacheBrowserEntry, item: ItemRecord?): Boolean {
        val albumId = entry.albumId ?: return false
        val data = api.downloadThumbnail(albumId)
        val albumInfo = ObjectBox.albumInfo.query {
            equal(AlbumInfo_.uuid, albumId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(AlbumInfo_.serverId, entry.serverId)
        }.findFirst()

        if (albumInfo == null) {
            logcat(LogPriority.WARN) { "No AlbumInfo found for $albumId" }
            return false
        }

        if (data == null) {
            val tc = ThumbCache(data = null, checkedServer = true)
            ObjectBox.thumbCache.put(tc)
            albumInfo.thumbCache.target = tc
            ObjectBox.albumInfo.put(albumInfo)
            return false
        } else {
            val tc = ThumbCache(data = data, checkedServer = true)
            ObjectBox.thumbCache.put(tc)
            albumInfo.thumbCache.target = tc
            ObjectBox.albumInfo.put(albumInfo)
            return true
        }
    }

    private suspend fun downloadFileThumb(api: JellyfinAccessor, docId: String, item: ItemRecord?): Boolean {
        val data = api.downloadThumbnail(docId)
        if (item == null) return false

        if (data == null) {
            val tc = ThumbCache(data = null, checkedServer = true)
            ObjectBox.thumbCache.put(tc)
            if (item.thumbCacheId == 0L) {
                val updated = item.copy(thumbCacheId = tc.id)
                updated.thumbCache.target = tc
                ObjectBox.itemRecord.put(updated)
            }
            return false
        } else {
            val tc = ThumbCache(data = data, checkedServer = true)
            ObjectBox.thumbCache.put(tc)
            val updated = item.copy(thumbCacheId = tc.id)
            updated.thumbCache.target = tc
            ObjectBox.itemRecord.put(updated)
            return true
        }
    }

    private suspend fun downloadLyrics(api: JellyfinAccessor, docId: String): Boolean {
        return try {
            val existing = ObjectBox.lyricsCache.query {
                equal(LyricsCache_.vfDocId, docId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            }.findFirst()

            if (existing != null && existing.lyrics != null) return true

            val lyrics = api.getLyrics(docId)
            if (lyrics != null) {
                if (existing != null) {
                    ObjectBox.lyricsCache.put(existing.copy(lyrics = lyrics))
                } else {
                    ObjectBox.lyricsCache.put(LyricsCache(vfDocId = docId, lyrics = lyrics))
                }
                true
            } else {
                if (existing == null) {
                    ObjectBox.lyricsCache.put(LyricsCache(vfDocId = docId, lyrics = null))
                }
                false
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Lyrics download failed: ${e.message}" }
            false
        }
    }

    private fun refreshEntry(virtualFileId: Long) {
        val idx = entries.indexOfFirst { it.virtualFileId == virtualFileId }
        if (idx < 0) return

        val old = entries[idx]
        val vf = ObjectBox.virtualFile[virtualFileId] ?: return
        val item = vf.item.target ?: return

        val ci = ObjectBox.cacheInfo.query {
            equal(CacheInfo_.vfDocId, old.documentId, QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.findFirst()

        val thumbData = ObjectBox.thumbCache.all
            .filter { it.data != null }
            .map { it.id to true }
            .toMap()

        val albumThumbMap = mutableMapOf<Pair<String, Long>, Long>()
        ObjectBox.albumInfo.all.forEach { ai ->
            ai.thumbCache.target?.let { tc ->
                albumThumbMap[ai.uuid to ai.serverId] = tc.id
            }
        }

        val lyricsEntry = ObjectBox.lyricsCache.query {
            equal(LyricsCache_.vfDocId, old.documentId, QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.findFirst()

        entries = entries.toMutableList().also { list ->
            list[idx] = old.copy(
                fileCacheStatus = ci?.toCacheStatus(item.size) ?: CacheStatus.NotCached,
                thumbStatus = resolveThumbStatus(vf, item, thumbData, albumThumbMap),
                lyricsStatus = if (lyricsEntry?.lyrics != null) LyricsStatus.CACHED
                else LyricsStatus.NOT_CACHED,
                localPath = ci?.localPath,
                cacheInfoId = ci?.id,
            )
        }
        totalCacheSize = entries.sumOf { it.fileCacheStatus.cachedBytes() }
        applyFilters()
    }

    private fun refreshStatusesOnIO(): List<CacheBrowserEntry>? {
        if (entries.isEmpty()) return null

        val cacheInfoMap = ObjectBox.cacheInfo.all.associateBy { it.vfDocId }
        val lyricsMap = ObjectBox.lyricsCache.all
            .mapNotNull { entry -> entry.lyrics?.let { entry.vfDocId to true } }
            .toMap()
        val thumbData = ObjectBox.thumbCache.all
            .filter { it.data != null }
            .map { it.id to true }
            .toMap()

        val albumThumbMap = mutableMapOf<Pair<String, Long>, Long>()
        ObjectBox.albumInfo.all.forEach { ai ->
            ai.thumbCache.target?.let { tc ->
                albumThumbMap[ai.uuid to ai.serverId] = tc.id
            }
        }

        return entries.map { entry ->
            val vf = ObjectBox.virtualFile[entry.virtualFileId]
            val item = vf?.item?.target
            if (vf == null || item == null) return@map entry

            val ci = cacheInfoMap[entry.documentId]

            entry.copy(
                fileCacheStatus = ci?.toCacheStatus(item.size) ?: CacheStatus.NotCached,
                thumbStatus = resolveThumbStatus(vf, item, thumbData, albumThumbMap),
                lyricsStatus = if (lyricsMap[entry.documentId] == true) LyricsStatus.CACHED
                else LyricsStatus.NOT_CACHED,
                cacheInfoId = ci?.id,
                localPath = ci?.localPath,
            )
        }
    }

    fun deleteCache(entry: CacheBrowserEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entry.cacheInfoId?.let { ObjectBox.cacheInfo.remove(it) }
                entry.localPath?.let { File(it).delete() }
            }
            refreshEntry(entry.virtualFileId)
        }
    }
}

private fun resolveThumbStatus(
    vf: VirtualFile,
    item: ItemRecord,
    thumbData: Map<Long, Boolean>,
    albumThumbMap: Map<Pair<String, Long>, Long>,
): ThumbStatus {
    return if (vf.albumId != null) {
        val albumTcId = albumThumbMap[vf.albumId to vf.serverId]
        when {
            albumTcId != null && thumbData[albumTcId] == true -> ThumbStatus.CACHED
            albumTcId != null -> ThumbStatus.NOT_CACHED
            else -> ThumbStatus.NONE
        }
    } else {
        when {
            item.thumbCacheId > 0 && thumbData[item.thumbCacheId] == true -> ThumbStatus.CACHED
            item.thumbCacheId > 0 -> ThumbStatus.NOT_CACHED
            else -> ThumbStatus.NONE
        }
    }
}
