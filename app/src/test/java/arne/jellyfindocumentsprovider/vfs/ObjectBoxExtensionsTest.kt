package arne.jellyfindocumentsprovider.vfs

import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ObjectBoxExtensionsTest {
    private lateinit var store: BoxStore

    @Before
    fun setUp() {
        store = MyObjectBox.builder().name("test-${UUID.randomUUID()}").build()
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun item(
        name: String = "song.mp3", docId: String = "doc-1",
        mime: String = "audio/mpeg", display: String = "Song",
        albumId: String? = null
    ) = ItemRecord(
        documentId = docId,
        name = name, mimeType = mime, displayName = display,
        lastModified = 0, size = 0,
        duration = null, year = null, title = name,
        album = null, track = null, artist = null,
        bitrate = null, albumId = albumId, albumCoverTag = null,
    )

    private fun vf(
        docId: String = "doc-1", libId: String = "lib1",
        serverId: Long = 0, albumId: String? = null
    ) = VirtualFile(
        documentId = docId, libId = libId, serverId = serverId, albumId = albumId,
    )

    private fun vfWithItem(
        itemBox: io.objectbox.Box<ItemRecord>,
        vfBox: io.objectbox.Box<VirtualFile>,
        name: String = "song.mp3", docId: String = "doc-1",
        mime: String = "audio/mpeg", display: String = "Song",
        libId: String = "lib1", serverId: Long = 0, albumId: String? = null
    ): VirtualFile {
        val it = item(name = name, docId = docId, mime = mime, display = display, albumId = albumId)
        itemBox.put(it)
        val v = vf(docId = docId, libId = libId, serverId = serverId, albumId = albumId)
        v.item.target = it
        vfBox.put(v)
        return v
    }

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

    @Test
    fun findAllByLibId_returnsFiles() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val vfBox = store.boxFor(VirtualFile::class.java)
        vfWithItem(itemBox, vfBox, name = "a.mp3", docId = "d1", libId = "lib1", serverId = 1)
        vfWithItem(itemBox, vfBox, name = "b.mp3", docId = "d2", libId = "lib1", serverId = 1)

        val result = vfBox.findAllByLibId("lib1", 1)
        assertEquals(2, result.size)
    }

    @Test
    fun findAllByLibIdNotInAlbum_excludesAlbumFiles() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val vfBox = store.boxFor(VirtualFile::class.java)
        vfWithItem(itemBox, vfBox, name = "no-album.mp3", docId = "d1", libId = "lib1", serverId = 1)
        vfWithItem(itemBox, vfBox, name = "in-album.mp3", docId = "d2", libId = "lib1", serverId = 1, albumId = "album-1")

        val result = vfBox.findAllByLibIdNotInAlbum("lib1", 1)
        assertEquals(1, result.size)
        assertEquals("d1", result[0].documentId)
    }

    @Test
    fun findAllByAlbumId_returnsFiles() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val vfBox = store.boxFor(VirtualFile::class.java)
        vfWithItem(itemBox, vfBox, name = "song1.mp3", docId = "d1", libId = "lib1", serverId = 1, albumId = "album-1")
        vfWithItem(itemBox, vfBox, name = "song2.mp3", docId = "d2", libId = "lib1", serverId = 1, albumId = "album-1")
        vfWithItem(itemBox, vfBox, name = "other.mp3", docId = "d3", libId = "lib1", serverId = 1, albumId = "album-2")

        val result = vfBox.findAllByAlbumId("album-1", 1)
        assertEquals(2, result.size)
    }

    @Test
    fun findByDocumentId_returnsCorrect() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val vfBox = store.boxFor(VirtualFile::class.java)
        vfWithItem(itemBox, vfBox, name = "song.mp3", docId = "doc-1", libId = "lib1", serverId = 1)

        val found = vfBox.findByDocumentId("doc-1", 1)
        assertNotNull(found)
        assertEquals("song.mp3", found!!.item.target.name)
    }

    @Test
    fun findByDocumentId_notFound_returnsNull() {
        val vfBox = store.boxFor(VirtualFile::class.java)
        assertNull(vfBox.findByDocumentId("nonexistent", 1))
    }

    @Test
    fun countByServer_returnsCorrectCount() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val vfBox = store.boxFor(VirtualFile::class.java)
        vfWithItem(itemBox, vfBox, name = "a.mp3", docId = "d1", libId = "lib1", serverId = 1)
        vfWithItem(itemBox, vfBox, name = "b.mp3", docId = "d2", libId = "lib1", serverId = 1)
        vfWithItem(itemBox, vfBox, name = "c.mp3", docId = "d3", libId = "lib1", serverId = 2)

        assertEquals(2, vfBox.countByServer(1L))
        assertEquals(1, vfBox.countByServer(2L))
    }

    @Test
    fun findAllAlbumByLibId_returnsAlbums() {
        val box = store.boxFor(AlbumInfo::class.java)
        box.put(AlbumInfo(uuid = "album-1", name = "Album One", libId = "lib1", serverId = 1))
        box.put(AlbumInfo(uuid = "album-2", name = "Album Two", libId = "lib1", serverId = 1))
        box.put(AlbumInfo(uuid = "album-3", name = "Other", libId = "lib2", serverId = 2))

        val result = box.findAllAlbumByLibId("lib1", 1)
        assertEquals(2, result.size)
    }

    @Test
    fun findAlbumByUUID_returnsCorrect() {
        val box = store.boxFor(AlbumInfo::class.java)
        box.put(AlbumInfo(uuid = "album-1", name = "Target Album", libId = "lib1", serverId = 1))
        box.put(AlbumInfo(uuid = "album-2", name = "Other", libId = "lib1", serverId = 1))

        val result = box.findAlbumByUUID("album-1", 1)
        assertEquals(1, result.size)
        assertEquals("Target Album", result[0].name)
    }

    @Test
    fun findAlbumByUUID_notFound_returnsEmpty() {
        val box = store.boxFor(AlbumInfo::class.java)
        val result = box.findAlbumByUUID("nonexistent", 1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun getOrCreate_createsNew() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val storeBox = store.boxFor(CacheInfo::class.java)
        val vf = vfWithItem(itemBox, store.boxFor(VirtualFile::class.java),
            name = "song.mp3", docId = "doc-1", libId = "lib1", serverId = 1)

        val cacheInfo = storeBox.getOrCreate(vf, "/tmp/test-cache.dat")

        assertNotNull(cacheInfo)
        assertEquals("doc-1", cacheInfo.vfDocId)
        assertEquals("/tmp/test-cache.dat", cacheInfo.localPath)
    }

    @Test
    fun getOrCreate_returnsExisting() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val storeBox = store.boxFor(CacheInfo::class.java)
        val vf = vfWithItem(itemBox, store.boxFor(VirtualFile::class.java),
            name = "song.mp3", docId = "doc-1", libId = "lib1", serverId = 1)

        val first = storeBox.getOrCreate(vf, "/tmp/test-cache.dat")
        val second = storeBox.getOrCreate(vf, "/tmp/test-cache.dat")

        assertEquals("should return same vfDocId", first.vfDocId, second.vfDocId)
    }

    @Test
    fun getOrCreate_createsSeparateForDifferentDocIds() {
        val itemBox = store.boxFor(ItemRecord::class.java)
        val storeBox = store.boxFor(CacheInfo::class.java)
        val vfBox = store.boxFor(VirtualFile::class.java)
        val vf1 = vfWithItem(itemBox, vfBox, name = "a.mp3", docId = "doc-1", libId = "lib1", serverId = 1)
        val vf2 = vfWithItem(itemBox, vfBox, name = "b.mp3", docId = "doc-2", libId = "lib1", serverId = 1)

        val c1 = storeBox.getOrCreate(vf1, "/tmp/a.dat")
        val c2 = storeBox.getOrCreate(vf2, "/tmp/b.dat")

        assertNotSame(c1, c2)
        assertEquals("doc-1", c1.vfDocId)
        assertEquals("doc-2", c2.vfDocId)
    }
}
