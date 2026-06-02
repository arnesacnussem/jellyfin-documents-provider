package arne.jellyfindocumentsprovider.data.repository

import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ServerRepositoryTest {
    private lateinit var store: BoxStore
    private lateinit var repo: ServerRepository

    @Before
    fun setUp() {
        store = MyObjectBox.builder().name("test-${UUID.randomUUID()}").build()
        repo = ObjectBoxServerRepository(store.boxFor(JellyfinServer::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun findAllInitiallyEmpty() {
        assertEquals(0, repo.findAll().size)
    }

    @Test
    fun putAndFindAll() {
        val server = JellyfinServer(
            uuid = "uuid-1",
            url = "https://test.local",
            serverName = "ServerName",
            library = mutableMapOf("lib1" to "Library 1"),
            username = "user1",
            token = "token1"
        )
        repo.put(server)
        assertEquals(1, repo.findAll().size)
    }

    @Test
    fun findByUUID_returnsCorrect() {
        val server = JellyfinServer(
            uuid = "uuid-1",
            url = "https://test.local",
            serverName = "ServerName",
            library = mutableMapOf("lib1" to "Library 1"),
            username = "user1",
            token = "token1"
        )
        repo.put(server)
        val found = repo.findByUUID("uuid-1")
        assertNotNull(found)
        assertEquals("user1@ServerName", found!!.name)
    }

    @Test
    fun findByUUID_notFound() {
        assertNull(repo.findByUUID("nonexistent"))
    }

    @Test
    fun findByLibraryId_returnsCorrect() {
        val server = JellyfinServer(
            uuid = "uuid-1",
            url = "https://srv",
            serverName = "SrvName",
            library = mutableMapOf("lib1" to "Lib One", "lib2" to "Lib Two"),
            username = "u1",
            token = "t1"
        )
        repo.put(server)
        val found = repo.findByLibraryId("lib1")
        assertNotNull(found)
        assertEquals("uuid-1", found!!.uuid)
    }

    @Test
    fun count_returnsCorrect() {
        assertEquals(0, repo.count())
        repo.put(JellyfinServer(uuid = "u1", url = "https://s1", serverName = "sn1", library = mutableMapOf(), username = "u1", token = "t1"))
        assertEquals(1, repo.count())
        repo.put(JellyfinServer(uuid = "u2", url = "https://s2", serverName = "sn2", library = mutableMapOf(), username = "u2", token = "t2"))
        assertEquals(2, repo.count())
    }

    @Test
    fun putDuplicate_updatesExisting() {
        val server = JellyfinServer(
            uuid = "uuid-1",
            url = "https://test.local",
            serverName = "Original",
            library = mutableMapOf(),
            username = "user1",
            token = "token1"
        )
        repo.put(server)
        val updated = server.copy(serverName = "Updated")
        repo.put(updated)
        assertEquals(1, repo.count())
        assertEquals("user1@Updated", repo.findAll().first().name)
    }
}
