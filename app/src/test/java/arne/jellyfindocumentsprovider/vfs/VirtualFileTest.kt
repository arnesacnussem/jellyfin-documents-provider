package arne.jellyfindocumentsprovider.vfs

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import arne.jellyfindocumentsprovider.vfs.VirtualFile.Companion.toVirtualFile
import arne.jellyfindocumentsprovider.vfs.VirtualFile.Companion.toItemRecord

class VirtualFileTest {

    private val credential = JellyfinServer(
        uuid = "user-1", url = "https://srv", serverName = "Srv",
        username = "u1", token = "tok", library = mapOf()
    )

    @Before
    fun setUp() {
        try {
            val field = VirtualFile::class.java.getDeclaredField("mimeTypeCache")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val cache = field.get(null) as HashMap<String, String>
            cache.clear()
            cache["flac"] = "audio/flac"
            cache["mp3"] = "audio/mpeg"
            cache["xyzunknown"] = "application/octet-stream"
        } catch (_: NoSuchFieldException) {
        }
    }

    private fun mediaSource(
        container: String? = null,
        size: Long? = null,
        bitrate: Int? = null,
    ) = MediaSourceInfo(
        protocol = MediaProtocol.FILE,
        id = null, path = null, encoderPath = null, encoderProtocol = null,
        type = MediaSourceType.DEFAULT, container = container,
        size = size, name = null, isRemote = false, eTag = null,
        runTimeTicks = null, readAtNativeFramerate = false,
        ignoreDts = false, ignoreIndex = false, genPtsInput = false,
        supportsTranscoding = false, supportsDirectStream = false,
        supportsDirectPlay = false, isInfiniteStream = false,
        requiresOpening = false, openToken = null, requiresClosing = false,
        liveStreamId = null, bufferMs = null, requiresLooping = false,
        supportsProbing = false, videoType = null, isoType = null,
        video3dFormat = null, mediaStreams = emptyList(),
        mediaAttachments = emptyList(), formats = emptyList(),
        bitrate = bitrate, timestamp = null, requiredHttpHeaders = null,
        transcodingUrl = null, transcodingSubProtocol = MediaStreamProtocol.HTTP,
        transcodingContainer = null, analyzeDurationMs = null,
        defaultAudioStreamIndex = null, defaultSubtitleStreamIndex = null,
        hasSegments = false,
    )

    @Test
    fun toVirtualFile_basicFields() {
        val dto = BaseItemDto(
            name = "Test Song",
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO,
            mediaType = MediaType.AUDIO,
            runTimeTicks = 5_000_000L,
            productionYear = 2024,
            indexNumber = 3,
            artists = listOf("Artist Name"),
            album = "Album Name",
            albumId = UUID.randomUUID(),
            albumPrimaryImageTag = "tag-123",
            dateCreated = LocalDateTime.of(2024, 1, 15, 0, 0),
            mediaSources = listOf(mediaSource(container = "flac", size = 15_000_000L, bitrate = 1411))
        )

        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        val item = vf.item.target

        assertEquals("Test Song", item.name)
        assertEquals(dto.id!!.asString(), vf.documentId)
        assertEquals("audio/flac", item.mimeType)
        assertEquals("Test Song", item.displayName)
        assertEquals(15_000_000L, item.size)
        assertEquals(500L, item.duration)
        assertEquals(2024, item.year)
        assertEquals("Test Song", item.title)
        assertEquals("Album Name", item.album)
        assertEquals(3, item.track)
        assertEquals("Artist Name", item.artist)
        assertEquals(1411, item.bitrate)
        assertEquals(dto.albumId!!.asString(), vf.albumId)
        assertEquals("lib-1", vf.libId)
        assertEquals("tag-123", item.albumCoverTag)
    }

    @Test
    fun toVirtualFile_defaultMimeTypeWhenContainerNull() {
        val dto = BaseItemDto(
            name = "Unknown", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = listOf(mediaSource(container = null))
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        assertEquals("application/octet-stream", vf.item.target.mimeType)
    }

    @Test
    fun toVirtualFile_unknownContainerDefaultsToOctetStream() {
        val dto = BaseItemDto(
            name = "Unknown", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = listOf(mediaSource(container = "xyzunknown"))
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        assertEquals("application/octet-stream", vf.item.target.mimeType)
    }

    @Test
    fun toVirtualFile_knownContainerMapsToMimeType() {
        val dto = BaseItemDto(
            name = "Song", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = listOf(mediaSource(container = "mp3"))
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        assertEquals("audio/mpeg", vf.item.target.mimeType)
    }

    @Test
    fun toVirtualFile_zeroSizeWhenMediaSourcesNull() {
        val dto = BaseItemDto(
            name = "Empty", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = null
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        assertEquals(0L, vf.item.target.size)
    }

    @Test
    fun toVirtualFile_nullFieldsDefaultSafely() {
        val dto = BaseItemDto(
            name = null, id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = null
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        val item = vf.item.target

        assertEquals("Unknown", item.name)
        assertEquals(dto.id!!.asString(), vf.documentId)
        assertEquals("application/octet-stream", item.mimeType)
        assertEquals(0L, item.size)
        assertEquals(0L, item.duration)
        assertEquals(0, item.track)
        assertEquals(0, item.bitrate)
        assertNull(vf.albumId)
        assertNull(item.albumCoverTag)
    }

    @Test
    fun toVirtualFile_multipleMediaSourcesUsesFirst() {
        val dto = BaseItemDto(
            name = "Song", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = listOf(
                mediaSource(container = "flac", size = 1000, bitrate = 500),
                mediaSource(container = "mp3", size = 2000, bitrate = 320)
            )
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        val item = vf.item.target
        assertEquals("audio/flac", item.mimeType)
        assertEquals(1000L, item.size)
        assertEquals(500, item.bitrate)
    }

    @Test
    fun toVirtualFile_emptyArtistsReturnsEmptyString() {
        val dto = BaseItemDto(
            name = "Song", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            artists = emptyList(),
            mediaSources = listOf(mediaSource(container = "mp3"))
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        assertEquals("", vf.item.target.artist)
    }

    @Test
    fun toVirtualFile_multipleArtistsJoined() {
        val dto = BaseItemDto(
            name = "Duet", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            artists = listOf("Alice", "Bob"),
            mediaSources = listOf(mediaSource(container = "mp3"))
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        assertEquals("Alice, Bob", vf.item.target.artist)
    }

    @Test
    fun toVirtualFile_providerIdIsVPathString() {
        val dto = BaseItemDto(
            name = "Song",
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            albumId = UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
            mediaSources = listOf(mediaSource(container = "mp3"))
        )
        val itemRecord = dto.toItemRecord()
        val vf = dto.toVirtualFile(credential, "lib-1", itemRecord)
        assertTrue(vf.providerId.isNotEmpty())
        assertTrue(vf.providerId.startsWith("user-1/lib-1/"))
        assertTrue(vf.providerId.contains("00000000-0000-0000-0000-000000000001"))
    }
}
