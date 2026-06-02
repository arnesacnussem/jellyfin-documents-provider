package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.VirtualFile
import arne.jellyfindocumentsprovider.vfs.VirtualFile_
import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder

class ObjectBoxVirtualFileRepository(
    private val box: Box<VirtualFile>
) : VirtualFileRepository {
    override fun findAll() = box.all

    override fun findAllByLibId(libId: String) = box.query {
        equal(VirtualFile_.libId, libId, QueryBuilder.StringOrder.CASE_SENSITIVE)
    }.find()

    override fun findAllByLibIdNotInAlbum(libId: String) = box.query {
        equal(VirtualFile_.libId, libId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .isNull(VirtualFile_.albumId)
    }.find()

    override fun findAllByAlbumId(albumId: String) = box.query {
        equal(VirtualFile_.albumId, albumId, QueryBuilder.StringOrder.CASE_SENSITIVE)
    }.find()

    override fun findByDocumentId(documentId: String) = box.query {
        equal(VirtualFile_.documentId, documentId, QueryBuilder.StringOrder.CASE_SENSITIVE)
    }.findFirst()

    override fun countByServerId(serverId: Long) = box.query {
        equal(VirtualFile_.serverId, serverId)
    }.count()

    override fun count() = box.count()

    override fun put(vararg files: VirtualFile) {
        box.put(files.toList())
    }

    override fun removeByLibId(libId: String) {
        box.query {
            equal(VirtualFile_.libId, libId, QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.remove()
    }
}
