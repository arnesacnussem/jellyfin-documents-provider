package arne.jellyfindocumentsprovider.vfs

import arne.jellyfindocumentsprovider.data.AppRepos
import arne.jellyfindocumentsprovider.vfs.VirtualFile.Companion.toVirtualFile
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.jellyfin.sdk.model.api.BaseItemDto
class DatabaseSyncTask(
    private val api: JellyfinApi,
    private val repos: AppRepos,
    private val credential: JellyfinServer,
) {
    suspend fun sync(
        batchSize: Int = 1000,
        onProgress: (text: String, current: Int, currentTotal: Int) -> Unit
    ) {
        logcat {
            "[${credential.name}] syncing database for ${credential.info}"
        }
        onProgress("[1/3 getting total items to sync...]", 0, -1)
        val libraryTotal = credential.library.keys.associateWith {
            api.queryAudioItems(it, limit = 0)?.totalRecordCount ?: 0
        }

        logcat {
            "[${credential.name}] total items to sync: ${libraryTotal.size}"
        }
        val total = libraryTotal.values.sum()
        var proceed = 0
        onProgress("[1/3 getting total items to sync...$total]", 0, total)

        libraryTotal.forEach { (libId, libTotal) ->
            logcat {
                "[${credential.name}] syncing library: $libId"
            }

            // cleanup
            repos.virtualFile.removeByLibId(libId)
            repos.albumInfo.removeByLibId(libId)

            // fetch
            runBlocking {
                fetchItemsInBatches(libId = libId,
                    batchSize = batchSize,
                    totalItems = libTotal,
                    onFetch = { items ->
                        repos.virtualFile.put(*items.map {
                            it.toVirtualFile(credential, libId)
                        }.toTypedArray())
                        proceed += items.size
                        onProgress(
                            "2/3 syncing library: $libId ...",
                            proceed,
                            total
                        )
                        logcat {
                            "[${credential.name}] syncing library: $libId ... $proceed/$total"
                        }
                    })
            }
        }

        logcat {
            "[${credential.name}] synced libraries, processing album info"
        }
        onProgress("3/3 processing album info...", 0, -1)

        val nameMap = mutableMapOf<String, String>()
        runBlocking {
            repos.virtualFile.findAll().groupBy { it.albumId }.forEach { (album, items) ->
                if (album != null) {
                    val name = items.firstOrNull { it.album != null }?.name
                        ?: api.getItemNameById(album)
                    if (name != null) {
                        nameMap[album] = name
                    }
                }
            }
        }

        repos.virtualFile.findAll().groupBy { it.albumId }.forEach { (album, items) ->
            if (album == null) {
                items.forEach {
                    val tc = ThumbCache()
                    repos.thumbCache.put(tc)
                    repos.virtualFile.put(it.apply {
                        it.thumbCache.target = tc
                    })
                }
                return@forEach
            }
            val name = nameMap[album] ?: return@forEach
            val libId = items.first().libId
            val tc = ThumbCache()
            repos.thumbCache.put(tc)
            repos.albumInfo.put(
                AlbumInfo(
                    uuid = album, name = name, libId = libId
                ).apply {
                    thumbCache.target = tc
                }
            )
        }

        logcat {
            "[${credential.name}] processed album info"
        }
    }

    private suspend fun fetchItemsInBatches(
        batchSize: Int,
        totalItems: Int,
        libId: String,
        onFetch: (List<BaseItemDto>) -> Unit,
    ) {
        val numberOfBatches = (totalItems + batchSize - 1) / batchSize

        for (batch in 0 until numberOfBatches) {
            val startIndex = batch * batchSize
            val items = api.queryAudioItems(libId, startIndex, batchSize)?.items

            if (items != null) {
                onFetch(items)
            } else {
                break
            }
        }
    }
}
