package a.sac.jellyfindocumentsprovider.jellyfin

import a.sac.jellyfindocumentsprovider.MediaInfo
import a.sac.jellyfindocumentsprovider.MediaLibraryListItem
import a.sac.jellyfindocumentsprovider.ServerInfo
import a.sac.jellyfindocumentsprovider.TAG
import a.sac.jellyfindocumentsprovider.database.AppDatabase
import a.sac.jellyfindocumentsprovider.database.entities.Credential
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val db = AppDatabase.getDatabase(ctx)


    @Throws(AuthorizationException::class, IllegalArgumentException::class)
    suspend fun login(info: ServerInfo) = with(info) {
        if (api.accessToken?.isNotBlank() == true) {
            return@with null
        }
        if (baseUrl.isBlank()) {
            throw IllegalArgumentException("The baseUrl must not leave blank!")
        }
        api.baseUrl = baseUrl



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
            Log.i(TAG, "login: success uid=${result.user!!.id}")
            db.credentialDao().insert(cred)
            updateApiCredential(cred)
            return@with cred
        } catch (err: InvalidStatusException) {
            if (err.status == 401) {
                // Username or password is incorrect
                Log.e(TAG, "login: Invalid user")
            } else {
                Log.e(TAG, "login: Auth failed")
            }
            throw AuthorizationException(err.message)
        }
    }

    suspend fun verifySavedCredential(credential: Credential) {
        updateApiCredential(credential)
        try {
            val currentUser = api.userApi.getCurrentUser().content
            Log.i(TAG, "verifySavedCredential: success, user=${currentUser}")
        } catch (e: IllegalStateException) {
            throw AuthorizationException("auth failed with saved credential ${e.message}")
        }
    }

    private fun updateApiCredential(credential: Credential) {
        api.accessToken = credential.token
        api.baseUrl = credential.server
        api.userId = UUID.fromString(credential.uid)
    }

    suspend fun getUserViews(): List<MediaLibraryListItem> {
        val l = api.userViewsApi.getUserViews().content.items?.mapTo(ArrayList()) {
            MediaLibraryListItem(false, it.name ?: "", it.id.toString())
        } ?: ArrayList()

        val (_, _, _, _, _, libraries) = db.credentialDao().getByUid(api.userId.toString())
        return l.map {
            it.checked = libraries.contains(it.id)
            it
        }
    }


    suspend fun apiQueryAudioItems(
        parentId: String, startIndex: Int = 0, limit: Int = 100
    ): BaseItemDtoQueryResult? {
        return try {
            withContext(Dispatchers.IO) {
                api.itemsApi.getItemsByUserId(
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
            println("Error querying Jellyfin API: ${e.message}")
            null
        }
    }

    suspend fun queryApiForLibraries(
        libraries: List<MediaLibraryListItem>,
        batchSize: Int = 1000,
        onProgress: (Int) -> Unit,
        onMessage: (String) -> Unit
    ) {

        val queryTotal = libraries.associateWith {
            apiQueryAudioItems(it.id, 0, 0)?.totalRecordCount ?: 0
        }

        val totalSum = queryTotal.values.sum()
        var proceed = 0


        onMessage("Total $totalSum need to fetch...")
        queryTotal.forEach { (lib, total) ->
            onMessage("Cleaning database for ${lib.name}.")
            db.virtualFileDao().deleteAllByLibraryId(lib.id)
            onMessage("Database cleared for ${lib.name}")
            onMessage("Querying api for media library ${lib.name} ...")
            fetchItemsInBatches(batchSize = batchSize,
                totalItems = total,
                libId = lib.id,
                onProgress = {
                    proceed += it
                    onProgress(100 * proceed / totalSum)
                    onMessage("${lib.name}: got $it from query, total=$proceed")
                },
                onFetch = {
                    val uid = api.userId.toString()
                    val vfs = it.map { dto -> toVirtualFile(dto, lib.id, uid) }
                    db.virtualFileDao().insertAll(vfs)
                })
        }
    }

    private suspend fun fetchItemsInBatches(
        batchSize: Int,
        totalItems: Int,
        libId: String,
        onProgress: (Int) -> Unit,
        onFetch: (List<BaseItemDto>) -> Unit
    ) {
        val numberOfBatches = (totalItems + batchSize - 1) / batchSize

        for (batch in 0 until numberOfBatches) {
            val startIndex = batch * batchSize
            val items = apiQueryAudioItems(libId, startIndex, batchSize)?.items

            if (items != null) {
                onFetch(items)
                onProgress(items.size)
            } else {
                break
            }
        }
    }


    private fun toVirtualFile(it: BaseItemDto, lid: String, uid: String): VirtualFile {
        val ms = it.mediaSources?.first()!!
        var flag = 0
        if (it.albumPrimaryImageTag != null)
            flag = flag or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        return VirtualFile(
            id = it.id.toString(),
            mimeType = getMimeTypeFromExtension(ms.container!!)!!,
            displayName = "${ms.name}.${ms.container}",
            lastModified = 1000 * it.dateCreated?.toEpochSecond(ZoneOffset.UTC)!!,
            flags = flag,
            size = ms.size ?: 0,
            lid = lid,
            uid = uid,
            mediaInfo = MediaInfo(
                duration = (it.runTimeTicks ?: 0) / 10000,
                year = it.productionYear ?: -1,
                title = it.name!!,
                album = it.album ?: "",
                track = it.indexNumber ?: 0,
                artist = it.artists?.joinToString(", ") ?: "",
                bitrate = ms.bitrate ?: 0,
                thumbnail = it.albumPrimaryImageTag
            )
        )
    }

    private val mimeTypeCache = HashMap<String, String>()
    private fun getMimeTypeFromExtension(extension: String): String? {
        return mimeTypeCache.getOrPut(extension) {
            // Get the MIME type for the extension using the MimeTypeMap class
            val mimeTypeMap = MimeTypeMap.getSingleton()
            return mimeTypeMap.getMimeTypeFromExtension(extension)
        }
    }


    fun resolveStreamingURL(vf: VirtualFile): String {
        loadCredentialForFile(vf)
        return api.libraryApi.getFileUrl(UUID.fromString(vf.id), includeCredentials = true)
//        return api.audioApi.getAudioStreamUrl(UUID.fromString(vf.id), static = true)
    }

    fun resolveThumbnailURL(vf: VirtualFile): String {
        loadCredentialForFile(vf)
        return api.imageApi.getItemImageUrl(
            UUID.fromString(vf.id),
            ImageType.PRIMARY
        )
    }

    private fun loadCredentialForFile(vf: VirtualFile) {
        val credential = db.credentialDao().getByUid(vf.uid)
        updateApiCredential(credential)
    }
}