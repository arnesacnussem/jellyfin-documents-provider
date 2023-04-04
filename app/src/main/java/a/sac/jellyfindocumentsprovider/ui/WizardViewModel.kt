package a.sac.jellyfindocumentsprovider.ui

import a.sac.jellyfindocumentsprovider.MediaLibraryListItem
import a.sac.jellyfindocumentsprovider.ServerInfo
import a.sac.jellyfindocumentsprovider.TAG
import a.sac.jellyfindocumentsprovider.database.entities.Credential
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val msgJoin = ObservableField("whatever")
    private val _message = ArrayList<String>()

    fun clearMessage() {
        _message.clear()
    }

    fun onMessage(msg: String) {
        if (_message.size > 13) _message.removeAt(0)
        _message.add(msg)
        msgJoin.set(_message.joinToString("\n"))
    }

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
            Log.e(TAG, "testConnection: failed", e)
            false
        }
        btnLoading.set(false)
        serverInfoValid.set(valid)
    }

    fun onServerInfoChanged() {
        serverInfoValid.set(false)
    }
}