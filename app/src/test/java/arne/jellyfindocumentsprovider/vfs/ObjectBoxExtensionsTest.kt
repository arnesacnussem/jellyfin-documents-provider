package arne.jellyfindocumentsprovider.vfs

import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ObjectBoxExtensionsTest {
    private lateinit var store: BoxStore

    private val defaultVfArgs = mapOf(
        "duration" to null as Long?, "year" to null as Int?,
        "title" to null as String?, "album" to null as String?,
        "track" to null as Int?, "artist" to null as String?,
        "bitrate" to null as Int?, "albumId" to null as String?,
        "albumCoverTag" to null as String?
    )

    @Before
    fun setUp() {
        store = MyObjectBox.builder().name("test-${UUID.randomUUID()}").build()
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun vf(
        name: String, docId: String, mime: String, display: String,
        lastMod: Long = 0, size: Long = 0, libId: String = "lib1",
        serverId: Long = 0, albumId: String? = null
    ) = VirtualFile(
        name = name, documentId = docId, mimeType = mime,
        displayName = display, lastModified = lastMod, size = size,
        libId = libId, serverId = serverId,
        duration = null, year = null, title = null, album = null,
        track = null, artist = null, bitrate = null,
        albumId = albumId, albumCoverTag = null
    )

    // ─── JellyfinServer extensions ────────────────────────────────

    @Test
    fun findByUUID_returnsServer() {
        val box = store.boxFor(JellyfinServer::class.java)
        val server = JellyfinServer(uuid = "uuid-1", url = "https://srv", serverName = "Srv",
            username = "u", token = "t", library = mapOf())
        box.put(server)

        val found = box.findByUUID("uuid-1")
        assertNotNull(found)
        assertEquals("uuid-1", found!!.uuid)
    }

    @Test
    fun findByUUID_notFound_returnsNull() {
        val box = store.boxFor(JellyfinServer::class.java)
        assertNull(box.findByUUID("nonexistent"))
    }

    @Test
    fun findByLibraryId_returnsServerContainingLib() {
        val box = store.boxFor(JellyfinServer::class.java)
        val server = JellyfinServer(uuid = "u1", url = "https://srv", serverName = "Srv",
            username = "u", token = "t", library = mapOf("lib1" to "Music", "lib2" to "Movies"))
        box.put(server)

        val found = box.findByLibraryId("lib1")
        assertNotNull(found)
        assertEquals("u1", found!!.uuid)
    }

    @Test
    fun findByLibraryId_notFound_returnsNull() {
        val box = store.boxFor(JellyfinServer::class.java)
        val server = JellyfinServer(uuid = "u1", url = "https://srv", serverName = "Srv",
            username = "u", token = "t", library = mapOf("lib1" to "Music"))
        box.put(server)

        assertNull(box.findByLibraryId("nonexistent"))
    }

    @Test
    fun findByLibraryId_multipleServers_returnsCorrect() {
        val box = store.boxFor(JellyfinServer::class.java)
        box.put(JellyfinServer(uuid = "u1", url = "https://a", serverName = "A",
            username = "u", token = "t", library = mapOf("lib1" to "Music")))
        box.put(JellyfinServer(uuid = "u2", url = "https://b", serverName = "B",
            username = "u", token = "t", library = mapOf("lib2" to "Movies")))

        val found = box.findByLibraryId("lib2")
        assertNotNull(found)
        assertEquals("u2", found!!.uuid)
    }

    // ─── VirtualFile extensions ───────────────────────────────────

    @Test
    fun findAllByLibId_returnsFiles() {
        val box = store.boxFor(VirtualFile::class.java)
        box.put(vf(name = "a.mp3", docId = "d1", mime = "audio/mpeg", display = "A", libId = "lib1"))
        box.put(vf(name = "b.mp3", docId = "d2", mime = "audio/mpeg", display = "B", libId = "lib1"))

        val result = box.findAllByLibId("lib1")
        assertEquals(2, result.size)
    }

    @Test
    fun findAllByLibIdNotInAlbum_excludesAlbumFiles() {
        val box = store.boxFor(VirtualFile::class.java)
        box.put(vf(name = "no-album.mp3", docId = "d1", mime = "audio/mpeg", display = "No Album", libId = "lib1"))
        box.put(vf(name = "in-album.mp3", docId = "d2", mime = "audio/mpeg", display = "In Album", libId = "lib1", albumId = "album-1"))

        val result = box.findAllByLibIdNotInAlbum("lib1")
        assertEquals(1, result.size)
        assertEquals("d1", result[0].documentId)
    }

    @Test
    fun findAllByAlbumId_returnsFiles() {
        val box = store.boxFor(VirtualFile::class.java)
        box.put(vf(name = "song1.mp3", docId = "d1", mime = "audio/mpeg", display = "Song1", libId = "lib1", albumId = "album-1"))
        box.put(vf(name = "song2.mp3", docId = "d2", mime = "audio/mpeg", display = "Song2", libId = "lib1", albumId = "album-1"))
        box.put(vf(name = "other.mp3", docId = "d3", mime = "audio/mpeg", display = "Other", libId = "lib1", albumId = "album-2"))

        val result = box.findAllByAlbumId("album-1")
        assertEquals(2, result.size)
    }

    @Test
    fun findByDocumentId_returnsCorrect() {
        val box = store.boxFor(VirtualFile::class.java)
        box.put(vf(name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song", libId = "lib1"))

        val found = box.findByDocumentId("doc-1")
        assertNotNull(found)
        assertEquals("song.mp3", found!!.name)
    }

    @Test
    fun findByDocumentId_notFound_returnsNull() {
        val box = store.boxFor(VirtualFile::class.java)
        assertNull(box.findByDocumentId("nonexistent"))
    }

    @Test
    fun countByServer_returnsCorrectCount() {
        val box = store.boxFor(VirtualFile::class.java)
        box.put(vf(name = "a.mp3", docId = "d1", mime = "audio/mpeg", display = "A", libId = "lib1", serverId = 1))
        box.put(vf(name = "b.mp3", docId = "d2", mime = "audio/mpeg", display = "B", libId = "lib1", serverId = 1))
        box.put(vf(name = "c.mp3", docId = "d3", mime = "audio/mpeg", display = "C", libId = "lib1", serverId = 2))

        assertEquals(2, box.countByServer(1L))
        assertEquals(1, box.countByServer(2L))
    }

    // ─── AlbumInfo extensions ────────────────────────────────────

    @Test
    fun findAllAlbumByLibId_returnsAlbums() {
        val box = store.boxFor(AlbumInfo::class.java)
        box.put(AlbumInfo(uuid = "album-1", name = "Album One", libId = "lib1"))
        box.put(AlbumInfo(uuid = "album-2", name = "Album Two", libId = "lib1"))
        box.put(AlbumInfo(uuid = "album-3", name = "Other", libId = "lib2"))

        val result = box.findAllAlbumByLibId("lib1")
        assertEquals(2, result.size)
    }

    @Test
    fun findAlbumByUUID_returnsCorrect() {
        val box = store.boxFor(AlbumInfo::class.java)
        box.put(AlbumInfo(uuid = "album-1", name = "Target Album", libId = "lib1"))
        box.put(AlbumInfo(uuid = "album-2", name = "Other", libId = "lib1"))

        val result = box.findAlbumByUUID("album-1")
        assertEquals(1, result.size)
        assertEquals("Target Album", result[0].name)
    }

    @Test
    fun findAlbumByUUID_notFound_returnsEmpty() {
        val box = store.boxFor(AlbumInfo::class.java)
        val result = box.findAlbumByUUID("nonexistent")
        assertTrue(result.isEmpty())
    }

    // ─── CacheInfo extensions ────────────────────────────────────

    @Test
    fun getOrCreate_createsNew() {
        val storeBox = store.boxFor(CacheInfo::class.java)
        val vf = vf(name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song", libId = "lib1")
            .also { store.boxFor(VirtualFile::class.java).put(it) }

        val cacheInfo = storeBox.getOrCreate(vf, "/tmp/test-cache.dat")

        assertNotNull(cacheInfo)
        assertEquals("doc-1", cacheInfo.vfDocId)
        assertEquals("/tmp/test-cache.dat", cacheInfo.localPath)
    }

    @Test
    fun getOrCreate_returnsExisting() {
        val storeBox = store.boxFor(CacheInfo::class.java)
        val vf = vf(name = "song.mp3", docId = "doc-1", mime = "audio/mpeg", display = "Song", libId = "lib1")
            .also { store.boxFor(VirtualFile::class.java).put(it) }

        val first = storeBox.getOrCreate(vf, "/tmp/test-cache.dat")
        val second = storeBox.getOrCreate(vf, "/tmp/test-cache.dat")

        assertEquals("should return same vfDocId", first.vfDocId, second.vfDocId)
    }

    @Test
    fun getOrCreate_createsSeparateForDifferentDocIds() {
        val storeBox = store.boxFor(CacheInfo::class.java)
        val vfBox = store.boxFor(VirtualFile::class.java)
        val vf1 = vf(name = "a.mp3", docId = "doc-1", mime = "audio/mpeg", display = "A", libId = "lib1")
            .also { vfBox.put(it) }
        val vf2 = vf(name = "b.mp3", docId = "doc-2", mime = "audio/mpeg", display = "B", libId = "lib1")
            .also { vfBox.put(it) }

        val c1 = storeBox.getOrCreate(vf1, "/tmp/a.dat")
        val c2 = storeBox.getOrCreate(vf2, "/tmp/b.dat")

        assertNotSame(c1, c2)
        assertEquals("doc-1", c1.vfDocId)
        assertEquals("doc-2", c2.vfDocId)
    }
}
