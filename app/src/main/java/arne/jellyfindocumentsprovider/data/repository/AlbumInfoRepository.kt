package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.AlbumInfo

interface AlbumInfoRepository {
    fun findAllByLibId(libId: String, serverId: Long): List<AlbumInfo>
    fun findAlbumByUUID(uuid: String, serverId: Long): List<AlbumInfo>
    fun put(vararg albums: AlbumInfo)
    fun removeByLibId(libId: String, serverId: Long)
}
