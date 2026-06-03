package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.ThumbCache

interface ThumbCacheRepository {
    fun put(thumbCache: ThumbCache)
}
