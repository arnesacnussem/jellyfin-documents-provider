package arne.jellyfindocumentsprovider.vfs

import org.junit.Assert.*
import org.junit.Test

class JellyfinAccessorTest {

    private val credential = JellyfinServer(
        url = "https://example.jellyfin.org",
        serverName = "Test Server",
        library = mapOf("lib1" to "Music"),
        token = "test-token",
        username = "testuser",
        uuid = "user-uuid"
    )

    @Test
    fun credentialFieldsAreSet() {
        assertEquals("https://example.jellyfin.org", credential.url)
        assertEquals("Test Server", credential.serverName)
        assertEquals("test-token", credential.token)
        assertEquals("testuser", credential.username)
        assertEquals("user-uuid", credential.uuid)
    }

    @Test
    fun credentialHasLibrary() {
        assertEquals("Music", credential.library["lib1"])
    }

    @Test
    fun serverInfoDefaultValues() {
        val info = JellyfinAccessor.ServerInfo()
        assertEquals("", info.url)
        assertEquals("", info.username)
        assertEquals("", info.password)
    }

    @Test
    fun serverInfoCustomValues() {
        val info = JellyfinAccessor.ServerInfo(
            url = "https://example.com",
            username = "user",
            password = "pass"
        )
        assertEquals("https://example.com", info.url)
        assertEquals("user", info.username)
        assertEquals("pass", info.password)
    }

    @Test
    fun jellyfinApiStreamTypeValues() {
        assertEquals(JellyfinApi.Stream.Type.FILE, JellyfinApi.Stream.Type.valueOf("FILE"))
        assertEquals(JellyfinApi.Stream.Type.AUDIO_STREAM, JellyfinApi.Stream.Type.valueOf("AUDIO_STREAM"))
    }
}
