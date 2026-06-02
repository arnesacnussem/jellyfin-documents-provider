package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import logcat.LogPriority
import logcat.logcat
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.ApiClient.Companion.HEADER_ACCEPT
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
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
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUID
import arne.jellyfindocumentsprovider.vfs.JellyfinApi.Stream
import java.io.ByteArrayOutputStream

class JellyfinAccessor(val ctx: Context, val credential: JellyfinServer) : JellyfinApi {
    private val api: ApiClient = createJellyfin(ctx).createApi(
        baseUrl = credential.url,
        accessToken = credential.token,
    )

    private val ktorClient = io.ktor.client.HttpClient {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    /**
     * get all user libraries
     */
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

            if (req.status == 404) {
                return null
            }

            return req.content.readAllToByteArray()
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
        return { start, _ ->
            ktorClient.get(url) {
                with(api) {
                    header(
                        key = HttpHeaders.Accept,
                        value = HEADER_ACCEPT,
                    )

                    header(
                        key = HttpHeaders.Authorization,
                        value = AuthorizationHeaderBuilder.buildHeader(
                            clientName = clientInfo.name,
                            clientVersion = clientInfo.version,
                            deviceId = deviceInfo.id,
                            deviceName = deviceInfo.name,
                            accessToken = accessToken
                        )
                    )

                    // range header
                    header(
                        key = HttpHeaders.Range,
                        value = "bytes=$start-"
                    )
                }
            }.let {
                logcat(LogPriority.DEBUG) {
                    "opened stream for url: $url, headers: ${it.headers}"
                }
                Stream(
                    channel = it.bodyAsChannel(),
                    length = it.contentLength() ?: -1,
                    type = Stream.Type.FILE,
                    range = it.headers[HttpHeaders.ContentRange]?.toRangeHeader()
                )
            }

        }
    }

    fun getAudioFileStreamFactory(id: VPath): FileStreamFactory =
        getDownloadStreamFactory(id.id)


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

                password = ""
                return JellyfinServer(
                    url = url,
                    serverName = serverPublicInfo.serverName ?: "Unknown Server",
                    library = mapOf(),
                    token = authResult.accessToken!!,
                    username = authResult.user!!.name!!,
                    uuid = authResult.user!!.id.asString()
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "unable to login to server $url, error: ${e.stackTraceToString()}"
                }
            }
            throw IllegalArgumentException("unable to login to server $url")
        }
    }

    private fun Response<ByteReadChannel>.toStream(type: Stream.Type): Stream {
        logcat(LogPriority.DEBUG) {
            "response status=${this.status}, headers: ${this.headers}"
        }
        val length = headers[HttpHeaders.ContentLength]?.first()?.toLong() ?: -1
        val rangeStr = headers[HttpHeaders.ContentRange]?.first()

        return Stream(content, length, type, rangeStr?.toRangeHeader())
    }

    private suspend fun ByteReadChannel.readAllToByteArray(): ByteArray {
        val buffer = ByteArray(10240)
        val outputStream = ByteArrayOutputStream()
        var bytesRead: Int
        while (true) {
            bytesRead = readAvailable(buffer)
            if (bytesRead == -1) break
            outputStream.write(buffer, 0, bytesRead)
        }
        return outputStream.toByteArray()
    }

    private fun String.toRangeHeader(): LongRange? =
        Regex("""bytes (\d+)-(\d+)/(\d+)""").find(this)?.let {
            val (start, end, _) = it.destructured
            return LongRange(start.toLong(), end.toLong())
        }
}
