package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AlbumInfoRepositoryTest {
    private lateinit var store: BoxStore
    private lateinit var repo: AlbumInfoRepository

    @Before
    fun setUp() {
        store = MyObjectBox.builder().name("test-${UUID.randomUUID()}").build()
        repo = ObjectBoxAlbumInfoRepository(store.boxFor(AlbumInfo::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun album(uuid: String, name: String, libId: String, serverId: Long = 1L) =
        AlbumInfo(uuid = uuid, name = name, libId = libId, serverId = serverId)

    @Test
    fun findAllByLibId_returnsCorrect() {
        repo.put(
            album("uuid-1", "Album 1", "lib-1", 1L),
            album("uuid-2", "Album 2", "lib-1", 1L),
            album("uuid-3", "Album 3", "lib-2", 2L)
        )
        assertEquals(2, repo.findAllByLibId("lib-1", 1L).size)
        assertEquals(1, repo.findAllByLibId("lib-2", 2L).size)
    }

    @Test
    fun findAlbumByUUID_returnsCorrect() {
        repo.put(album("uuid-1", "Album 1", "lib-1", 1L))
        val found = repo.findAlbumByUUID("uuid-1", 1L)
        assertEquals(1, found.size)
        assertEquals("Album 1", found.first().name)
    }

    @Test
    fun findAlbumByUUID_notFound() {
        assertEquals(0, repo.findAlbumByUUID("nonexistent", 1L).size)
    }

    @Test
    fun put_allPersisted() {
        repo.put(
            album("uuid-1", "A1", "l1", 1L),
            album("uuid-2", "A2", "l1", 1L)
        )
        assertEquals(2, repo.findAllByLibId("l1", 1L).size)
    }

    @Test
    fun removeByLibId_removesCorrect() {
        repo.put(
            album("uuid-1", "A1", "lib-1", 1L),
            album("uuid-2", "A2", "lib-2", 1L)
        )
        repo.removeByLibId("lib-1", 1L)
        assertEquals(0, repo.findAllByLibId("lib-1", 1L).size)
        assertEquals(1, repo.findAllByLibId("lib-2", 1L).size)
    }
}
