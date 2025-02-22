package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import arne.jellyfindocumentsprovider.common.useNav
import arne.jellyfindocumentsprovider.ui.components.LinearProgressIndicator
import arne.jellyfindocumentsprovider.ui.components.ServerItem
import arne.jellyfindocumentsprovider.ui.components.ServerListEntryInfo

@Composable
fun HomeScreen(
    vm: AppViewModel = viewModel()
) {
    val nav = useNav()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<ServerListEntryInfo?>(null) }
    val workManager = remember { WorkManager.getInstance(context) }
    val servers by vm.servers.collectAsState()
    val sync by vm.sync.collectAsState()
    val progress by vm.progress.collectAsState()

    LaunchedEffect(sync) {
        with(vm) {
            workManager.observeProgress()
        }
    }

    LaunchedEffect(lifecycleState) {
        when (lifecycleState) {
            Lifecycle.State.RESUMED -> {
                vm.updateServerList()
            }

            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        servers.map { s ->
            ServerItem(
                info = s,
                sync = {
                    with(vm) {
                        workManager.requestSync(s)
                    }
                },
                delete = {
                    showDeleteConfirm = s
                },
                onClick = {
                    nav {
                        navigate("server-setting/${s.db}")
                    }
                },
                progressBar = {
                    val p = progress[s.id] ?: return@ServerItem
                    if (p == -1 || p == 100)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    else
                        LinearProgressIndicator(p / 100f)
                }
            )
        }
        if (servers.isEmpty()) Text("No servers found")
    }

    if (showDeleteConfirm != null) AlertDialog(onDismissRequest = { showDeleteConfirm = null },
        confirmButton = {
            TextButton(onClick = {
                vm.deleteServer(showDeleteConfirm!!)
                showDeleteConfirm = null
            }) {
                Text("Yes!")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteConfirm = null }) {
                Text("No!")
            }
        },
        text = {
            Column {
                Text("Delete server?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(showDeleteConfirm!!.name)
                Text(showDeleteConfirm!!.url)
            }
        })
}
