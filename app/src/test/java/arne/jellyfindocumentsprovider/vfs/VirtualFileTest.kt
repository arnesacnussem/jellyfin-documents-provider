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

class VirtualFileTest {

    private val credential = JellyfinServer(
        uuid = "user-1", url = "https://srv", serverName = "Srv",
        username = "u1", token = "tok", library = mapOf()
    )

    @Before
    fun setUp() {
        // Pre-populate static mimeTypeCache to avoid Android MimeTypeMap stub in unit tests
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

        val vf = dto.toVirtualFile(credential, "lib-1")

        assertEquals("Test Song", vf.name)
        assertEquals(dto.id!!.asString(), vf.documentId)
        assertEquals("audio/flac", vf.mimeType)
        assertEquals("Test Song", vf.displayName)
        assertEquals(15_000_000L, vf.size)
        assertEquals(500L, vf.duration)
        assertEquals(2024, vf.year)
        assertEquals("Test Song", vf.title)
        assertEquals("Album Name", vf.album)
        assertEquals(3, vf.track)
        assertEquals("Artist Name", vf.artist)
        assertEquals(1411, vf.bitrate)
        assertEquals(dto.albumId!!.asString(), vf.albumId)
        assertEquals("lib-1", vf.libId)
        assertEquals("tag-123", vf.albumCoverTag)
    }

    @Test
    fun toVirtualFile_defaultMimeTypeWhenContainerNull() {
        val dto = BaseItemDto(
            name = "Unknown", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = listOf(mediaSource(container = null))
        )
        val vf = dto.toVirtualFile(credential, "lib-1")
        // null container => key "null" in mimeTypeCache => getMimeTypeFromExtension(null) => null => "application/octet-stream"
        assertEquals("application/octet-stream", vf.mimeType)
    }

    @Test
    fun toVirtualFile_unknownContainerDefaultsToOctetStream() {
        val dto = BaseItemDto(
            name = "Unknown", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = listOf(mediaSource(container = "xyzunknown"))
        )
        val vf = dto.toVirtualFile(credential, "lib-1")
        assertEquals("application/octet-stream", vf.mimeType)
    }

    @Test
    fun toVirtualFile_knownContainerMapsToMimeType() {
        val dto = BaseItemDto(
            name = "Song", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = listOf(mediaSource(container = "mp3"))
        )
        val vf = dto.toVirtualFile(credential, "lib-1")
        assertEquals("audio/mpeg", vf.mimeType)
    }

    @Test
    fun toVirtualFile_zeroSizeWhenMediaSourcesNull() {
        val dto = BaseItemDto(
            name = "Empty", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = null
        )
        val vf = dto.toVirtualFile(credential, "lib-1")
        assertEquals(0L, vf.size)
    }

    @Test
    fun toVirtualFile_nullFieldsDefaultSafely() {
        val dto = BaseItemDto(
            name = null, id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            mediaSources = null
        )
        val vf = dto.toVirtualFile(credential, "lib-1")

        assertEquals("Unknown", vf.name)
        assertEquals(dto.id!!.asString(), vf.documentId)
        assertEquals("application/octet-stream", vf.mimeType)
        assertEquals(0L, vf.size)
        assertEquals(0L, vf.duration)
        assertEquals(0, vf.track)
        assertEquals(0, vf.bitrate)
        assertNull(vf.albumId)
        assertNull(vf.albumCoverTag)
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
        val vf = dto.toVirtualFile(credential, "lib-1")
        assertEquals("audio/flac", vf.mimeType)
        assertEquals(1000L, vf.size)
        assertEquals(500, vf.bitrate)
    }

    @Test
    fun toVirtualFile_emptyArtistsReturnsEmptyString() {
        val dto = BaseItemDto(
            name = "Song", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            artists = emptyList(),
            mediaSources = listOf(mediaSource(container = "mp3"))
        )
        val vf = dto.toVirtualFile(credential, "lib-1")
        assertEquals("", vf.artist)
    }

    @Test
    fun toVirtualFile_multipleArtistsJoined() {
        val dto = BaseItemDto(
            name = "Duet", id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            artists = listOf("Alice", "Bob"),
            mediaSources = listOf(mediaSource(container = "mp3"))
        )
        val vf = dto.toVirtualFile(credential, "lib-1")
        assertEquals("Alice, Bob", vf.artist)
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
        val vf = dto.toVirtualFile(credential, "lib-1")
        assertTrue(vf.providerId.isNotEmpty())
        assertTrue(vf.providerId.startsWith("user-1/lib-1/"))
        assertTrue(vf.providerId.contains("00000000-0000-0000-0000-000000000001"))
    }
}
