package arne.jellyfindocumentsprovider.vfs

import arne.jellyfindocumentsprovider.data.AppRepos
import arne.jellyfindocumentsprovider.data.repository.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DatabaseSyncTaskTest {

    private val api = mockk<JellyfinApi>(relaxed = true)
    private val serverRepo = mockk<ServerRepository>(relaxed = true)
    private val vfRepo = mockk<VirtualFileRepository>(relaxed = true)
    private val albumInfoRepo = mockk<AlbumInfoRepository>(relaxed = true)
    private val thumbCacheRepo = mockk<ThumbCacheRepository>(relaxed = true)

    private val repos = AppRepos(
        server = serverRepo,
        virtualFile = vfRepo,
        albumInfo = albumInfoRepo,
        cacheInfo = mockk(relaxed = true),
        thumbCache = thumbCacheRepo,
    )

    private val credential = JellyfinServer(
        uuid = "u1", url = "https://srv", serverName = "Srv",
        username = "user", token = "tok", library = mapOf("lib1" to "Music")
    )

    private val task get() = DatabaseSyncTask(api, repos, credential)

    private fun audioItem(id: UUID = UUID.randomUUID(), name: String = "Song",
                          albumId: UUID? = null, album: String? = null) =
        BaseItemDto(
            id = id, name = name, type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO,
            runTimeTicks = 1000, indexNumber = 1, artists = listOf("Artist"),
            album = album, albumId = albumId
        )

    private fun queryResult(total: Int, items: List<BaseItemDto> = emptyList(), startIndex: Int = 0) =
        BaseItemDtoQueryResult(items = items, totalRecordCount = total, startIndex = startIndex)

    @Test
    fun sync_emptyLibrary_noErrors() {
        coEvery { api.queryAudioItems("lib1", limit = 0) } returns queryResult(0)
        every { vfRepo.findAll() } returns emptyList()

        val progress = mutableListOf<String>()
        runBlocking { task.sync { text, _, _ -> progress.add(text) } }

        assertTrue(progress.isNotEmpty())
        coVerify(exactly = 1) { api.queryAudioItems("lib1", limit = 0) }
    }

    @Test
    fun sync_oneBatch_queriesAndSaves() {
        coEvery { api.queryAudioItems("lib1", limit = 0) } returns queryResult(2)
        coEvery { api.queryAudioItems("lib1", 0, 1000) } returns queryResult(2,
            listOf(audioItem(name = "Song 1"), audioItem(name = "Song 2")))
        every { vfRepo.findAll() } returns emptyList()
        every { vfRepo.put(any<VirtualFile>()) } just Runs

        runBlocking { task.sync { _, _, _ -> } }

        verify(exactly = 1) { vfRepo.removeByLibId("lib1") }
        verify(exactly = 1) { albumInfoRepo.removeByLibId("lib1") }
    }

    @Test
    fun sync_multiBatch_fetchesInBatches() {
        val totalItems = 2500

        coEvery { api.queryAudioItems("lib1", limit = 0) } returns queryResult(totalItems)
        coEvery { api.queryAudioItems("lib1", 0, 1000) } returns queryResult(1000, (1..1000).map { audioItem() })
        coEvery { api.queryAudioItems("lib1", 1000, 1000) } returns queryResult(1000, (1001..2000).map { audioItem() })
        coEvery { api.queryAudioItems("lib1", 2000, 1000) } returns queryResult(500, (2001..2500).map { audioItem() })
        every { vfRepo.put(any<VirtualFile>()) } just Runs
        every { vfRepo.findAll() } returns emptyList()

        runBlocking { task.sync(batchSize = 1000) { _, _, _ -> } }

        coVerify(exactly = 1) { api.queryAudioItems("lib1", 0, 1000) }
        coVerify(exactly = 1) { api.queryAudioItems("lib1", 1000, 1000) }
        coVerify(exactly = 1) { api.queryAudioItems("lib1", 2000, 1000) }
    }

    @Test
    fun sync_progressCallbacks_fire() {
        coEvery { api.queryAudioItems("lib1", limit = 0) } returns queryResult(3)
        coEvery { api.queryAudioItems("lib1", 0, 1000) } returns queryResult(3,
            (1..3).map { audioItem() })
        every { vfRepo.put(any<VirtualFile>()) } just Runs
        every { vfRepo.findAll() } returns emptyList()

        val progressTexts = mutableListOf<String>()
        runBlocking { task.sync { text, _, _ -> progressTexts.add(text) } }

        assertTrue("should have progress messages, got: $progressTexts", progressTexts.isNotEmpty())
        assertTrue(progressTexts.any { it.contains("total items") })
    }

    @Test
    fun sync_withAlbums_createsAlbumEntries() {
        val albumId = UUID.randomUUID()

        coEvery { api.queryAudioItems("lib1", limit = 0) } returns queryResult(2)
        coEvery { api.queryAudioItems("lib1", 0, 1000) } returns queryResult(2, listOf(
            audioItem(name = "Track 1", albumId = albumId, album = "Album Name"),
            audioItem(name = "Track 2", albumId = albumId, album = "Album Name"),
        ))
        coEvery { api.getItemNameById(albumId.asString()) } returns "Album Name"
        every { vfRepo.put(any<VirtualFile>()) } just Runs
        every { thumbCacheRepo.put(any()) } just Runs
        every { albumInfoRepo.put(any()) } just Runs

        // findAll returns the saved files with albumId
        every { vfRepo.findAll() } returns listOf(
            VirtualFile(name = "Track 1", documentId = "d1", mimeType = "audio/mpeg",
                displayName = "Track 1", lastModified = 0, size = 100, libId = "lib1",
                albumId = albumId.asString(), album = "Album Name",
                duration = null, year = null, title = null,
                track = null, artist = null, bitrate = null, albumCoverTag = null),
            VirtualFile(name = "Track 2", documentId = "d2", mimeType = "audio/mpeg",
                displayName = "Track 2", lastModified = 0, size = 200, libId = "lib1",
                albumId = albumId.asString(), album = "Album Name",
                duration = null, year = null, title = null,
                track = null, artist = null, bitrate = null, albumCoverTag = null),
        )

        runBlocking { task.sync { _, _, _ -> } }

        verify { thumbCacheRepo.put(any<ThumbCache>()) }
        verify { albumInfoRepo.put(any<AlbumInfo>()) }
    }

    @Test
    fun sync_nullItemsFromApi_breaksCleanly() {
        coEvery { api.queryAudioItems("lib1", limit = 0) } returns queryResult(10)
        coEvery { api.queryAudioItems("lib1", 0, 1000) } returns queryResult(0, emptyList())
        every { vfRepo.put(any<VirtualFile>()) } just Runs
        every { vfRepo.findAll() } returns emptyList()

        runBlocking { task.sync { _, _, _ -> } }

        // Should not crash, just stop after first null batch
        coVerify(exactly = 1) { api.queryAudioItems("lib1", 0, 1000) }
    }

    @Test
    fun sync_withoutAlbum_filesGetThumbCacheDirectly() {
        coEvery { api.queryAudioItems("lib1", limit = 0) } returns queryResult(1)
        coEvery { api.queryAudioItems("lib1", 0, 1000) } returns queryResult(1,
            listOf(audioItem(name = "NoAlbumTrack")))
        every { vfRepo.put(any<VirtualFile>()) } just Runs
        every { thumbCacheRepo.put(any()) } just Runs

        val savedVf = VirtualFile(
            name = "NoAlbumTrack", documentId = "d1", mimeType = "audio/mpeg",
            displayName = "NoAlbumTrack", lastModified = 0, size = 100, libId = "lib1",
            duration = null, year = null, title = null, album = null,
            track = null, artist = null, bitrate = null, albumId = null, albumCoverTag = null)
        every { vfRepo.findAll() } returns listOf(savedVf)

        runBlocking { task.sync { _, _, _ -> } }

        verify { thumbCacheRepo.put(any<ThumbCache>()) }
    }
}
