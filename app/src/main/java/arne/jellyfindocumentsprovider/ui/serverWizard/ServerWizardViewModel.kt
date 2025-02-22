package arne.jellyfindocumentsprovider.ui.serverWizard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import arne.jellyfindocumentsprovider.ServerWizardActivity
import arne.jellyfindocumentsprovider.ui.serverWizard.ServerWizardViewModel.Library.Companion.toLibrary
import arne.jellyfindocumentsprovider.vfs.JellyfinAccessor.ServerInfo
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.vfs.ObjectBox
import arne.jellyfindocumentsprovider.vfs.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import org.jellyfin.sdk.model.api.BaseItemDto

class ServerWizardViewModel(application: Application) : AndroidViewModel(application) {
    val url = MutableStateFlow("")
    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    private val _state = MutableStateFlow(State.INVALID_SERVER)
    val state: StateFlow<State>
        get() = _state

    private val _libraries = MutableStateFlow<MutableList<Library>>(mutableListOf())
    val libraries: StateFlow<List<Library>>
        get() = _libraries

    fun toggleLibraryChecked(id: String) {
        _libraries.value = _libraries.value.map {
            if (it.id == id) {
                it.copy(checked = !it.checked)
            } else {
                it
            }
        }.toMutableList()
    }

    private var server: JellyfinServer? = null

    fun markServerInvalid() {
        _state.value = State.INVALID_SERVER
    }

    suspend fun testServer() {
        _state.value = State.VALIDATING_SERVER
        withContext(Dispatchers.IO) {
            try {
                server =
                    ServerInfo(url.value, username.value, password.value)
                        .login(getApplication())
                _state.value = State.VALID_SERVER
            } catch (e: Exception) {
                _state.value = State.INVALID_SERVER
            }
        }
    }

    suspend fun loadLibraries() {
        _state.value = State.LOADING_LIBRARY
        withContext(Dispatchers.IO) {
            try {
                val libraries = server!!.asAccessor(getApplication()).libraries()
                _libraries.value = (libraries?.map {
                    it.toLibrary()
                } ?: emptyList()).toMutableList()
                _state.value =
                    (if (_libraries.value.isEmpty()) State.EMPTY_LIBRARY else State.LOADED_LIBRARY)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Error loading libraries: ${e.stackTraceToString()}"
                }
                _state.value = State.INVALID_LIBRARY
            }
        }
    }

    fun save(libraries: List<Library>, activityCtx: Context) {
        val cred = server!!.copy(
            library = libraries.filter { it.checked }.associate { it.id to it.name }
        )
        ObjectBox.server.put(cred)
        (activityCtx as? ServerWizardActivity)?.finish()
    }

    data class Library(
        val name: String,
        val id: String,
        var checked: Boolean = false,
        var type: String? = null,
    ) {
        companion object {
            fun BaseItemDto.toLibrary(): Library {
                return Library(name ?: "Unknown", id.asString(), type = collectionType?.name)
            }
        }
    }

    enum class State {
        INVALID_SERVER,
        VALIDATING_SERVER,
        VALID_SERVER,
        LOADING_LIBRARY,
        EMPTY_LIBRARY,
        LOADED_LIBRARY,
        INVALID_LIBRARY
    }

}