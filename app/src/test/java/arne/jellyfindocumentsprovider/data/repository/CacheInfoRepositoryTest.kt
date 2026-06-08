package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.ItemRecord
import arne.jellyfindocumentsprovider.vfs.MyObjectBox
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class CacheInfoRepositoryTest {
    private lateinit var store: BoxStore
    private lateinit var repo: CacheInfoRepository

    @Before
    fun setUp() {
        store = MyObjectBox.builder().name("test-${UUID.randomUUID()}").build()
        repo = ObjectBoxCacheInfoRepository(store.boxFor(CacheInfo::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun createVf(docId: String = "doc-1"): VirtualFile {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val item = ItemRecord(
            documentId = docId, name = "test.mp3", mimeType = "audio/mpeg",
            displayName = "test", lastModified = 1000L, size = 5000L,
            duration = null, year = null, title = null, album = null,
            track = null, artist = null, bitrate = null,
            albumId = null, albumCoverTag = null,
        )
        itemBox.put(item)
        val vf = VirtualFile(
            documentId = docId, libId = "lib-1", serverId = 1L, albumId = null,
        )
        vf.item.target = item
        return vf
    }

    @Test
    fun getOrCreate_createsNew() {
        val vf = createVf("doc-1")
        val cacheInfo = repo.getOrCreate(vf, "/tmp/test.mp3")
        assertNotNull(cacheInfo)
        assertEquals("doc-1", cacheInfo.vfDocId)
        assertEquals("/tmp/test.mp3", cacheInfo.localPath)
    }

    @Test
    fun getOrCreate_returnsExisting() {
        val vf = createVf("doc-1")
        val first = repo.getOrCreate(vf, "/tmp/first.mp3")
        val second = repo.getOrCreate(vf, "/tmp/second.mp3")
        assertEquals(first.id, second.id)
        assertEquals("/tmp/first.mp3", second.localPath)
    }

    @Test
    fun put_persists() {
        val vf = createVf("doc-1")
        val ci = repo.getOrCreate(vf, "/tmp/test.mp3")
        val updated = ci.copy(localPath = "/tmp/updated.mp3")
        repo.put(updated)
        val retrieved = repo.getOrCreate(vf, "/tmp/ignore.mp3")
        assertEquals("/tmp/updated.mp3", retrieved.localPath)
    }
}
