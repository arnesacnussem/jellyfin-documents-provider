package arne.jellyfindocumentsprovider.vfs

import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder

fun Box<VirtualFile>.findAllByLibId(libId: String): List<VirtualFile> = query {
    equal(VirtualFile_.libId, libId, StringOrder.CASE_SENSITIVE)
}.find()

fun Box<VirtualFile>.findAllByLibIdNotInAlbum(libId: String): List<VirtualFile> = query {
    equal(VirtualFile_.libId, libId, StringOrder.CASE_SENSITIVE).isNull(VirtualFile_.albumId)
}.find()

fun Box<VirtualFile>.findAllByAlbumId(albumId: String): List<VirtualFile> = query {
    equal(VirtualFile_.albumId, albumId, StringOrder.CASE_SENSITIVE)
}.find()

fun Box<AlbumInfo>.findAllAlbumByLibId(libId: String): List<AlbumInfo> = query {
    equal(AlbumInfo_.libId, libId, StringOrder.CASE_SENSITIVE)
}.find()

fun Box<AlbumInfo>.findAlbumByUUID(uuid: String): List<AlbumInfo> = query {
    equal(AlbumInfo_.uuid, uuid, StringOrder.CASE_SENSITIVE)
}.find()

fun Box<VirtualFile>.countByServer(server: Long) = query {
    equal(VirtualFile_.serverId, server)
}.count()

fun Box<JellyfinServer>.findByUUID(uuid: String) = query {
    equal(JellyfinServer_.uuid, uuid, StringOrder.CASE_SENSITIVE)
}.findFirst()

fun Box<JellyfinServer>.findByLibraryId(id: String) = all.find { it.library.containsKey(id) }

fun Box<VirtualFile>.findByDocumentId(documentId: String) = query {
    equal(VirtualFile_.documentId, documentId, StringOrder.CASE_SENSITIVE)
}.findFirst()

fun Box<LyricsCache>.findByVfDocId(vfDocId: String): LyricsCache? = query {
    equal(LyricsCache_.vfDocId, vfDocId, StringOrder.CASE_SENSITIVE)
}.findFirst()

fun Box<CacheInfo>.getOrCreate(vf: VirtualFile, path: String): CacheInfo {
    return query {
        equal(CacheInfo_.vfDocId, vf.documentId, StringOrder.CASE_SENSITIVE)
    }.findFirst() ?: CacheInfo(
        virtualFileId = vf.id, vfDocId = vf.documentId, localPath = path
    ).apply {
        put(this)
    }
}