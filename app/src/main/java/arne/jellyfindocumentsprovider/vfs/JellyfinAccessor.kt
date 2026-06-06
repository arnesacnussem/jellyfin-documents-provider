package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.ApiClient.Companion.HEADER_ACCEPT
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.api.LyricLine
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUID
import arne.jellyfindocumentsprovider.vfs.JellyfinApi.Stream
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class JellyfinAccessor(val ctx: Context, val credential: JellyfinServer) : JellyfinApi {
    private val api: ApiClient = createJellyfin(ctx).createApi(
        baseUrl = credential.url,
        accessToken = JellyfinTokenStore.resolve(ctx, credential),
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun libraries() =
        api.userViewsApi.getUserViews().content.items

    companion object {
        @JvmStatic
        fun createJellyfin(ctx: Context) = createJellyfin {
            context = ctx
            clientInfo = ClientInfo("JellyfinDocumentsProvider", version = "in-dev")
        }
    }

    override suspend fun queryAudioItems(
        parentId: String, startIndex: Int, limit: Int
    ): BaseItemDtoQueryResult? {
        return try {
            api.itemsApi.getItems(
                GetItemsRequest(
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = setOf(SortOrder.ASCENDING),
                    includeItemTypes = setOf(BaseItemKind.AUDIO),
                    recursive = true,
                    fields = setOf(
                        ItemFields.DATE_CREATED,
                        ItemFields.SORT_NAME,
                        ItemFields.MEDIA_STREAMS,
                        ItemFields.MEDIA_SOURCES,
                    ),
                    startIndex = startIndex,
                    imageTypeLimit = 1,
                    enableImageTypes = setOf(ImageType.PRIMARY),
                    limit = limit,
                    parentId = parentId.toUUID()
                )
            ).content
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error querying Jellyfin API: ${e.stackTraceToString()}" }
            null
        }
    }

    override suspend fun downloadThumbnail(itemId: String, width: Int?, height: Int?): ByteArray? {
        try {
            val req = api.imageApi.getItemImage(
                itemId.toUUID(),
                ImageType.PRIMARY,
                fillWidth = width,
                fillHeight = height,
                quality = 96
            )

            if (req.status == 404) return null

            return req.content
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "unable to get thumbnail for $itemId " +
                        (e.message ?: "Error querying Jellyfin API: ${e.stackTraceToString()}")
            }
            return null
        }
    }

    override suspend fun streamThumbnail(itemId: String, width: Int?, height: Int?) =
        try {
            api.imageApi.getItemImage(
                itemId.toUUID(),
                ImageType.PRIMARY,
                fillWidth = width,
                fillHeight = height,
                quality = 96
            ).toStream(Stream.Type.FILE)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "unable to stream thumbnail for $itemId " +
                        (e.message ?: "Error querying Jellyfin API: ${e.stackTraceToString()}")
            }
            null
        }

    override suspend fun getItemNameById(id: String): String? {
        val uuid = id.toUUID()
        logcat(LogPriority.WARN) { "getItemNameById: id=$uuid" }
        return api.itemsApi.getItems(ids = setOfNotNull(uuid)).content.items?.firstOrNull()?.name
    }

    suspend fun getItemNameByUUID(id: UUID): String? =
        getItemNameById(id.asString())


    override suspend fun getLyrics(itemId: String): String? {
        return try {
            val dto = api.lyricsApi.getLyrics(itemId.toUUID()).content
            dto?.toLrc()
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "getLyrics($itemId): ${e.message}" }
            null
        }
    }

    override suspend fun reportPlaybackStart(itemId: String, playSessionId: String?) {
        api.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = itemId.toUUID(),
                isPaused = false,
                isMuted = false,
                playMethod = PlayMethod.DIRECT_PLAY,
                playSessionId = playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            )
        )
    }

    override suspend fun reportPlaybackProgress(itemId: String, playSessionId: String?, positionTicks: Long?, isPaused: Boolean) {
        api.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = itemId.toUUID(),
                playSessionId = playSessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                isMuted = false,
                canSeek = true,
                playMethod = PlayMethod.DIRECT_PLAY,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            )
        )
    }

    override suspend fun reportPlaybackStopped(itemId: String, playSessionId: String?, positionTicks: Long?) {
        api.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = itemId.toUUID(),
                positionTicks = positionTicks,
                playSessionId = playSessionId,
                failed = false,
            )
        )
    }

    override suspend fun getAudioStreamFactory(itemId: String, bps: Int): FileStreamFactory =
        { start, _ ->
            api.audioApi.getAudioStream(
                itemId.toUUID(),
                startTimeTicks = start,
                audioBitRate = bps
            ).toStream(Stream.Type.AUDIO_STREAM)
        }

    suspend fun getAudioStreamFactory(doc: VPath, bps: Int): FileStreamFactory =
        getAudioStreamFactory(doc.id, bps)

    override fun getDownloadStreamFactory(itemId: String): FileStreamFactory {
        val url = api.libraryApi.getDownloadUrl(itemId.toUUID())
        val authHeader = AuthorizationHeaderBuilder.buildHeader(
            clientName = api.clientInfo.name,
            clientVersion = api.clientInfo.version,
            deviceId = api.deviceInfo.id,
            deviceName = api.deviceInfo.name,
            accessToken = api.accessToken
        )
        return { start, end ->
            val rangeValue = if (end != null) "bytes=$start-$end" else "bytes=$start-"
            val response = okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", HEADER_ACCEPT)
                    .header("Authorization", authHeader)
                    .header("Range", rangeValue)
                    .build()
            ).execute()
            val contentLength = response.body?.contentLength() ?: -1
            val contentRange = response.header("Content-Range")
            logcat(LogPriority.DEBUG) {
                "DL stream url=$url range=$rangeValue status=${response.code}" +
                        " contentRange=$contentRange contentLength=$contentLength"
            }
            Stream(
                inputStream = response.body!!.byteStream(),
                length = contentLength,
                type = Stream.Type.FILE,
                range = contentRange?.toRangeHeader()
            )
        }
    }

    fun getAudioFileStreamFactory(id: VPath): FileStreamFactory =
        getDownloadStreamFactory(id.id)

    fun downloadWithoutRange(itemId: String): FileStreamFactory {
        val url = api.libraryApi.getDownloadUrl(itemId.toUUID())
        val authHeader = AuthorizationHeaderBuilder.buildHeader(
            clientName = api.clientInfo.name,
            clientVersion = api.clientInfo.version,
            deviceId = api.deviceInfo.id,
            deviceName = api.deviceInfo.name,
            accessToken = api.accessToken
        )
        return { _, _ ->
            val response = okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", HEADER_ACCEPT)
                    .header("Authorization", authHeader)
                    .build()
            ).execute()
            val contentLength = response.body?.contentLength() ?: -1
            val contentRange = response.header("Content-Range")
            logcat(LogPriority.DEBUG) {
                "DL stream url=$url range=NONE status=${response.code}" +
                        " contentRange=$contentRange contentLength=$contentLength"
            }
            Stream(
                inputStream = response.body!!.byteStream(),
                length = contentLength,
                type = Stream.Type.FILE,
                range = contentRange?.toRangeHeader()
            )
        }
    }


    data class ServerInfo(
        var url: String = "",
        var username: String = "",
        var password: String = ""
    ) {
        suspend fun login(
            ctx: Context
        ): JellyfinServer {
            if (url.isBlank())
                throw IllegalArgumentException("The baseUrl must not leave blank!")

            logcat {
                "try logging in to server: $this"
            }
            val api = createJellyfin(ctx).createApi(baseUrl = url)
            try {
                val serverPublicInfo by api.systemApi.getPublicSystemInfo()
                logcat {
                    "server info: $serverPublicInfo"
                }
                val authResult by api.userApi.authenticateUserByName(
                    AuthenticateUserByName(
                        username = username,
                        pw = password
                    )
                )
                logcat { "user info: ${authResult.user}" }

                val token = authResult.accessToken!!
                val userId = authResult.user!!.id.asString()
                JellyfinTokenStore.save(ctx, userId, token)
                password = ""
                return JellyfinServer(
                    url = url,
                    serverName = serverPublicInfo.serverName ?: "Unknown Server",
                    library = mapOf(),
                    token = "",
                    username = authResult.user!!.name!!,
                    uuid = userId
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "unable to login to server $url, error: ${e.stackTraceToString()}"
                }
            }
            throw IllegalArgumentException("unable to login to server $url")
        }
    }

    private fun Response<ByteArray>.toStream(type: Stream.Type): Stream {
        logcat(LogPriority.DEBUG) {
            "response status=${this.status}, headers: ${this.headers}"
        }
        val length = headers["Content-Length"]?.first()?.toLong() ?: -1L
        val rangeStr = headers["Content-Range"]?.first()
        return Stream(ByteArrayInputStream(content), length, type, rangeStr?.toRangeHeader())
    }

    private fun String.toRangeHeader(): LongRange? =
        Regex("""bytes (\d+)-(\d+)/(\d+)""").find(this)?.let {
            val (start, end, _) = it.destructured
            return LongRange(start.toLong(), end.toLong())
        }
}

private fun Long.ticksToLrcTimestamp(): String {
    val totalMs = this / 10_000
    val minutes = (totalMs / 60_000).coerceIn(0, 99)
    val seconds = (totalMs % 60_000) / 1_000
    val centiseconds = (totalMs % 1_000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
}

private fun LyricDto.toLrc(): String {
    val header = metadata?.let { m ->
        buildString {
            m.artist?.let { appendLine("[ar:$it]") }
            m.album?.let { appendLine("[al:$it]") }
            m.title?.let { appendLine("[ti:$it]") }
            m.author?.let { appendLine("[au:$it]") }
            m.by?.let { appendLine("[by:$it]") }
            m.offset?.let { appendLine("[offset:$it]") }
        }
    } ?: ""
    val body = lyrics.joinToString("\n") { line ->
        val ts = (line.start ?: 0L).ticksToLrcTimestamp()
        "[$ts]${line.text}"
    }
    return header + body
}
