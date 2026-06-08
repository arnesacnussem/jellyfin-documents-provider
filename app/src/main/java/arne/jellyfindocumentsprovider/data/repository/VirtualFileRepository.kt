package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.VirtualFile

interface VirtualFileRepository {
    fun findAll(): List<VirtualFile>
    fun findAllByLibId(libId: String, serverId: Long): List<VirtualFile>
    fun findAllByLibIdNotInAlbum(libId: String, serverId: Long): List<VirtualFile>
    fun findAllByAlbumId(albumId: String, serverId: Long): List<VirtualFile>
    fun findByDocumentId(documentId: String, serverId: Long): VirtualFile?
    fun countByServerId(serverId: Long): Long
    fun count(): Long
    fun put(vararg files: VirtualFile)
    fun removeByLibId(libId: String, serverId: Long)
}
