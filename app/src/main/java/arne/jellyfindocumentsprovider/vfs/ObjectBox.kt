package arne.jellyfindocumentsprovider.vfs

import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore
import logcat.LogPriority
import logcat.logcat


object ObjectBox {
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
    lateinit var lyricsCache: Box<LyricsCache>
        private set

    fun init(context: Context) {
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
        try {
            val adminClass = Class.forName("io.objectbox.android.Admin")
            val admin = adminClass.getConstructor(BoxStore::class.java).newInstance(store)
            val started = adminClass.getMethod("start", Context::class.java).invoke(admin, context)
            logcat("ObjectBoxAdmin", LogPriority.INFO) { "Started: $started" }
        } catch (_: ClassNotFoundException) { }

        server = store.boxFor(JellyfinServer::class.java)
        virtualFile = store.boxFor(VirtualFile::class.java)
        albumInfo = store.boxFor(AlbumInfo::class.java)
        cacheInfo = store.boxFor(CacheInfo::class.java)
        thumbCache = store.boxFor(ThumbCache::class.java)
        lyricsCache = store.boxFor(LyricsCache::class.java)
    }
}