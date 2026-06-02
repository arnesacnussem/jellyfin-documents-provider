package arne.jellyfindocumentsprovider.data.repository

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
        name: String = "test.mp3",
        documentId: String = "doc-1",
        libId: String = "lib-1",
        serverId: Long = 1L,
        albumId: String? = null
    ) = VirtualFile(
        name = name,
        documentId = documentId,
        mimeType = "audio/mpeg",
        displayName = name,
        lastModified = 1000L,
        size = 5000L,
        libId = libId,
        serverId = serverId,
        albumId = albumId,
        albumCoverTag = null,
        duration = null,
        year = null,
        title = null,
        album = null,
        track = null,
        artist = null,
        bitrate = null
    )

    @Test
    fun findAllInitiallyEmpty() {
        assertEquals(0, repo.findAll().size)
    }

    @Test
    fun putAndFindAll() {
        repo.put(createVf("a.mp3", "doc-1"), createVf("b.mp3", "doc-2"))
        assertEquals(2, repo.findAll().size)
    }

    @Test
    fun findByDocumentId_returnsCorrect() {
        repo.put(createVf("a.mp3", "doc-1"), createVf("b.mp3", "doc-2"))
        val found = repo.findByDocumentId("doc-1")
        assertNotNull(found)
        assertEquals("a.mp3", found!!.name)
    }

    @Test
    fun findByDocumentId_notFound() {
        assertNull(repo.findByDocumentId("nonexistent"))
    }

    @Test
    fun findAllByLibId_returnsCorrect() {
        repo.put(
            createVf("a.mp3", "doc-1", libId = "lib-1"),
            createVf("b.mp3", "doc-2", libId = "lib-1"),
            createVf("c.mp3", "doc-3", libId = "lib-2")
        )
        assertEquals(2, repo.findAllByLibId("lib-1").size)
        assertEquals(1, repo.findAllByLibId("lib-2").size)
        assertEquals(0, repo.findAllByLibId("lib-3").size)
    }

    @Test
    fun findAllByLibIdNotInAlbum_filtersAlbumNull() {
        repo.put(
            createVf("a.mp3", "doc-1", libId = "lib-1", albumId = null),
            createVf("b.mp3", "doc-2", libId = "lib-1", albumId = "album-1"),
            createVf("c.mp3", "doc-3", libId = "lib-1", albumId = null)
        )
        val result = repo.findAllByLibIdNotInAlbum("lib-1")
        assertEquals(2, result.size)
        assertTrue(result.all { it.albumId == null })
    }

    @Test
    fun findAllByAlbumId_returnsCorrect() {
        repo.put(
            createVf("a.mp3", "doc-1", albumId = "album-1"),
            createVf("b.mp3", "doc-2", albumId = null),
            createVf("c.mp3", "doc-3", albumId = "album-1")
        )
        assertEquals(2, repo.findAllByAlbumId("album-1").size)
        assertEquals(0, repo.findAllByAlbumId("nonexistent").size)
    }

    @Test
    fun countByServerId_returnsCorrect() {
        repo.put(
            createVf("a.mp3", "doc-1", serverId = 1L),
            createVf("b.mp3", "doc-2", serverId = 1L),
            createVf("c.mp3", "doc-3", serverId = 2L)
        )
        assertEquals(2, repo.countByServerId(1L))
        assertEquals(1, repo.countByServerId(2L))
        assertEquals(0, repo.countByServerId(3L))
    }

    @Test
    fun count_returnsCorrect() {
        assertEquals(0, repo.count())
        repo.put(createVf("a.mp3", "doc-1"), createVf("b.mp3", "doc-2"))
        assertEquals(2, repo.count())
    }

    @Test
    fun removeByLibId_removesCorrect() {
        repo.put(
            createVf("a.mp3", "doc-1", libId = "lib-1"),
            createVf("b.mp3", "doc-2", libId = "lib-2")
        )
        assertEquals(2, repo.count())
        repo.removeByLibId("lib-1")
        assertEquals(1, repo.count())
        assertEquals("doc-2", repo.findAll().first().documentId)
    }
}
