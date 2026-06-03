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

    @Test
    fun findAllByLibId_returnsCorrect() {
        repo.put(
            AlbumInfo(uuid = "uuid-1", name = "Album 1", libId = "lib-1"),
            AlbumInfo(uuid = "uuid-2", name = "Album 2", libId = "lib-1"),
            AlbumInfo(uuid = "uuid-3", name = "Album 3", libId = "lib-2")
        )
        assertEquals(2, repo.findAllByLibId("lib-1").size)
        assertEquals(1, repo.findAllByLibId("lib-2").size)
    }

    @Test
    fun findAlbumByUUID_returnsCorrect() {
        repo.put(AlbumInfo(uuid = "uuid-1", name = "Album 1", libId = "lib-1"))
        val found = repo.findAlbumByUUID("uuid-1")
        assertEquals(1, found.size)
        assertEquals("Album 1", found.first().name)
    }

    @Test
    fun findAlbumByUUID_notFound() {
        assertEquals(0, repo.findAlbumByUUID("nonexistent").size)
    }

    @Test
    fun put_allPersisted() {
        repo.put(
            AlbumInfo(uuid = "uuid-1", name = "A1", libId = "l1"),
            AlbumInfo(uuid = "uuid-2", name = "A2", libId = "l1")
        )
        assertEquals(2, repo.findAllByLibId("l1").size)
    }

    @Test
    fun removeByLibId_removesCorrect() {
        repo.put(
            AlbumInfo(uuid = "uuid-1", name = "A1", libId = "lib-1"),
            AlbumInfo(uuid = "uuid-2", name = "A2", libId = "lib-2")
        )
        repo.removeByLibId("lib-1")
        assertEquals(0, repo.findAllByLibId("lib-1").size)
        assertEquals(1, repo.findAllByLibId("lib-2").size)
    }
}
