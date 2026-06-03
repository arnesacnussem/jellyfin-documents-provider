package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.ThumbCache
import io.objectbox.Box

class ObjectBoxThumbCacheRepository(
    private val box: Box<ThumbCache>
) : ThumbCacheRepository {
    override fun put(thumbCache: ThumbCache) {
        box.put(thumbCache)
    }

    override fun count() = box.count()

    override fun countWithData() = box.all.count { it.data != null }.toLong()

    override fun deleteAll() {
        box.removeAll()
    }
}
