package arne.jellyfindocumentsprovider.ui.main

import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import arne.jellyfindocumentsprovider.hacks.toPropertyMap
import arne.jellyfindocumentsprovider.vfs.DatabaseSyncWorker
import arne.jellyfindocumentsprovider.vfs.DatabaseSyncWorker.SyncRequest
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.countByServer
import arne.jellyfindocumentsprovider.ui.components.ServerListEntryInfo
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import logcat.logcat

class AppViewModel : ViewModel() {
    private var _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean>
        get() = _loading
    private val _servers = MutableStateFlow<List<ServerListEntryInfo>>(mutableListOf())
    val servers: StateFlow<List<ServerListEntryInfo>>
        get() = _servers

    private var _sync = MutableStateFlow<OneTimeWorkRequest?>(null)
    val sync: StateFlow<OneTimeWorkRequest?>
        get() = _sync

    private val _progressRec = mutableMapOf<String, Int>()
    private var _progress = MutableStateFlow<Map<String, Int>>(mapOf())
    val progress: StateFlow<Map<String, Int>>
        get() = _progress


    fun updateServerList() {
        _loading.value = true
        _servers.value = ObjectBox.server.all.map {
            ServerListEntryInfo(
                db = it.id,
                name = it.serverName,
                url = it.url,
                user = it.username,
                id = it.uuid,
                itemCount = ObjectBox.virtualFile.countByServer(it.id),
                libCount = it.library.size
            )
        }
        _loading.value = false
    }

    @Synchronized
    fun WorkManager.requestSync(info: ServerListEntryInfo?) {
        if (_sync.value != null) {
            logcat {
                "Sync already in progress"
            }
            return
        }
        _sync.value = OneTimeWorkRequestBuilder<DatabaseSyncWorker>()
            .setInputData(
                workDataOf(
                    *SyncRequest(
                        listOfNotNull(info?.db).toTypedArray(),
                        all = info == null
                    ).toPropertyMap().toList().toTypedArray()
                )
            )
            .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
            .build()
        this.enqueue(_sync.value!!)
    }

    suspend fun WorkManager.observeProgress() {
        if (_sync.value == null) {
            _progress.value = mapOf()
            return
        }
        getWorkInfoByIdFlow(_sync.value!!.id).collect {
            @Suppress("UNCHECKED_CAST")
            val workProgress = it?.progress?.keyValueMap as Map<String, Int>
            _progressRec.putAll(workProgress)
            _progress.value = _progressRec.toMap()

            if (it.state.isFinished) {
                _sync.value = null
            }
        }
    }

    fun deleteServer(info: ServerListEntryInfo) {
        ObjectBox.server.remove(info.db)
        updateServerList()
    }
}