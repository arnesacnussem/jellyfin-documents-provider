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
import java.io.ByteArrayOutputStream

class JellyfinAccessor(val ctx: Context, val credential: JellyfinServer) {
    private val api: ApiClient = createJellyfin(ctx).createApi(
        baseUrl = credential.url,
        accessToken = credential.token,
    )

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

    suspend fun queryAudioItems(
        parent: String, startIndex: Int = 0, limit: Int = 100
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
                    parentId = parent.toUUID()
                )
            ).content
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error querying Jellyfin API: ${e.stackTraceToString()}" }
            null
        }
    }

    suspend fun downloadThumbnail(uuid: String, w: Int? = 250, h: Int? = 250): ByteArray? {
        try {
            val req = api.imageApi.getItemImage(
                uuid.toUUID(),
                ImageType.PRIMARY,
                fillWidth = w,
                fillHeight = h,
                quality = 96
            )

            if (req.status == 404) {
                return null
            }

            return req.content.readAllToByteArray()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "unable to get thumbnail for $uuid " +
                        (e.message ?: "Error querying Jellyfin API: ${e.stackTraceToString()}")
            }
            return null
        }
    }

    suspend fun streamThumbnail(document: String, w: Int? = 250, h: Int? = 250) =
        try {
            api.imageApi.getItemImage(
                document.toUUID(),
                ImageType.PRIMARY,
                fillWidth = w,
                fillHeight = h,
                quality = 96
            ).toStream(Stream.Type.FILE)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "unable to stream thumbnail for $document " +
                        (e.message ?: "Error querying Jellyfin API: ${e.stackTraceToString()}")
            }
            null
        }

    suspend fun getItemNameByUUID(id: UUID): String? {
        logcat(LogPriority.WARN) { "getItemNameByUUID: id=$id" }
        return api.itemsApi.getItems(ids = setOfNotNull(id)).content.items?.firstOrNull()?.name
    }


    suspend fun getAudioStreamFactory(
        doc: VPath,
        bps: Int,
    ): FileStreamFactory =
        { start: Long, _: Long? ->
            api.audioApi.getAudioStream(
                doc.uuid,
                startTimeTicks = start,
                audioBitRate = bps
            ).toStream(Stream.Type.AUDIO_STREAM)
        }

    fun getAudioFileStreamFactory(id: VPath): FileStreamFactory {
        val url = api.libraryApi.getDownloadUrl(id.uuid)
        val ktorClient = io.ktor.client.HttpClient()
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

    data class Stream(
        val channel: ByteReadChannel,
        val length: Long,
        val type: Type,
        val range: LongRange?
    ) {
        enum class Type {
            FILE, AUDIO_STREAM
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

typealias FileStreamFactory = suspend (start: Long, end: Long?) -> JellyfinAccessor.Stream