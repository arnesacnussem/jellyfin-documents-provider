package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arne.jellyfindocumentsprovider.common.Query.Companion.useQuery
import arne.jellyfindocumentsprovider.common.useLocalSnackbar
import arne.jellyfindocumentsprovider.common.useNav
import arne.jellyfindocumentsprovider.ui.serverWizard.LibraryItem
import arne.jellyfindocumentsprovider.ui.serverWizard.ServerWizardViewModel.Library.Companion.toLibrary
import arne.jellyfindocumentsprovider.vfs.JellyfinServer
import arne.jellyfindocumentsprovider.data.AppDependencies
import kotlinx.coroutines.launch
import logcat.logcat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun ServerSetting(id: Long = 0) {
    val credential = remember {
        AppDependencies.repos.server.findAll().firstOrNull { it.id == id }
    } ?: return
    val selection = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val nav = useNav()

    val query = useQuery(
        onLoad = { lib ->
            lib?.associate { it.id to credential.library.containsKey(it.id) }
                ?.let { selection.putAll(it) }
        }
    ) {
        credential.asAccessor(context).libraries()?.map { it.toLibrary() }
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbar = useLocalSnackbar()

    with(query) {
        Scaffold(topBar = {
            TopAppBar(title = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Server Settings - ${credential.username}@${credential.serverName}",
                        fontSize = 20.sp
                    )
                    Text(credential.url, fontSize = 16.sp)
                }
            }, navigationIcon = {
                IconButton(onClick = { nav { popBackStack() } }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }, actions = {
                IconButton(enabled = state.isSuccess, onClick = {
                    coroutineScope.launch {
                        AppDependencies.repos.server.put(credential.copy(library = if (data.isNullOrEmpty()) credential.library
                        else {
                            data.associate { it.id to it.name }.filter {
                                selection[it.key] == true
                            }.also {
                                logcat { "Selected: $it" }
                            }
                        }))
                        nav { popBackStack() }
                        snackbar {
                            showSnackbar("Saved", duration = SnackbarDuration.Long)
                        }
                    }
                }) {
                    Icon(Icons.Filled.Save, contentDescription = "Save")
                }
            })
        }) { innerPadding ->
            if (state.isLoading) LinearProgressIndicator(
                Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
            )
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {

                if (state.isSuccess) {
                    if (data.isNullOrEmpty()) Text("No libraries found")
                    else {
                        data.forEach {
                            LibraryItem(
                                it,
                                checked = selection[it.id] == true,
                                onClick = {
                                    selection[it.id] = !selection[it.id]!!
                                })
                        }
                    }
                }

                if (state.isError) Column {
                    Text("Error loading library")
                    Text(state.message ?: "Unknown Error")
                }
            }
        }
    }
}