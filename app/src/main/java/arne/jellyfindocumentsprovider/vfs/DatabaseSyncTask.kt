package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import android.preference.PreferenceManager
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.common.SyncLikeToggle
import arne.jellyfindocumentsprovider.common.getEnum
import arne.jellyfindocumentsprovider.data.AppRepos
import arne.jellyfindocumentsprovider.vfs.VirtualFile.Companion.toVirtualFile
import logcat.LogPriority
import logcat.logcat
import org.jellyfin.sdk.model.api.BaseItemDto
class DatabaseSyncTask(
    private val api: JellyfinApi,
    private val repos: AppRepos,
    private val credential: JellyfinServer,
    private val context: Context,
) {
    suspend fun sync(
        batchSize: Int = 1000,
        onProgress: (text: String, current: Int, currentTotal: Int) -> Unit
    ) {
        val syncId = credential.uuid
        StatusEventManager.startSync(syncId, "[${credential.name}] starting sync...")

        logcat("DatabaseSyncTask", LogPriority.INFO) {
            "[${credential.name}] syncing database for ${credential.info}"
        }

        onProgress("[1/3 getting total items to sync...]", 0, -1)
        val libraryTotal = credential.library.keys.associateWith {
            api.queryAudioItems(it, limit = 0)?.totalRecordCount ?: 0
        }

        logcat("DatabaseSyncTask", LogPriority.INFO) {
            "[${credential.name}] total items to sync: ${libraryTotal.values.sum()}"
        }

        val total = libraryTotal.values.sum()
        var proceed = 0
        onProgress("[1/3 getting total items to sync...$total]", 0, total)

        // Preserve existing ThumbCaches before cleanup so we can reuse them
        val existingThumbCaches = collectExistingThumbCaches()

        libraryTotal.forEach { (libId, libTotal) ->
            logcat("DatabaseSyncTask", LogPriority.INFO) {
                "[${credential.name}] syncing library: $libId ($libTotal items)"
            }

            val fetchedItems = mutableListOf<BaseItemDto>()
            val fetchedAll = fetchItemsInBatches(libId = libId,
                batchSize = batchSize,
                totalItems = libTotal,
                onFetch = { items ->
                    fetchedItems += items
                    proceed += items.size
                    onProgress(
                        "2/3 syncing library: $libId ...",
                        proceed,
                        total
                    )
                    val pct = if (total > 0) proceed.toFloat() / total else 0f
                    StatusEventManager.updateSync(syncId, "[${credential.name}] syncing $proceed/$total", pct)
                    logcat {
                        "[${credential.name}] syncing library: $libId ... $proceed/$total"
                    }
                })

            if (!fetchedAll) {
                logcat("DatabaseSyncTask", LogPriority.ERROR) {
                    "[${credential.name}] failed to fetch all items for library $libId; keeping existing data"
                }
                return@forEach
            }

            repos.virtualFile.removeByLibId(libId)
            repos.albumInfo.removeByLibId(libId)
            repos.virtualFile.put(*fetchedItems.map {
                it.toVirtualFile(credential, libId)
            }.toTypedArray())
        }

        logcat("DatabaseSyncTask", LogPriority.INFO) {
            "[${credential.name}] processing album info..."
        }
        StatusEventManager.updateSync(syncId, "[${credential.name}] processing album info...", 1f)

        onProgress("3/3 processing album info...", 0, -1)

        // Ensure ThumbCaches exist for all newly synced items, reusing cached thumbnail data
        ensureThumbCaches(existingThumbCaches)

        logcat("DatabaseSyncTask", LogPriority.INFO) {
            "[${credential.name}] sync complete"
        }
        StatusEventManager.finishSync(syncId)

        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getEnum<SyncLikeToggle>(PrefKeys.SYNC_LIKES_TO_POWERAMP) == SyncLikeToggle.ENABLED) {
                PowerampRatingSync.pushAllLikesToPoweramp(context)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to push likes to Poweramp: ${e.message}" }
        }
    }

    suspend fun syncFavorites(
        batchSize: Int = 1000,
        onProgress: (text: String, current: Int, currentTotal: Int) -> Unit
    ) {
        val syncId = credential.uuid
        StatusEventManager.startSync(syncId, "[${credential.name}] syncing favorites...")

        logcat("DatabaseSyncTask", LogPriority.INFO) {
            "[${credential.name}] syncing favorites for ${credential.info}"
        }

        onProgress("getting favorited items...", 0, -1)
        val libraryTotal = credential.library.keys.associateWith {
            api.queryFavoriteAudioItems(it, limit = 0)?.totalRecordCount ?: 0
        }

        val total = libraryTotal.values.sum()
        logcat("DatabaseSyncTask", LogPriority.INFO) {
            "[${credential.name}] total favorited items: $total"
        }
        onProgress("found $total favorited items", 0, total)

        if (total == 0) {
            StatusEventManager.finishSync(syncId)
            return
        }

        val favoriteIds = mutableSetOf<String>()
        var proceed = 0

        libraryTotal.forEach { (libId, libTotal) ->
            val fetchedItems = fetchFavoriteItemsInBatches(libId = libId,
                batchSize = batchSize,
                totalItems = libTotal,
                onFetch = { items ->
                    items.forEach { item ->
                        item.id?.asString()?.let { favoriteIds.add(it) }
                    }
                    proceed += items.size
                    onProgress("syncing favorites: $libId ...", proceed, total)
                    StatusEventManager.updateSync(syncId, "[${credential.name}] favorites $proceed/$total", proceed.toFloat() / total)
                })

            if (!fetchedItems) {
                logcat("DatabaseSyncTask", LogPriority.ERROR) {
                    "[${credential.name}] failed to fetch favorites for library $libId"
                }
            }
        }

        val allVf = repos.virtualFile.findAll()
        var updated = 0
        for (vf in allVf) {
            val shouldBeFavorite = favoriteIds.contains(vf.documentId)
            if (vf.isFavorite != shouldBeFavorite) {
                repos.virtualFile.put(vf.copy(isFavorite = shouldBeFavorite))
                updated++
            }
        }

        logcat("DatabaseSyncTask", LogPriority.INFO) {
            "[${credential.name}] favorites sync complete: updated $updated items"
        }
        StatusEventManager.finishSync(syncId)

        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getEnum<SyncLikeToggle>(PrefKeys.SYNC_LIKES_TO_POWERAMP) == SyncLikeToggle.ENABLED) {
                PowerampRatingSync.pushAllLikesToPoweramp(context)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to push likes to Poweramp: ${e.message}" }
        }
    }

    /**
     * Collect all existing ThumbCache objects keyed by their UUID
     * (item documentId for non-album items, album UUID for album items).
     */
    private fun collectExistingThumbCaches(): Map<String, ThumbCache> {
        val caches = mutableMapOf<String, ThumbCache>()
        repos.virtualFile.findAll().forEach { vf ->
            val uuid = vf.albumId ?: vf.documentId
            val tc = if (vf.albumId != null) {
                repos.albumInfo.findAlbumByUUID(vf.albumId).firstOrNull()?.thumbCache?.target
            } else {
                vf.thumbCache.target
            }
            if (tc != null) {
                caches[uuid] = tc
            }
        }
        return caches
    }

    /**
     * Create ThumbCache entries for all VirtualFiles that don't have one yet,
     * reusing any existing ThumbCaches found before the sync.
     */
    private suspend fun ensureThumbCaches(existingThumbCaches: Map<String, ThumbCache>) {
        val nameMap = mutableMapOf<String, String>()
        repos.virtualFile.findAll().groupBy { it.albumId }.forEach { (album, items) ->
            if (album != null) {
                val name = try {
                    items.firstOrNull { it.album != null }?.name
                        ?: api.getItemNameById(album)
                } catch (e: Exception) {
                    logcat(LogPriority.DEBUG) { "ensureThumbCaches: failed to resolve album name for $album: ${e.message}" }
                    null
                }
                if (name != null) {
                    nameMap[album] = name
                }
            }
        }

        repos.virtualFile.findAll().groupBy { it.albumId }.forEach { (album, items) ->
            if (album == null) {
                items.forEach { vf ->
                    val existing = existingThumbCaches[vf.documentId]
                    val tc = existing ?: ThumbCache()
                    if (existing == null) repos.thumbCache.put(tc)
                    repos.virtualFile.put(vf.apply { vf.thumbCache.target = tc })
                }
                return@forEach
            }
            val name = nameMap[album] ?: return@forEach
            val libId = items.first().libId
            val existing = existingThumbCaches[album]
            val tc = existing ?: ThumbCache()
            if (existing == null) repos.thumbCache.put(tc)
            repos.albumInfo.put(
                AlbumInfo(
                    uuid = album, name = name, libId = libId
                ).apply {
                    thumbCache.target = tc
                }
            )
        }
    }

    private suspend fun fetchItemsInBatches(
        batchSize: Int,
        totalItems: Int,
        libId: String,
        onFetch: (List<BaseItemDto>) -> Unit,
    ): Boolean {
        val numberOfBatches = (totalItems + batchSize - 1) / batchSize

        for (batch in 0 until numberOfBatches) {
            val startIndex = batch * batchSize
            val items = api.queryAudioItems(libId, startIndex, batchSize)?.items

            if (items != null) {
                onFetch(items)
            } else {
                return false
            }
        }
        return true
    }

    private suspend fun fetchFavoriteItemsInBatches(
        batchSize: Int,
        totalItems: Int,
        libId: String,
        onFetch: (List<BaseItemDto>) -> Unit,
    ): Boolean {
        val numberOfBatches = (totalItems + batchSize - 1) / batchSize

        for (batch in 0 until numberOfBatches) {
            val startIndex = batch * batchSize
            val items = api.queryFavoriteAudioItems(libId, startIndex, batchSize)?.items

            if (items != null) {
                onFetch(items)
            } else {
                return false
            }
        }
        return true
    }
}
