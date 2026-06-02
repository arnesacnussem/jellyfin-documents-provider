package arne.jellyfindocumentsprovider.data

import android.content.Context
import android.util.Log
import arne.jellyfindocumentsprovider.vfs.AlbumInfo
import arne.jellyfindocumentsprovider.vfs.MyObjectBox
import arne.jellyfindocumentsprovider.vfs.CacheInfo
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.ThumbCache
import arne.jellyfindocumentsprovider.vfs.VirtualFile
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.android.Admin

object ObjectBoxStore {
    lateinit var store: BoxStore
        private set

    lateinit var server: Box<JellyfinServer>
        private set
    lateinit var virtualFile: Box<VirtualFile>
        private set
    lateinit var albumInfo: Box<AlbumInfo>
        private set
    lateinit var cacheInfo: Box<CacheInfo>
        private set
    lateinit var thumbCache: Box<ThumbCache>
        private set

    fun init(context: Context) {
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
        val started = Admin(store).start(context)
        Log.i("ObjectBoxStore", "Started: $started")

        server = store.boxFor(JellyfinServer::class.java)
        virtualFile = store.boxFor(VirtualFile::class.java)
        albumInfo = store.boxFor(AlbumInfo::class.java)
        cacheInfo = store.boxFor(CacheInfo::class.java)
        thumbCache = store.boxFor(ThumbCache::class.java)
    }
}
