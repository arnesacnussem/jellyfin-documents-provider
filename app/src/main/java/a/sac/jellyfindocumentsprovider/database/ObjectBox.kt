package a.sac.jellyfindocumentsprovider.database

import a.sac.jellyfindocumentsprovider.SortedLongRangeList
import a.sac.jellyfindocumentsprovider.database.entities.CacheInfo
import a.sac.jellyfindocumentsprovider.database.entities.CacheInfo_
import a.sac.jellyfindocumentsprovider.database.entities.Credential
import a.sac.jellyfindocumentsprovider.database.entities.Credential_
import a.sac.jellyfindocumentsprovider.database.entities.MyObjectBox
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile_
import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.android.Admin
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE
import logcat.logcat
import java.io.File


object ObjectBox {
    private lateinit var store: BoxStore
    lateinit var credentialBox: Box<Credential>
        private set

    lateinit var virtualFileBox: Box<VirtualFile>
        private set

    private lateinit var cacheInfoBox: Box<CacheInfo>

    fun init(context: Context) {
        store = MyObjectBox.builder().androidContext(context.applicationContext).build()
        credentialBox = store.boxFor(Credential::class.java)
        virtualFileBox = store.boxFor(VirtualFile::class.java)
        cacheInfoBox = store.boxFor(CacheInfo::class.java)

        val started = Admin(store).start(context)
        logcat { "ObjectBox admin started: $started" }
    }

    fun getOrCreateFileCacheInfo(vf: VirtualFile, file: File): CacheInfo =
        cacheInfoBox.query {
            equal(CacheInfo_.virtualFileId, vf.id)
        }.use {
            it.findFirst() ?: createCacheInfoForDocId(vf, file)
        }

    private fun createCacheInfoForDocId(vf: VirtualFile, file: File): CacheInfo {
        val c = CacheInfo(
            id = 0,
            virtualFileId = vf.id,
            vfDocId = vf.documentId,
            isCompleted = false,
            bufferedRanges = SortedLongRangeList(),
            localPath = file.absolutePath
        )
        cacheInfoBox.attach(c)
        return c
    }

    fun setFileCacheInfo(cacheInfo: CacheInfo): CacheInfo {
        cacheInfoBox.put(cacheInfo)
        return cacheInfo
    }


    fun getVirtualFileByDocId(documentId: String) =
        virtualFileBox.query {
            equal(VirtualFile_.documentId, documentId, CASE_SENSITIVE)
        }.use { it.findFirst()!! }

    fun getCredentialByUid(uid: String) = credentialBox.query().equal(
        Credential_.uid, uid, CASE_SENSITIVE
    ).build().use {
        it.findLazyCached().first()!!
    }

    fun getAllVirtualFileByLibraryId(libId: String): Collection<VirtualFile> =
        virtualFileBox.query().equal(VirtualFile_.libId, libId, CASE_SENSITIVE).build()
            .use { it.find() }

    fun removeAllVirtualFileByLibraryId(libId: String) =
        virtualFileBox.query().equal(VirtualFile_.libId, libId, CASE_SENSITIVE).build()
            .use { it.remove() }

    fun updateLibraryPreference(uid: String, lib: Map<String, String>) {
        val credentialByUid = getCredentialByUid(uid)
        val copy = credentialByUid.copy(library = lib)
        credentialBox.put(copy)
    }
}