package a.sac.jellyfindocumentsprovider.ui

import a.sac.jellyfindocumentsprovider.database.entities.Credential
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import a.sac.jellyfindocumentsprovider.utils.FixedCapacityList
import a.sac.jellyfindocumentsprovider.utils.logcat
import android.app.Application
import android.widget.Toast
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val jellyfinProvider: JellyfinProvider, private val application: Application
) : AndroidViewModel(application) {
    val serverInfo = ObservableField(ServerInfo())
    val libraryInfo = ObservableArrayList<MediaLibraryListItem>()
    val serverInfoValid = ObservableField(false)
    val btnLoading = ObservableField(false)
    var currentUser: Credential? = null
    val progress = ObservableInt()
    val message = FixedCapacityList<String>(50)

    suspend fun testConnection() {
        btnLoading.set(true)
        val valid = try {
            serverInfo.get()?.let {
                currentUser = jellyfinProvider.login(it)
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(application, e.message, Toast.LENGTH_SHORT).show()
            }
            logcat(LogPriority.WARN) {
                "testConnection: failed ${e.stackTraceToString()}"
            }
            false
        }
        btnLoading.set(false)
        serverInfoValid.set(valid)
    }

    fun onServerInfoChanged() {
        serverInfoValid.set(false)
    }

    data class ServerInfo(
        var baseUrl: String = "",
        var username: String = "",
        var password: String = ""
    )

    data class MediaLibraryListItem(
        var checked: Boolean,
        val name: String,
        val id: String
    )
}