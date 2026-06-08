package arne.jellyfindocumentsprovider.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.ItemRecord
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

    private fun createVf(docId: String, serverId: Long = 1L): VirtualFile {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val item = ItemRecord(
            documentId = docId, name = "test.mp3", mimeType = "audio/mpeg",
            displayName = "test", lastModified = 1000L, size = 5000L,
            duration = null, year = null, title = null,
            album = null, track = null, artist = null, bitrate = null,
            albumId = null, albumCoverTag = null,
        )
        itemBox.put(item)
        val vf = VirtualFile(
            documentId = docId, libId = "lib-1", serverId = serverId, albumId = null,
        )
        vf.item.target = item
        return vf
    }

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

    @Test
    fun vfRepo_putAndFindByDocumentId() {
        val vf = createVf("inst-doc-1")
        vfRepo.put(vf)
        assertNotNull(vfRepo.findByDocumentId("inst-doc-1", 1L))
        assertEquals(1L, vfRepo.count())
    }

    @Test
    fun vfRepo_countByServerId() {
        vfRepo.put(createVf("d1", 1L), createVf("d2", 2L))
        assertEquals(1L, vfRepo.countByServerId(1L))
        assertEquals(1L, vfRepo.countByServerId(2L))
    }

    @Test
    fun albumRepo_putAndFind() {
        albumRepo.put(AlbumInfo(uuid = "inst-album-1", name = "Inst Album", libId = "l1", serverId = 1L))
        assertEquals(1, albumRepo.findAlbumByUUID("inst-album-1", 1L).size)
    }

    @Test
    fun albumRepo_removeByLibId() {
        albumRepo.put(
            AlbumInfo(uuid = "a1", name = "A1", libId = "l1", serverId = 1L),
            AlbumInfo(uuid = "a2", name = "A2", libId = "l2", serverId = 1L)
        )
        albumRepo.removeByLibId("l1", 1L)
        assertEquals(0, albumRepo.findAllByLibId("l1", 1L).size)
        assertEquals(1, albumRepo.findAllByLibId("l2", 1L).size)
    }

    @Test
    fun cacheRepo_getOrCreate() {
        val vf = createVf("inst-cache-1")
        val ci = cacheRepo.getOrCreate(vf, "/tmp/test.mp3")
        assertNotNull(ci)
        assertEquals("inst-cache-1", ci.vfDocId)
    }

    @Test
    fun thumbRepo_put() {
        val thumb = ThumbCache(data = byteArrayOf(1, 2, 3), checkedServer = true)
    }
}
