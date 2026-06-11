package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.CacheInfo_
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder

class ObjectBoxCacheInfoRepository(
    private val box: Box<CacheInfo>
) : CacheInfoRepository {
    override fun getOrCreate(vf: VirtualFile, path: String): CacheInfo {
        return box.query {
            equal(CacheInfo_.vfDocId, vf.documentId, QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.findFirst() ?: CacheInfo(
            virtualFileId = vf.id, vfDocId = vf.documentId, localPath = path
        ).apply { box.put(this) }
    }

    override fun findByVfDocId(vfDocId: String): CacheInfo? = box.query {
        equal(CacheInfo_.vfDocId, vfDocId, QueryBuilder.StringOrder.CASE_SENSITIVE)
    }.findFirst()

    override fun put(cacheInfo: CacheInfo) {
        box.put(cacheInfo)
    }

    override fun findAll() = box.all

    override fun delete(id: Long) {
        box.remove(id)
    }

    override fun deleteAll() {
        box.removeAll()
    }
}
