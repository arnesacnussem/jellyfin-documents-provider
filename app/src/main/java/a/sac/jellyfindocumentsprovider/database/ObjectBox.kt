package a.sac.jellyfindocumentsprovider.database

import a.sac.jellyfindocumentsprovider.database.entities.Credential
import a.sac.jellyfindocumentsprovider.database.entities.Credential_
import a.sac.jellyfindocumentsprovider.database.entities.MyObjectBox
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile
import a.sac.jellyfindocumentsprovider.database.entities.VirtualFile_
import android.content.Context
import android.util.Log
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.android.Admin
import io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE


object ObjectBox {
    private lateinit var store: BoxStore
    lateinit var credentialBox: Box<Credential>
        private set

    lateinit var virtualFileBox: Box<VirtualFile>

    fun init(context: Context) {
        store = MyObjectBox.builder().androidContext(context.applicationContext).build()
        credentialBox = store.boxFor(Credential::class.java)
        virtualFileBox = store.boxFor(VirtualFile::class.java)

        val started = Admin(store).start(context)
        Log.i("ObjectBoxAdmin", "Started: $started")
    }

    fun getVirtualFileByDocId(documentId: String) =
        virtualFileBox.query().equal(VirtualFile_.documentId, documentId, CASE_SENSITIVE).build()
            .use { it.findFirst()!! }

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