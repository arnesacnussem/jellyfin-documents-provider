package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.VirtualFile

interface CacheInfoRepository {
    fun getOrCreate(vf: VirtualFile, path: String): CacheInfo
    fun put(cacheInfo: CacheInfo)
}
