package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.AlbumInfo_
import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder

class ObjectBoxAlbumInfoRepository(
    private val box: Box<AlbumInfo>
) : AlbumInfoRepository {
    override fun findAllByLibId(libId: String, serverId: Long) = box.query {
        equal(AlbumInfo_.libId, libId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(AlbumInfo_.serverId, serverId)
    }.find()

    override fun findAlbumByUUID(uuid: String, serverId: Long) = box.query {
        equal(AlbumInfo_.uuid, uuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(AlbumInfo_.serverId, serverId)
    }.find()

    override fun put(vararg albums: AlbumInfo) {
        box.put(albums.toList())
    }

    override fun removeByLibId(libId: String, serverId: Long) {
        box.query {
            equal(AlbumInfo_.libId, libId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(AlbumInfo_.serverId, serverId)
        }.remove()
    }
}
