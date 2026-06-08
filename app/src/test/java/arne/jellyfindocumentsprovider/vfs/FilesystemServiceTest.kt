package arne.jellyfindocumentsprovider.vfs

import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import arne.jellyfindocumentsprovider.data.AppRepos
import arne.jellyfindocumentsprovider.data.repository.AlbumInfoRepository
import arne.jellyfindocumentsprovider.data.repository.ItemRecordRepository
import arne.jellyfindocumentsprovider.data.repository.ServerRepository
import arne.jellyfindocumentsprovider.data.repository.VirtualFileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.objectbox.relation.ToOne
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FilesystemServiceTest {

    private val serverRepo = mockk<ServerRepository>(relaxed = true)
    private val vfRepo = mockk<VirtualFileRepository>(relaxed = true)
    private val albumInfoRepo = mockk<AlbumInfoRepository>(relaxed = true)
    private val repos = AppRepos(
        server = serverRepo,
        virtualFile = vfRepo,
        albumInfo = albumInfoRepo,
        cacheInfo = mockk(relaxed = true),
        thumbCache = mockk(relaxed = true),
        itemRecord = mockk<ItemRecordRepository>(relaxed = true),
    )
    private val apiFactory = mockk<(JellyfinServer) -> JellyfinApi>(relaxed = true)
    private lateinit var service: FilesystemService

    private val server = JellyfinServer(
        id = 1, uuid = "user-1", url = "https://srv", serverName = "MySrv",
        username = "alice", token = "tok",
        library = mapOf("lib-1" to "Music", "lib-2" to "Podcasts")
    )

    private fun mockVf(
        name: String, docId: String, mime: String, display: String,
        lastMod: Long = 0, size: Long = 0, libId: String = "lib-1",
        albumId: String? = null, serverId: Long = 1
    ): VirtualFile {
        val itemRec = ItemRecord(
            name = name, documentId = docId, mimeType = mime,
            displayName = display, lastModified = lastMod, size = size,
            albumId = albumId, duration = null, year = null, title = name,
            album = null, track = null, artist = null, bitrate = null,
            albumCoverTag = null,
        )
        val vf = VirtualFile(
            documentId = docId, libId = libId, serverId = serverId, albumId = albumId,
        )
        vf.item.target = itemRec
        return vf
    }

    @Before
    fun setUp() {
        service = FilesystemService(repos, apiFactory)
    }

    @Test
    fun resolveName_user_returnsUsernameAtServerName() {
        every { serverRepo.findByUUID("user-1") } returns server
        val result = service.resolveName(VPath.User("user-1"))
        assertEquals("alice@MySrv@MySrv", result)
    }

    @Test
    fun resolveName_library_returnsLibraryName() {
        every { serverRepo.findByLibraryId("lib-1") } returns server
        val result = service.resolveName(VPath.Library("user-1", "lib-1"))
        assertEquals("Music", result)
    }

    @Test
    fun resolveName_album_returnsAlbumName() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { albumInfoRepo.findAlbumByUUID("album-1", 1L) } returns listOf(
            AlbumInfo(uuid = "album-1", name = "Greatest Hits", libId = "lib-1", serverId = 1L)
        )
        val result = service.resolveName(VPath.Album("user-1", "lib-1", "album-1"))
        assertEquals("Greatest Hits", result)
    }

    @Test
    fun resolveName_file_returnsFileName() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns mockVf(
            name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song"
        )
        val result = service.resolveName(VPath.File("user-1", "lib-1", null, "doc-1"))
        assertEquals("song.mp3", result)
    }

    @Test
    fun resolveName_userNotFound_returnsNull() {
        every { serverRepo.findByUUID("unknown") } returns null
        assertNull(service.resolveName(VPath.User("unknown")))
    }

    @Test
    fun getRoots_emptyWhenNoServers() {
        every { serverRepo.findAll() } returns emptyList()
        assertTrue(service.getRoots().isEmpty())
    }

    @Test
    fun getRoots_returnsProjectionsForEachServer() {
        every { serverRepo.findAll() } returns listOf(server)
        val roots = service.getRoots()
        assertEquals(1, roots.size)
        val root = roots[0].toMap()
        val docId = root[Root.COLUMN_DOCUMENT_ID].toString()
        assertTrue(docId.contains("user-1"))
        assertEquals("MySrv", root[Root.COLUMN_TITLE])
    }

    @Test
    fun getRoots_multipleServers() {
        val server2 = JellyfinServer(
            uuid = "user-2", url = "https://srv2", serverName = "Srv2",
            username = "bob", token = "tok2", library = mapOf()
        )
        every { serverRepo.findAll() } returns listOf(server, server2)
        assertEquals(2, service.getRoots().size)
    }

    @Test
    fun getChildren_user_returnsLibraryProjections() {
        every { serverRepo.findByUUID("user-1") } returns server
        val children = service.getChildren(VPath.User("user-1"))
        assertEquals(2, children.size)
        val lib1Proj = children[0].toMap()
        assertTrue(lib1Proj[Document.COLUMN_DOCUMENT_ID].toString().contains("lib-1"))
        assertEquals("Music", lib1Proj[Document.COLUMN_DISPLAY_NAME])
    }

    @Test
    fun getChildren_userNotFound_returnsEmpty() {
        every { serverRepo.findByUUID("unknown") } returns null
        assertTrue(service.getChildren(VPath.User("unknown")).isEmpty())
    }

    @Test
    fun getChildren_library_returnsFilesAndAlbums() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { vfRepo.findAllByLibIdNotInAlbum("lib-1", 1L) } returns listOf(
            mockVf(name = "track1.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Track 1")
        )
        every { albumInfoRepo.findAllByLibId("lib-1", 1L) } returns listOf(
            AlbumInfo(uuid = "album-1", name = "Album One", libId = "lib-1", serverId = 1L)
        )

        val children = service.getChildren(VPath.Library("user-1", "lib-1"))
        assertEquals(2, children.size)
    }

    @Test
    fun getChildren_library_emptyWhenNoFilesOrAlbums() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { vfRepo.findAllByLibIdNotInAlbum("lib-1", 1L) } returns emptyList()
        every { albumInfoRepo.findAllByLibId("lib-1", 1L) } returns emptyList()
        assertTrue(service.getChildren(VPath.Library("user-1", "lib-1")).isEmpty())
    }

    @Test
    fun getChildren_album_returnsAlbumFiles() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { vfRepo.findAllByAlbumId("album-1", 1L) } returns listOf(
            mockVf(name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song", albumId = "album-1")
        )
        val children = service.getChildren(VPath.Album("user-1", "lib-1", "album-1"))
        assertEquals(1, children.size)
        assertEquals("song.mp3", children[0].toMap()[Document.COLUMN_DISPLAY_NAME])
    }

    @Test
    fun getChildren_album_emptyWhenNoFiles() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { vfRepo.findAllByAlbumId("empty-album", 1L) } returns emptyList()
        assertTrue(service.getChildren(VPath.Album("user-1", "lib-1", "empty-album")).isEmpty())
    }

    @Test
    fun getChildren_file_returnsEmpty() {
        val children = service.getChildren(VPath.File("user-1", "lib-1", null, "doc-1"))
        assertTrue(children.isEmpty())
    }

    @Test
    fun getOne_file_returnsDocumentProjection() {
        every { serverRepo.findByUUID("user-1") } returns server
        val vfile = mockVf(
            name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song",
            lastMod = 1000, size = 500
        )
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns vfile

        val result = service.getOne(VPath.File("user-1", "lib-1", null, "doc-1"))
        assertEquals(1, result.size)
        val proj = result[0].toMap()
        assertEquals("", proj[Document.COLUMN_DOCUMENT_ID])
        assertEquals("song.mp3", proj[Document.COLUMN_DISPLAY_NAME])
        assertEquals("audio/mpeg", proj[Document.COLUMN_MIME_TYPE])
    }

    @Test
    fun getOne_fileNotFound_returnsEmptyDirProjection() {
        every { vfRepo.findByDocumentId("unknown", any()) } returns null
        every { serverRepo.findByUUID("user-1") } returns null

        val result = service.getOne(VPath.File("user-1", "lib-1", null, "unknown"))
        assertEquals(1, result.size)
    }

    @Test
    fun getOne_library_returnsDirProjection() {
        every { serverRepo.findByLibraryId("lib-1") } returns server

        val result = service.getOne(VPath.Library("user-1", "lib-1"))
        assertEquals(1, result.size)
    }

    @Test
    fun getOne_album_returnsDirProjection() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { albumInfoRepo.findAlbumByUUID("album-1", 1L) } returns listOf(
            AlbumInfo(uuid = "album-1", name = "Hits", libId = "lib-1", serverId = 1L)
        )
        val result = service.getOne(VPath.Album("user-1", "lib-1", "album-1"))
        assertEquals(1, result.size)
    }

    @Test
    fun thumbnailFromCacheOrRemote_fileNotFound_returnsNull() {
        every { serverRepo.findByUUID("user-1") } returns null
        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "unknown"), null
        )
        assertNull(result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_noThumbCache_returnsNull() {
        every { serverRepo.findByUUID("user-1") } returns server
        val itemRec = mockk<ItemRecord>(relaxed = true) {
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns ThumbCache(data = null, checkedServer = true)
            }
        }
        val toOneItem = mockk<ToOne<ItemRecord>>(relaxed = true)
        every { toOneItem.target } returns itemRec
        val toOneServer = mockk<ToOne<JellyfinServer>>(relaxed = true)
        every { toOneServer.target } returns server
        val thumbFile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { serverId } returns 1L
            every { item } returns toOneItem
            every { server } returns toOneServer
        }
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns thumbFile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNull(result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_cacheHit_returnsData() {
        every { serverRepo.findByUUID("user-1") } returns server
        val thumbData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val itemRec = mockk<ItemRecord>(relaxed = true) {
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns ThumbCache(data = thumbData, checkedServer = true)
            }
        }
        val toOneItem = mockk<ToOne<ItemRecord>>(relaxed = true)
        every { toOneItem.target } returns itemRec
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { serverId } returns 1L
            every { item } returns toOneItem
        }
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns vfile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNotNull("should return cached data", result)
        assertArrayEquals(thumbData, result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_albumCacheHit_returnsData() {
        every { serverRepo.findByUUID("user-1") } returns server
        val thumbData = byteArrayOf(1, 2, 3, 4, 5)
        val albumInfo = mockk<AlbumInfo>(relaxed = true) {
            every { thumbCache } returns mockk {
                every { target } returns ThumbCache(data = thumbData, checkedServer = true)
            }
        }
        val itemRec = mockk<ItemRecord>(relaxed = true) {
            every { albumId } returns "album-1"
        }
        val toOneItem = mockk<ToOne<ItemRecord>>(relaxed = true)
        every { toOneItem.target } returns itemRec
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns "album-1"
            every { serverId } returns 1L
            every { item } returns toOneItem
        }
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns vfile
        every { albumInfoRepo.findAlbumByUUID("album-1", 1L) } returns listOf(albumInfo)

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNotNull("should return album's cached thumbnail", result)
        assertArrayEquals(thumbData, result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_downloadsWhenNoData() {
        every { serverRepo.findByUUID("user-1") } returns server
        val downloadedBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val api = mockk<JellyfinApi>(relaxed = true)
        coEvery { api.downloadThumbnail("doc-1", any(), any()) } returns downloadedBytes
        every { apiFactory(any()) } returns api

        val tc = ThumbCache(data = null, checkedServer = false)
        tc.persistCallback = { /* no-op */ }
        val itemRec = mockk<ItemRecord>(relaxed = true) {
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns tc
            }
        }
        val toOneItem = mockk<ToOne<ItemRecord>>(relaxed = true)
        every { toOneItem.target } returns itemRec
        val toOneServer = mockk<ToOne<JellyfinServer>>(relaxed = true)
        every { toOneServer.target } returns server
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { serverId } returns 1L
            every { item } returns toOneItem
            every { server } returns toOneServer
        }
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns vfile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNotNull("should return downloaded data", result)
        assertArrayEquals(downloadedBytes, result)
        assertTrue("cache should be populated with downloaded data", tc.data != null)
        assertArrayEquals(downloadedBytes, tc.data!!)
        assertTrue("checkedServer should be true after download", tc.checkedServer)
    }

    @Test
    fun thumbnailFromCacheOrRemote_downloadFailed_returnsNull() {
        every { serverRepo.findByUUID("user-1") } returns server
        val api = mockk<JellyfinApi>(relaxed = true)
        coEvery { api.downloadThumbnail(any(), any(), any()) } throws RuntimeException("network error")
        every { apiFactory(any()) } returns api

        val tc = ThumbCache(data = null, checkedServer = false)
        tc.persistCallback = { /* no-op */ }
        val itemRec = mockk<ItemRecord>(relaxed = true) {
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns tc
            }
        }
        val toOneItem = mockk<ToOne<ItemRecord>>(relaxed = true)
        every { toOneItem.target } returns itemRec
        val toOneServer = mockk<ToOne<JellyfinServer>>(relaxed = true)
        every { toOneServer.target } returns mockk(relaxed = true)
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { serverId } returns 1L
            every { item } returns toOneItem
            every { server } returns toOneServer
        }
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns vfile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNull("should return null when download fails", result)
        assertNull("cache data should NOT be set on failure", tc.data)
    }

    @Test
    fun thumbnailFromCacheOrRemote_notCached_returnsNullWhenNotExists() {
        every { serverRepo.findByUUID("user-1") } returns server
        val tc = ThumbCache(data = null, checkedServer = false)
        tc.persistCallback = { /* no-op */ }
        val itemRec = mockk<ItemRecord>(relaxed = true) {
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns tc
            }
        }
        val toOneItem = mockk<ToOne<ItemRecord>>(relaxed = true)
        every { toOneItem.target } returns itemRec
        val toOneServer = mockk<ToOne<JellyfinServer>>(relaxed = true)
        every { toOneServer.target } returns mockk(relaxed = true)
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { serverId } returns 1L
            every { item } returns toOneItem
            every { server } returns toOneServer
        }
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns vfile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNull(result)
    }

    @Test
    fun streamThumbnail_fileNotFound_returnsNull() {
        every { serverRepo.findByUUID("user-1") } returns null
        val result = service.streamThumbnail(
            VPath.File("user-1", "lib-1", null, "unknown"), null
        )
        assertNull(result)
    }

    @Test
    fun streamThumbnail_nonFile_returnsNull() {
        every { serverRepo.findByUUID("user-1") } returns mockk()
        val result = service.streamThumbnail(VPath.User("user-1"), null)
        assertNull(result)
    }

    @Test
    fun getAudioStreamFactory_fileNotFound_returnsNull() {
        every { serverRepo.findByUUID("user-1") } returns null
        val result = service.getAudioStreamFactory(
            VPath.File("user-1", "lib-1", null, "unknown"), 320
        )
        assertNull(result)
    }

    @Test
    fun getAudioStreamFactory_nonFile_returnsNull() {
        val result = service.getAudioStreamFactory(VPath.Library("user-1", "lib-1"), null)
        assertNull(result)
    }

    @Test
    fun resolveName_file_callsFindByDocumentId() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { vfRepo.findByDocumentId("doc-1", 1L) } returns null
        service.resolveName(VPath.File("user-1", "lib-1", null, "doc-1"))
        verify(exactly = 1) { vfRepo.findByDocumentId("doc-1", 1L) }
    }

    @Test
    fun getRoots_callsFindAll() {
        every { serverRepo.findAll() } returns emptyList()
        service.getRoots()
        verify(exactly = 1) { serverRepo.findAll() }
    }

    @Test
    fun getChildren_library_correctQueries() {
        every { serverRepo.findByUUID("user-1") } returns server
        every { vfRepo.findAllByLibIdNotInAlbum("lib-1", 1L) } returns emptyList()
        every { albumInfoRepo.findAllByLibId("lib-1", 1L) } returns emptyList()

        service.getChildren(VPath.Library("user-1", "lib-1"))

        verify(exactly = 1) { vfRepo.findAllByLibIdNotInAlbum("lib-1", 1L) }
        verify(exactly = 1) { albumInfoRepo.findAllByLibId("lib-1", 1L) }
    }
}
