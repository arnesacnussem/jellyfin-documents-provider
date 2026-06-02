package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinServerTest {

    @Test
    fun info_returnsUsernameAndServerAndUrl() {
        val server = JellyfinServer(
            uuid = "uuid-1", url = "https://srv.local", serverName = "MyServer",
            username = "alice", token = "tok", library = mapOf()
        )
        assertEquals("alice@MyServer(https://srv.local)", server.info)
    }

    @Test
    fun name_returnsUsernameAtServerName() {
        val server = JellyfinServer(
            uuid = "uuid-1", url = "https://srv.local", serverName = "MyServer",
            username = "alice", token = "tok", library = mapOf()
        )
        assertEquals("alice@MyServer", server.name)
    }

    @Test
    fun name_withEmptyUsername() {
        val server = JellyfinServer(
            uuid = "uuid-1", url = "https://srv", serverName = "Srv",
            username = "", token = "tok", library = mapOf()
        )
        assertEquals("@Srv", server.name)
        assertEquals("@Srv(https://srv)", server.info)
    }

    @Test
    fun library_mapReflectsPassedLibraries() {
        val libraries = mapOf("lib1" to "Music", "lib2" to "Movies")
        val server = JellyfinServer(
            uuid = "uuid-1", url = "https://srv", serverName = "Srv",
            username = "u", token = "t", library = libraries
        )
        assertEquals(2, server.library.size)
        assertEquals("Music", server.library["lib1"])
        assertEquals("Movies", server.library["lib2"])
    }

    @Test
    fun library_passedValuesStored() {
        val server = JellyfinServer(
            uuid = "uuid-1", url = "https://srv", serverName = "Srv",
            username = "u", token = "t", library = mapOf("lib1" to "Music")
        )
        assertEquals(mapOf("lib1" to "Music"), server.library)
    }
}
