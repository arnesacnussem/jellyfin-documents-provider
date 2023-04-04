package a.sac.jellyfindocumentsprovider.jellyfin

import a.sac.jellyfindocumentsprovider.MediaLibraryListItem
import a.sac.jellyfindocumentsprovider.ServerInfo
import a.sac.jellyfindocumentsprovider.database.ObjectBox
import a.sac.jellyfindocumentsprovider.database.entities.Credential
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.logcat
import android.content.Context
import android.graphics.Point
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.SortOrder
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinProvider @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val jellyfin = createJellyfin {
        clientInfo = ClientInfo("JellyfinStorageProvider", version = "indev")
        context = ctx
    }
    private val api = jellyfin.createApi()


    /**
     * Login with server info, will overwrite current login state
     */
    @Throws(AuthorizationException::class, IllegalArgumentException::class)
    suspend fun login(info: ServerInfo) = with(info) {
        if (baseUrl.isBlank()) {
            throw IllegalArgumentException("The baseUrl must not leave blank!")
        }
        api.baseUrl = baseUrl
        api.accessToken = null
        api.userId = null

        try {
            val serverPublicSystemInfo by api.systemApi.getPublicSystemInfo()

            val result by api.userApi.authenticateUserByName(
                username = username,
                password = password,
            )

            val cred = Credential(
                uid = result.user!!.id.toString(),
                server = baseUrl,
                token = result.accessToken!!,
                username = result.user!!.name!!,
                serverName = serverPublicSystemInfo.serverName ?: "Unknown Server",
                library = HashMap()
            )
            logcat(LogPriority.INFO) {
                "login(): auth success with userDto = ${result.user}"
            }
            ObjectBox.credentialBox.put(cred)
            withCredential(cred)
            return@with cred
        } catch (err: InvalidStatusException) {
            logcat(LogPriority.ERROR) {
                "login(): auth failed $err"
            }
            throw AuthorizationException(err.message)
        }
    }

    suspend fun verifySavedCredential(credential: Credential) {
        try {
            val currentUser = withCredential(credential).userApi.getCurrentUser().content
            logcat(LogPriority.INFO) { "verifySavedCredential: success, user=${currentUser}" }
        } catch (e: IllegalStateException) {
            throw AuthorizationException("auth failed with saved credential ${e.message}")
        }
    }


    suspend fun getUserViews(credential: Credential): List<MediaLibraryListItem> {
        val l =
            withCredential(credential).userViewsApi.getUserViews().content.items?.mapTo(ArrayList()) {
                MediaLibraryListItem(false, it.name ?: "", it.id.toString())
            } ?: ArrayList()

        val libraries = ObjectBox.getCredentialByUid(credential.uid).library
        return l.map {
            it.checked = libraries.contains(it.id)
            it
        }
    }


    private suspend fun apiQueryAudioItems(
        parentId: String, startIndex: Int = 0, limit: Int = 100, credential: Credential
    ): BaseItemDtoQueryResult? {
        return try {
            withContext(Dispatchers.IO) {
                withCredential(credential).itemsApi.getItemsByUserId(
                    sortBy = listOf("SortName"),
                    sortOrder = setOf(SortOrder.ASCENDING),
                    includeItemTypes = setOf(BaseItemKind.AUDIO),
                    recursive = true,
                    fields = setOf(
                        ItemFields.DATE_CREATED,
                        ItemFields.SORT_NAME,
                        ItemFields.MEDIA_STREAMS,
                        ItemFields.MEDIA_SOURCES
                    ),
                    startIndex = startIndex,
                    imageTypeLimit = 1,
                    enableImageTypes = setOf(ImageType.PRIMARY),
                    limit = limit,
                    parentId = UUID.fromString(parentId)
                ).content
            }
        } catch (e: Exception) {
            logcat { "Error querying Jellyfin API: ${e.message}" }
            null
        }
    }

    suspend fun queryApiForLibraries(
        libraries: List<MediaLibraryListItem>,
        batchSize: Int = 1000,
        onProgress: (Int) -> Unit,
        onMessage: (String) -> Unit,
        credential: Credential
    ) {

        val queryTotal = libraries.associateWith {
            apiQueryAudioItems(it.id, 0, 0, credential)?.totalRecordCount ?: 0
        }

        val totalSum = queryTotal.values.sum()
        var proceed = 0


        onMessage("Total $totalSum need to fetch...")
        queryTotal.forEach { (lib, total) ->
            onMessage("Cleaning database for ${lib.name}.")
            val removed = ObjectBox.removeAllVirtualFileByLibraryId(lib.id)
            onMessage("Database cleared for ${lib.name}, $removed rows removed.")
            onMessage("Querying api for media library ${lib.name} ...")
            fetchItemsInBatches(
                batchSize = batchSize,
                totalItems = total,
                libId = lib.id,
                credential = credential,
                onProgress = {
                    proceed += it
                    onProgress(100 * proceed / totalSum)
                    onMessage("${lib.name}: got $it from query, total=$proceed")
                },
                onFetch = {
                    val vfs = it.map { dto -> toVirtualFile(dto, lib.id, credential) }
                    ObjectBox.virtualFileBox.put(vfs)
                    //TODO: update cache info
                })
        }
    }

    private suspend fun fetchItemsInBatches(
        batchSize: Int,
        totalItems: Int,
        libId: String,
        onProgress: (Int) -> Unit,
        onFetch: (List<BaseItemDto>) -> Unit,
        credential: Credential
    ) {
        val numberOfBatches = (totalItems + batchSize - 1) / batchSize

        for (batch in 0 until numberOfBatches) {
            val startIndex = batch * batchSize
            val items = apiQueryAudioItems(libId, startIndex, batchSize, credential)?.items

            if (items != null) {
                onFetch(items)
                onProgress(items.size)
            } else {
                break
            }
        }
    }

    private fun toVirtualFile(it: BaseItemDto, libId: String, credential: Credential): VirtualFile {
        val ms = it.mediaSources?.first()!!
        var flag = 0
        if (it.albumId != null)
            flag = flag or Document.FLAG_SUPPORTS_THUMBNAIL
        val vf = VirtualFile(
            documentId = it.id.toString(),
            mimeType = getMimeTypeFromExtension(ms.container!!)!!,
            displayName = "${ms.name}.${ms.container}",
            lastModified = 1000 * it.dateCreated?.toEpochSecond(ZoneOffset.UTC)!!,
            flags = flag,
            size = ms.size ?: 0,
            libId = libId,
            uid = credential.uid,
            duration = (it.runTimeTicks ?: 0) / 10000,
            year = it.productionYear ?: -1,
            title = it.name!!,
            album = it.album ?: "",
            track = it.indexNumber ?: 0,
            artist = it.artists?.joinToString(", ") ?: "",
            bitrate = it.mediaSources?.first()?.bitrate ?: 0,
            albumId = it.albumId.toString(),
            albumCoverTag = it.albumPrimaryImageTag,
            credentialId = credential.id,
        )

        vf.credential.target = credential
        return vf
    }

    private val mimeTypeCache = HashMap<String, String>()
    private fun getMimeTypeFromExtension(extension: String): String? {
        return mimeTypeCache.getOrPut(extension) {
            // Get the MIME type for the extension using the MimeTypeMap class
            val mimeTypeMap = MimeTypeMap.getSingleton()
            return mimeTypeMap.getMimeTypeFromExtension(extension)
        }
    }


    fun resolveFileURL(vf: VirtualFile): String =
        withCredential(vf).libraryApi.getFileUrl(
            UUID.fromString(vf.documentId),
            includeCredentials = true
        )

    fun resolveThumbnailURL(vf: VirtualFile, sizeHint: Point?): String? {
        if (vf.albumCoverTag == null) return null
        return withCredential(vf).imageApi.getItemImageUrl(
            UUID.fromString(vf.albumId),
            ImageType.PRIMARY,
            tag = vf.albumCoverTag,
            height = sizeHint?.y,
            width = sizeHint?.x,
            format = ImageFormat.WEBP
        )
    }

    private fun withCredential(vf: VirtualFile) = withCredential(vf.credential.target)

    private fun withCredential(credential: Credential): ApiClient {
        api.accessToken = credential.token
        api.baseUrl = credential.server
        api.userId = UUID.fromString(credential.uid)
        return api
    }
}