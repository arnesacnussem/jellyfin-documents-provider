package arne.jellyfindocumentsprovider.vfs

import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder

fun Box<VirtualFile>.findAllByLibId(libId: String, serverId: Long): List<VirtualFile> = query {
    equal(VirtualFile_.libId, libId, StringOrder.CASE_SENSITIVE)
        .equal(VirtualFile_.serverId, serverId)
}.find()

fun Box<VirtualFile>.findAllByLibIdNotInAlbum(libId: String, serverId: Long): List<VirtualFile> = query {
    equal(VirtualFile_.libId, libId, StringOrder.CASE_SENSITIVE)
        .equal(VirtualFile_.serverId, serverId)
        .isNull(VirtualFile_.albumId)
}.find()

fun Box<VirtualFile>.findAllByAlbumId(albumId: String, serverId: Long): List<VirtualFile> = query {
    equal(VirtualFile_.albumId, albumId, StringOrder.CASE_SENSITIVE)
        .equal(VirtualFile_.serverId, serverId)
}.find()

fun Box<AlbumInfo>.findAllAlbumByLibId(libId: String, serverId: Long): List<AlbumInfo> = query {
    equal(AlbumInfo_.libId, libId, StringOrder.CASE_SENSITIVE)
        .equal(AlbumInfo_.serverId, serverId)
}.find()

fun Box<AlbumInfo>.findAlbumByUUID(uuid: String, serverId: Long): List<AlbumInfo> = query {
    equal(AlbumInfo_.uuid, uuid, StringOrder.CASE_SENSITIVE)
        .equal(AlbumInfo_.serverId, serverId)
}.find()

fun Box<VirtualFile>.countByServer(server: Long) = query {
    equal(VirtualFile_.serverId, server)
}.count()

fun Box<JellyfinServer>.findByUUID(uuid: String) = query {
    equal(JellyfinServer_.uuid, uuid, StringOrder.CASE_SENSITIVE)
}.findFirst()

fun Box<JellyfinServer>.findByLibraryId(id: String) = all.find { it.library.containsKey(id) }

fun Box<VirtualFile>.findByDocumentId(documentId: String, serverId: Long) = query {
    equal(VirtualFile_.documentId, documentId, StringOrder.CASE_SENSITIVE)
        .equal(VirtualFile_.serverId, serverId)
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
