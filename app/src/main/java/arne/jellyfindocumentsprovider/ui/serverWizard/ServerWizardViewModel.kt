package arne.jellyfindocumentsprovider.ui.serverWizard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import arne.jellyfindocumentsprovider.ServerWizardActivity
import arne.jellyfindocumentsprovider.ui.serverWizard.ServerWizardViewModel.Library.Companion.toLibrary
import arne.jellyfindocumentsprovider.vfs.JellyfinAccessor
import arne.jellyfindocumentsprovider.vfs.JellyfinAccessor.ServerInfo
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.data.AppDependencies
import arne.jellyfindocumentsprovider.vfs.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.model.api.BaseItemDto
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ServerWizardViewModel(application: Application) : AndroidViewModel(application) {
    val url = MutableStateFlow("")
    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    val quickConnectCode = MutableStateFlow("")
    val statusMessage = MutableStateFlow("")

    private var quickConnectSecret = ""
    private var pollingJob: Job? = null

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
        statusMessage.value = ""
        cancelPolling()
    }

    suspend fun initiateQuickConnect() {
        cancelPolling()
        statusMessage.value = ""
        _state.value = State.WAITING_QUICK_CONNECT
        withContext(Dispatchers.IO) {
            try {
                val result = ServerInfo(url.value).initiateQuickConnect(getApplication())
                quickConnectCode.value = result.code
                quickConnectSecret = result.secret
                startPolling()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Quick Connect initiation failed: ${e.stackTraceToString()}"
                }
                statusMessage.value = "Quick Connect failed: ${e.toUserMessage()}"
                _state.value = State.INVALID_SERVER
            }
        }
    }

    fun cancelQuickConnect() {
        cancelPolling()
        quickConnectCode.value = ""
        quickConnectSecret = ""
        statusMessage.value = ""
        _state.value = State.INVALID_SERVER
    }

    private fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            try {
                val api = JellyfinAccessor.createJellyfin(getApplication()).createApi(baseUrl = url.value)
                while (true) {
                    delay(2000)
                    val stateResult by api.quickConnectApi.getQuickConnectState(quickConnectSecret)
                    if (stateResult.authenticated) {
                        val jellyfinServer = ServerInfo(url.value)
                            .authenticateQuickConnect(getApplication(), quickConnectSecret)
                        server = jellyfinServer
                        statusMessage.value = ""
                        _state.value = State.QUICK_CONNECT_DONE
                        return@launch
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Quick Connect polling error: ${e.stackTraceToString()}"
                }
                statusMessage.value = "Quick Connect failed: ${e.toUserMessage()}"
                _state.value = State.INVALID_SERVER
            }
        }
    }

    suspend fun testServer() {
        if (username.value.isBlank() && password.value.isBlank()) {
            statusMessage.value = "Username or password required"
            return
        }
        statusMessage.value = ""
        _state.value = State.VALIDATING_SERVER
        withContext(Dispatchers.IO) {
            try {
                server =
                    ServerInfo(url.value, username.value, password.value)
                        .login(getApplication())
                _state.value = State.VALID_SERVER
            } catch (e: Exception) {
                statusMessage.value = "Connection failed: ${e.toUserMessage()}"
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
        AppDependencies.repos.server.put(cred)
        (activityCtx as? ServerWizardActivity)?.finish()
    }

    override fun onCleared() {
        super.onCleared()
        cancelPolling()
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
        WAITING_QUICK_CONNECT,
        QUICK_CONNECT_DONE,
        VALID_SERVER,
        LOADING_LIBRARY,
        EMPTY_LIBRARY,
        LOADED_LIBRARY,
        INVALID_LIBRARY,
    }

    companion object {
        private fun knownError(e: Throwable): String? = when (e) {
            is InvalidStatusException -> when (e.status) {
                401 -> "Invalid username or password"
                in 400..499 -> "Server error (HTTP ${e.status})"
                in 500..599 -> "Server internal error (HTTP ${e.status})"
                else -> "Unexpected response (HTTP ${e.status})"
            }
            is TimeoutException -> "Server not reachable (timeout)"
            is SecureConnectionException -> "SSL/TLS connection failed"
            is InvalidContentException -> "Invalid server response"
            is UnknownHostException -> "Cannot resolve server address"
            is ConnectException -> "Connection refused by server"
            is SocketTimeoutException -> "Connection timed out"
            else -> null
        }

        private fun Exception.toUserMessage(): String {
            knownError(this)?.let { return it }
            var c = cause
            while (c != null) {
                knownError(c)?.let { return it }
                c = c.cause
            }
            return "${javaClass.simpleName}: ${message ?: "Unknown error"}"
        }
    }
}