package arne.jellyfindocumentsprovider.data

import arne.jellyfindocumentsprovider.data.repository.AlbumInfoRepository
import arne.jellyfindocumentsprovider.data.repository.CacheInfoRepository
import arne.jellyfindocumentsprovider.data.repository.ItemRecordRepository
import arne.jellyfindocumentsprovider.data.repository.ServerRepository
import arne.jellyfindocumentsprovider.data.repository.ThumbCacheRepository
import arne.jellyfindocumentsprovider.data.repository.VirtualFileRepository

data class AppRepos(
    val server: ServerRepository,
    val virtualFile: VirtualFileRepository,
    val albumInfo: AlbumInfoRepository,
    val cacheInfo: CacheInfoRepository,
    val thumbCache: ThumbCacheRepository,
    val itemRecord: ItemRecordRepository,
)
