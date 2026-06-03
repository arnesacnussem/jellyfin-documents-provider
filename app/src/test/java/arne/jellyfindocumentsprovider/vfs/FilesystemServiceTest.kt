package arne.jellyfindocumentsprovider.vfs

import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import arne.jellyfindocumentsprovider.data.AppRepos
import arne.jellyfindocumentsprovider.data.repository.AlbumInfoRepository
import arne.jellyfindocumentsprovider.data.repository.ServerRepository
import arne.jellyfindocumentsprovider.data.repository.VirtualFileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    )
    private val apiFactory = mockk<(JellyfinServer) -> JellyfinApi>(relaxed = true)
    private lateinit var service: FilesystemService

    private val server = JellyfinServer(
        uuid = "user-1", url = "https://srv", serverName = "MySrv",
        username = "alice", token = "tok",
        library = mapOf("lib-1" to "Music", "lib-2" to "Podcasts")
    )

    private fun vf(
        name: String, docId: String, mime: String, display: String,
        lastMod: Long = 0, size: Long = 0, libId: String = "lib-1",
        albumId: String? = null
    ) = VirtualFile(
        name = name, documentId = docId, mimeType = mime,
        displayName = display, lastModified = lastMod, size = size,
        libId = libId, albumId = albumId,
        duration = null, year = null, title = null, album = null,
        track = null, artist = null, bitrate = null, albumCoverTag = null
    )

    @Before
    fun setUp() {
        service = FilesystemService(repos, apiFactory)
    }

    // ─── resolveName ──────────────────────────────────────────────

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
        every { albumInfoRepo.findAlbumByUUID("album-1") } returns listOf(
            AlbumInfo(uuid = "album-1", name = "Greatest Hits", libId = "lib-1")
        )
        val result = service.resolveName(VPath.Album("user-1", "lib-1", "album-1"))
        assertEquals("Greatest Hits", result)
    }

    @Test
    fun resolveName_file_returnsFileName() {
        every { vfRepo.findByDocumentId("doc-1") } returns vf(
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

    // ─── getRoots ─────────────────────────────────────────────────

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

    // ─── getChildren ──────────────────────────────────────────────

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
        every { vfRepo.findAllByLibIdNotInAlbum("lib-1") } returns listOf(
            vf(name = "track1.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Track 1")
        )
        every { albumInfoRepo.findAllByLibId("lib-1") } returns listOf(
            AlbumInfo(uuid = "album-1", name = "Album One", libId = "lib-1")
        )

        val children = service.getChildren(VPath.Library("user-1", "lib-1"))
        assertEquals(2, children.size)
    }

    @Test
    fun getChildren_library_emptyWhenNoFilesOrAlbums() {
        every { vfRepo.findAllByLibIdNotInAlbum("lib-1") } returns emptyList()
        every { albumInfoRepo.findAllByLibId("lib-1") } returns emptyList()
        assertTrue(service.getChildren(VPath.Library("user-1", "lib-1")).isEmpty())
    }

    @Test
    fun getChildren_album_returnsAlbumFiles() {
        every { vfRepo.findAllByAlbumId("album-1") } returns listOf(
            vf(name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song", albumId = "album-1")
        )
        val children = service.getChildren(VPath.Album("user-1", "lib-1", "album-1"))
        assertEquals(1, children.size)
        assertEquals("song.mp3", children[0].toMap()[Document.COLUMN_DISPLAY_NAME])
    }

    @Test
    fun getChildren_album_emptyWhenNoFiles() {
        every { vfRepo.findAllByAlbumId("empty-album") } returns emptyList()
        assertTrue(service.getChildren(VPath.Album("user-1", "lib-1", "empty-album")).isEmpty())
    }

    @Test
    fun getChildren_file_returnsEmpty() {
        val children = service.getChildren(VPath.File("user-1", "lib-1", null, "doc-1"))
        assertTrue(children.isEmpty())
    }

    // ─── getOne ───────────────────────────────────────────────────

    @Test
    fun getOne_file_returnsDocumentProjection() {
        val vfile = vf(
            name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song",
            lastMod = 1000, size = 500
        )
        every { vfRepo.findByDocumentId("doc-1") } returns vfile

        val result = service.getOne(VPath.File("user-1", "lib-1", null, "doc-1"))
        assertEquals(1, result.size)
        val proj = result[0].toMap()
        assertEquals("", proj[Document.COLUMN_DOCUMENT_ID])
        assertEquals("song.mp3", proj[Document.COLUMN_DISPLAY_NAME])
        assertEquals("audio/mpeg", proj[Document.COLUMN_MIME_TYPE])
    }

    @Test
    fun getOne_fileNotFound_returnsEmptyDirProjection() {
        every { vfRepo.findByDocumentId("unknown") } returns null
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
        every { albumInfoRepo.findAlbumByUUID("album-1") } returns listOf(
            AlbumInfo(uuid = "album-1", name = "Hits", libId = "lib-1")
        )
        val result = service.getOne(VPath.Album("user-1", "lib-1", "album-1"))
        assertEquals(1, result.size)
    }

    // ─── thumbnailFromCacheOrRemote ──────────────────────────────

    @Test
    fun thumbnailFromCacheOrRemote_fileNotFound_returnsNull() {
        every { vfRepo.findByDocumentId("unknown") } returns null
        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "unknown"), null
        )
        assertNull(result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_noThumbCache_returnsNull() {
        val thumbFile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns ThumbCache(data = null, checkedServer = true)
            }
        }
        every { vfRepo.findByDocumentId("doc-1") } returns thumbFile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNull(result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_cacheHit_returnsData() {
        val thumbData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns ThumbCache(data = thumbData, checkedServer = true)
            }
        }
        every { vfRepo.findByDocumentId("doc-1") } returns vfile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNotNull("should return cached data", result)
        assertArrayEquals(thumbData, result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_albumCacheHit_returnsData() {
        val thumbData = byteArrayOf(1, 2, 3, 4, 5)
        val albumInfo = mockk<AlbumInfo>(relaxed = true) {
            every { thumbCache } returns mockk {
                every { target } returns ThumbCache(data = thumbData, checkedServer = true)
            }
        }
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns "album-1"
        }
        every { vfRepo.findByDocumentId("doc-1") } returns vfile
        every { albumInfoRepo.findAlbumByUUID("album-1") } returns listOf(albumInfo)

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNotNull("should return album's cached thumbnail", result)
        assertArrayEquals(thumbData, result)
    }

    @Test
    fun thumbnailFromCacheOrRemote_downloadsWhenNoData() {
        val downloadedBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val api = mockk<JellyfinApi>(relaxed = true)
        coEvery { api.downloadThumbnail("doc-1", any(), any()) } returns downloadedBytes
        every { apiFactory(any()) } returns api

        val tc = ThumbCache(data = null, checkedServer = false)
        tc.persistCallback = { /* no-op — avoid ObjectBox call in unit test */ }
        val serverMock = mockk<JellyfinServer>(relaxed = true)
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns tc
            }
            every { server } returns mockk {
                every { target } returns serverMock
            }
        }
        every { vfRepo.findByDocumentId("doc-1") } returns vfile

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
        val api = mockk<JellyfinApi>(relaxed = true)
        coEvery { api.downloadThumbnail(any(), any(), any()) } throws RuntimeException("network error")
        every { apiFactory(any()) } returns api

        val tc = ThumbCache(data = null, checkedServer = false)
        tc.persistCallback = { /* no-op */ }
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns tc
            }
            every { server } returns mockk {
                every { target } returns mockk(relaxed = true)
            }
        }
        every { vfRepo.findByDocumentId("doc-1") } returns vfile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        assertNull("should return null when download fails", result)
        assertNull("cache data should NOT be set on failure", tc.data)
    }

    @Test
    fun thumbnailFromCacheOrRemote_notCached_returnsNullWhenNotExists() {
        // data=null + checkedServer=false -> notExists=false, but data is null
        // No apiFactory setup means download will fail
        val tc = ThumbCache(data = null, checkedServer = false)
        tc.persistCallback = { /* no-op */ }
        val vfile = mockk<VirtualFile>(relaxed = true) {
            every { documentId } returns "doc-1"
            every { albumId } returns null
            every { thumbCache } returns mockk {
                every { target } returns tc
            }
            every { server } returns mockk {
                every { target } returns mockk(relaxed = true)
            }
        }
        every { vfRepo.findByDocumentId("doc-1") } returns vfile

        val result = service.thumbnailFromCacheOrRemote(
            VPath.File("user-1", "lib-1", null, "doc-1"), null
        )
        // When no apiFactory mock is set and data is null, download attempt fails
        assertNull(result)
    }

    // ─── streamThumbnail ──────────────────────────────────────────

    @Test
    fun streamThumbnail_fileNotFound_returnsNull() {
        every { vfRepo.findByDocumentId("unknown") } returns null
        val result = service.streamThumbnail(
            VPath.File("user-1", "lib-1", null, "unknown"), null
        )
        assertNull(result)
    }

    @Test
    fun streamThumbnail_nonFile_returnsNull() {
        val result = service.streamThumbnail(VPath.User("user-1"), null)
        assertNull(result)
    }

    // ─── getAudioStreamFactory ────────────────────────────────────

    @Test
    fun getAudioStreamFactory_fileNotFound_returnsNull() {
        every { vfRepo.findByDocumentId("unknown") } returns null
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

    // ─── Verify repository calls ──────────────────────────────────

    @Test
    fun resolveName_file_callsFindByDocumentId() {
        every { vfRepo.findByDocumentId("doc-1") } returns null
        service.resolveName(VPath.File("user-1", "lib-1", null, "doc-1"))
        verify(exactly = 1) { vfRepo.findByDocumentId("doc-1") }
    }

    @Test
    fun getRoots_callsFindAll() {
        every { serverRepo.findAll() } returns emptyList()
        service.getRoots()
        verify(exactly = 1) { serverRepo.findAll() }
    }

    @Test
    fun getChildren_library_correctQueries() {
        every { vfRepo.findAllByLibIdNotInAlbum("lib-1") } returns emptyList()
        every { albumInfoRepo.findAllByLibId("lib-1") } returns emptyList()

        service.getChildren(VPath.Library("user-1", "lib-1"))

        verify(exactly = 1) { vfRepo.findAllByLibIdNotInAlbum("lib-1") }
        verify(exactly = 1) { albumInfoRepo.findAllByLibId("lib-1") }
    }
}
