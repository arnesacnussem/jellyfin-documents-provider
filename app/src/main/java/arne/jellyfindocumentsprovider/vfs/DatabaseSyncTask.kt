package arne.jellyfindocumentsprovider.vfs

import arne.jellyfindocumentsprovider.vfs.VirtualFile.Companion.toVirtualFile
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.serializer.toUUID

class DatabaseSyncTask(
    private val accessor: JellyfinAccessor,
) {
    suspend fun sync(
        batchSize: Int = 1000,
        onProgress: (text: String, current: Int, currentTotal: Int) -> Unit
    ) {
        logcat {
            "[${accessor.credential.name}] syncing database for ${accessor.credential.info}"
        }
        onProgress("[1/3 getting total items to sync...]", 0, -1)
        val libraryTotal = accessor.credential.library.keys.associateWith {
            accessor.queryAudioItems(it, limit = 0)?.totalRecordCount ?: 0
        }

        logcat {
            "[${accessor.credential.name}] total items to sync: ${libraryTotal.size}"
        }
        val total = libraryTotal.values.sum()
        var proceed = 0
        onProgress("[1/3 getting total items to sync...$total]", 0, total)
        with(ObjectBox) {
            libraryTotal.forEach { (libId, libTotal) ->
                logcat {
                    "[${accessor.credential.name}] syncing library: $libId"
                }

                // cleanup
                store.runInTx {
                    virtualFile.query {
                        equal(VirtualFile_.libId, libId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    }.remove()
                    albumInfo.query {
                        equal(AlbumInfo_.libId, libId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    }.remove()
                }

                // fetch
                runBlocking {
                    fetchItemsInBatches(libId = libId,
                        batchSize = batchSize,
                        totalItems = libTotal,
                        onFetch = { items ->
                            store.runInTx {
                                virtualFile.put(items.map {
                                    it.toVirtualFile(
                                        accessor.credential,
                                        libId
                                    )
                                })
                            }
                            proceed += items.size
                            onProgress(
                                "2/3 syncing library: $libId ...",
                                proceed,
                                total
                            )
                            logcat {
                                "[${accessor.credential.name}] syncing library: $libId ... $proceed/$total"
                            }
                        })
                }

            }

            logcat {
                "[${accessor.credential.name}] synced libraries, processing album info"
            }
            onProgress("3/3 processing album info...", 0, -1)

            val nameMap = mutableMapOf<String, String>()
            runBlocking {
                virtualFile.all.groupBy { it.albumId }.forEach { (album, items) ->
                    if (album != null) {
                        val name = items.firstOrNull { it.album != null }?.name
                            ?: accessor.getItemNameByUUID(album.toUUID())
                        if (name != null) {
                            nameMap[album] = name
                        }
                    }
                }
            }

            store.runInTx {
                virtualFile.all.groupBy { it.albumId }.forEach { (album, items) ->
                    if (album == null) {
                        items.forEach {
                            virtualFile.put(it.apply {
                                it.thumbCache.target = ThumbCache()
                            })
                        }
                        return@forEach
                    }
                    val name = nameMap[album] ?: return@forEach
                    val libId = items.first().libId
                    albumInfo.put(
                        AlbumInfo(
                            uuid = album, name = name, libId = libId
                        ).apply {
                            thumbCache.target = ThumbCache()
                        }
                    )
                }
            }

            logcat {
                "[${accessor.credential.name}] processed album info"
            }
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
            val items = accessor.queryAudioItems(libId, startIndex, batchSize)?.items

            if (items != null) {
                onFetch(items)
            } else {
                break
            }
        }
    }
}