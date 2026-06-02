package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.ThumbCache
import io.objectbox.Box

class ObjectBoxThumbCacheRepository(
    private val box: Box<ThumbCache>
) : ThumbCacheRepository {
    override fun put(thumbCache: ThumbCache) {
        box.put(thumbCache)
    }
}
