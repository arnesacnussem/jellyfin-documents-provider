package arne.jellyfindocumentsprovider.data

import android.content.Context
import arne.jellyfindocumentsprovider.data.repository.ObjectBoxAlbumInfoRepository
import arne.jellyfindocumentsprovider.data.repository.ObjectBoxCacheInfoRepository
import arne.jellyfindocumentsprovider.data.repository.ObjectBoxServerRepository
import arne.jellyfindocumentsprovider.data.repository.ObjectBoxThumbCacheRepository
import arne.jellyfindocumentsprovider.data.repository.ObjectBoxVirtualFileRepository
import arne.jellyfindocumentsprovider.vfs.FilesystemService
import arne.jellyfindocumentsprovider.vfs.JellyfinApi
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.ObjectBox

object AppDependencies {
    lateinit var repos: AppRepos
    lateinit var apiFactory: (JellyfinServer) -> JellyfinApi
    lateinit var filesystemService: FilesystemService

    fun init(context: Context) {
        ObjectBox.init(context)
        repos = AppRepos(
            server = ObjectBoxServerRepository(ObjectBox.server),
            virtualFile = ObjectBoxVirtualFileRepository(ObjectBox.virtualFile),
            albumInfo = ObjectBoxAlbumInfoRepository(ObjectBox.albumInfo),
            cacheInfo = ObjectBoxCacheInfoRepository(ObjectBox.cacheInfo),
            thumbCache = ObjectBoxThumbCacheRepository(ObjectBox.thumbCache),
        )
        apiFactory = { server -> server.asAccessor(context) }
        filesystemService = FilesystemService(repos, apiFactory)
    }
}
