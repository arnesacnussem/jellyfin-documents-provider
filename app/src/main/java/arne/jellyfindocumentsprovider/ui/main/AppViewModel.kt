package arne.jellyfindocumentsprovider.ui.main

import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.Data
import arne.jellyfindocumentsprovider.data.AppDependencies
import arne.jellyfindocumentsprovider.ui.components.ServerListEntryInfo
import arne.jellyfindocumentsprovider.vfs.DatabaseSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import logcat.logcat

class AppViewModel : ViewModel() {
    private val _servers = MutableStateFlow<List<ServerListEntryInfo>>(mutableListOf())
    val servers: StateFlow<List<ServerListEntryInfo>>
        get() = _servers

    private var _sync = MutableStateFlow<OneTimeWorkRequest?>(null)
    val sync: StateFlow<OneTimeWorkRequest?>
        get() = _sync

    private val _progressRec = mutableMapOf<String, Int>()
    private var _progress = MutableStateFlow<Int>(-1)
    val progress: StateFlow<Int>
        get() = _progress

    fun updateServerList() {
        _servers.value = AppDependencies.repos.server.findAll().map {
            ServerListEntryInfo(
                db = it.id,
                name = it.serverName,
                url = it.url,
                user = it.username,
                id = it.uuid,
                itemCount = AppDependencies.repos.virtualFile.countByServerId(it.id),
                libCount = it.library.size
            )
        }
    }

    @Synchronized
    fun WorkManager.requestSync() {
        if (_sync.value != null) {
            logcat {
                "Sync already in progress"
            }
            return
        }
        _sync.value = OneTimeWorkRequestBuilder<DatabaseSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
            .build()
        this.enqueue(_sync.value!!)
    }

    @Synchronized
    fun WorkManager.requestFavoritesSync() {
        if (_sync.value != null) {
            logcat { "Sync already in progress" }
            return
        }
        _sync.value = OneTimeWorkRequestBuilder<DatabaseSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.DROP_WORK_REQUEST)
            .setInputData(Data.Builder().putBoolean("favorites_only", true).build())
            .build()
        this.enqueue(_sync.value!!)
    }

    suspend fun WorkManager.observeProgress() {
        if (_sync.value == null) {
            _progress.value = -1
            return
        }
        getWorkInfoByIdFlow(_sync.value!!.id).collect {
            @Suppress("UNCHECKED_CAST")
            val workProgress = it?.progress?.keyValueMap as Map<String, Int>
            // TODO: fix progress indicator
            _progressRec.putAll(workProgress)
            _progress.value = 1

            if (it.state.isFinished) {
                _sync.value = null
                _progress.value = -1
                updateServerList()
            }
        }
    }

    fun deleteServer(info: ServerListEntryInfo) {
        AppDependencies.repos.server.removeById(info.db)
        updateServerList()
    }
}