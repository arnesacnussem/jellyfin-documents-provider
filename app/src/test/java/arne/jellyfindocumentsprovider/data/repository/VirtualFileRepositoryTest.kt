package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.ItemRecord
import arne.jellyfindocumentsprovider.vfs.MyObjectBox
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class VirtualFileRepositoryTest {
    private lateinit var store: BoxStore
    private lateinit var repo: VirtualFileRepository

    @Before
    fun setUp() {
        store = MyObjectBox.builder().name("test-${UUID.randomUUID()}").build()
        repo = ObjectBoxVirtualFileRepository(store.boxFor(VirtualFile::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun createVf(
        documentId: String = "doc-1",
        libId: String = "lib-1",
        serverId: Long = 1L,
        albumId: String? = null
    ): VirtualFile {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val item = ItemRecord(
            documentId = documentId,
            name = "test.mp3", mimeType = "audio/mpeg", displayName = "test.mp3",
            lastModified = 1000L, size = 5000L,
            duration = null, year = null, title = "test.mp3",
            album = null, track = null, artist = null,
            bitrate = null, albumId = albumId, albumCoverTag = null,
        )
        itemBox.put(item)
        val vf = VirtualFile(
            documentId = documentId, libId = libId, serverId = serverId, albumId = albumId,
        )
        vf.item.target = item
        repo.put(vf)
        return vf
    }

    @Test
    fun findAllInitiallyEmpty() {
        assertEquals(0, repo.findAll().size)
    }

    @Test
    fun putAndFindAll() {
        createVf("doc-1")
        createVf("doc-2")
        assertEquals(2, repo.findAll().size)
    }

    @Test
    fun findByDocumentId_returnsCorrect() {
        createVf("doc-1", serverId = 1L)
        createVf("doc-2", serverId = 1L)
        val found = repo.findByDocumentId("doc-1", 1L)
        assertNotNull(found)
        assertEquals("test.mp3", found!!.item.target.name)
    }

    @Test
    fun findByDocumentId_notFound() {
        assertNull(repo.findByDocumentId("nonexistent", 1L))
    }

    @Test
    fun findAllByLibId_returnsCorrect() {
        createVf("doc-1", libId = "lib-1", serverId = 1L)
        createVf("doc-2", libId = "lib-1", serverId = 1L)
        createVf("doc-3", libId = "lib-2", serverId = 2L)
        assertEquals(2, repo.findAllByLibId("lib-1", 1L).size)
        assertEquals(1, repo.findAllByLibId("lib-2", 2L).size)
        assertEquals(0, repo.findAllByLibId("lib-3", 1L).size)
    }

    @Test
    fun findAllByLibIdNotInAlbum_filtersAlbumNull() {
        createVf("doc-1", libId = "lib-1", serverId = 1L, albumId = null)
        createVf("doc-2", libId = "lib-1", serverId = 1L, albumId = "album-1")
        createVf("doc-3", libId = "lib-1", serverId = 1L, albumId = null)
        val result = repo.findAllByLibIdNotInAlbum("lib-1", 1L)
        assertEquals(2, result.size)
        assertTrue(result.all { it.albumId == null })
    }

    @Test
    fun findAllByAlbumId_returnsCorrect() {
        createVf("doc-1", albumId = "album-1", serverId = 1L)
        createVf("doc-2", albumId = null, serverId = 1L)
        createVf("doc-3", albumId = "album-1", serverId = 1L)
        assertEquals(2, repo.findAllByAlbumId("album-1", 1L).size)
        assertEquals(0, repo.findAllByAlbumId("nonexistent", 1L).size)
    }

    @Test
    fun countByServerId_returnsCorrect() {
        createVf("doc-1", serverId = 1L)
        createVf("doc-2", serverId = 1L)
        createVf("doc-3", serverId = 2L)
        assertEquals(2, repo.countByServerId(1L))
        assertEquals(1, repo.countByServerId(2L))
        assertEquals(0, repo.countByServerId(3L))
    }

    @Test
    fun count_returnsCorrect() {
        assertEquals(0, repo.count())
        createVf("doc-1")
        createVf("doc-2")
        assertEquals(2, repo.count())
    }

    @Test
    fun removeByLibId_removesCorrect() {
        createVf("doc-1", libId = "lib-1", serverId = 1L)
        createVf("doc-2", libId = "lib-2", serverId = 1L)
        assertEquals(2, repo.count())
        repo.removeByLibId("lib-1", 1L)
        assertEquals(1, repo.count())
        assertEquals("doc-2", repo.findAll().first().documentId)
    }
}
