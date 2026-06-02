package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.VirtualFile

interface VirtualFileRepository {
    fun findAll(): List<VirtualFile>
    fun findAllByLibId(libId: String): List<VirtualFile>
    fun findAllByLibIdNotInAlbum(libId: String): List<VirtualFile>
    fun findAllByAlbumId(albumId: String): List<VirtualFile>
    fun findByDocumentId(documentId: String): VirtualFile?
    fun countByServerId(serverId: Long): Long
    fun count(): Long
    fun put(vararg files: VirtualFile)
    fun removeByLibId(libId: String)
}
