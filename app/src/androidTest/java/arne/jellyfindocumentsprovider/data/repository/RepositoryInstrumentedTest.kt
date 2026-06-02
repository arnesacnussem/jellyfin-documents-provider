package arne.jellyfindocumentsprovider.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.MyObjectBox
import arne.jellyfindocumentsprovider.vfs.ThumbCache
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RepositoryInstrumentedTest {
    private lateinit var store: BoxStore
    private lateinit var serverRepo: ServerRepository
    private lateinit var vfRepo: VirtualFileRepository
    private lateinit var albumRepo: AlbumInfoRepository
    private lateinit var cacheRepo: CacheInfoRepository
    private lateinit var thumbRepo: ThumbCacheRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = MyObjectBox.builder()
            .androidContext(context)
            .name("repo-test-${java.util.UUID.randomUUID()}")
            .build()
        serverRepo = ObjectBoxServerRepository(store.boxFor(JellyfinServer::class.java))
        vfRepo = ObjectBoxVirtualFileRepository(store.boxFor(VirtualFile::class.java))
        albumRepo = ObjectBoxAlbumInfoRepository(store.boxFor(AlbumInfo::class.java))
        cacheRepo = ObjectBoxCacheInfoRepository(store.boxFor(CacheInfo::class.java))
        thumbRepo = ObjectBoxThumbCacheRepository(store.boxFor(ThumbCache::class.java))
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
    }

    // Server
    @Test
    fun serverRepo_putAndFind() {
        val server = JellyfinServer(
            uuid = "inst-1",
            url = "https://inst.local",
            serverName = "Inst Server",
            library = mutableMapOf("lib1" to "Library 1"),
            username = "user1",
            token = "token1"
        )
        serverRepo.put(server)
        assertNotNull(serverRepo.findByUUID("inst-1"))
        assertEquals(1L, serverRepo.count())
    }

    @Test
    fun serverRepo_findAll() {
        assertEquals(0, serverRepo.findAll().size)
    }

    // VirtualFile
    @Test
    fun vfRepo_putAndFindByDocumentId() {
        val vf = VirtualFile(
            name = "test.mp3", documentId = "inst-doc-1", mimeType = "audio/mpeg",
            displayName = "test", lastModified = 1000L, size = 5000L,
            libId = "lib-1", serverId = 1L,
            duration = null, year = null, title = null,
            album = null, track = null, artist = null, bitrate = null,
            albumId = null, albumCoverTag = null
        )
        vfRepo.put(vf)
        assertNotNull(vfRepo.findByDocumentId("inst-doc-1"))
        assertEquals(1L, vfRepo.count())
    }

    @Test
    fun vfRepo_countByServerId() {
        vfRepo.put(
            VirtualFile(name = "a", documentId = "d1", mimeType = "audio/mpeg", displayName = "a",
                lastModified = 1L, size = 1L, libId = "l1", serverId = 1L,
                duration = null, year = null, title = null,
                album = null, track = null, artist = null, bitrate = null,
                albumId = null, albumCoverTag = null),
            VirtualFile(name = "b", documentId = "d2", mimeType = "audio/mpeg", displayName = "b",
                lastModified = 1L, size = 1L, libId = "l1", serverId = 2L,
                duration = null, year = null, title = null,
                album = null, track = null, artist = null, bitrate = null,
                albumId = null, albumCoverTag = null)
        )
        assertEquals(1L, vfRepo.countByServerId(1L))
        assertEquals(1L, vfRepo.countByServerId(2L))
    }

    // AlbumInfo
    @Test
    fun albumRepo_putAndFind() {
        albumRepo.put(AlbumInfo(uuid = "inst-album-1", name = "Inst Album", libId = "l1"))
        assertEquals(1, albumRepo.findAlbumByUUID("inst-album-1").size)
    }

    @Test
    fun albumRepo_removeByLibId() {
        albumRepo.put(
            AlbumInfo(uuid = "a1", name = "A1", libId = "l1"),
            AlbumInfo(uuid = "a2", name = "A2", libId = "l2")
        )
        albumRepo.removeByLibId("l1")
        assertEquals(0, albumRepo.findAllByLibId("l1").size)
        assertEquals(1, albumRepo.findAllByLibId("l2").size)
    }

    // CacheInfo
    @Test
    fun cacheRepo_getOrCreate() {
        val vf = VirtualFile(
            name = "test.mp3", documentId = "inst-cache-1", mimeType = "audio/mpeg",
            displayName = "test", lastModified = 1L, size = 1L, libId = "l1", serverId = 1L,
            duration = null, year = null, title = null,
            album = null, track = null, artist = null, bitrate = null,
            albumId = null, albumCoverTag = null
        )
        val ci = cacheRepo.getOrCreate(vf, "/tmp/test.mp3")
        assertNotNull(ci)
        assertEquals("inst-cache-1", ci.vfDocId)
    }

    // ThumbCache
    @Test
    fun thumbRepo_put() {
        val thumb = ThumbCache(data = byteArrayOf(1, 2, 3), checkedServer = true)
        thumbRepo.put(thumb)
        assertNotEquals(0L, thumb.id)
    }
}
