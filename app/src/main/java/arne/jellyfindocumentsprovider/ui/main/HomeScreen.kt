package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<ServerListEntryInfo?>(null) }
    val servers by vm.servers.collectAsState()

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
                delete = {
                    showDeleteConfirm = s
                },
                onClick = {
                    nav {
                        navigate("server-setting/${s.db}")
                    }
                },
            )
        }
        if (servers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No servers configured",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Tap + to add a Jellyfin server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showDeleteConfirm != null) AlertDialog(onDismissRequest = { showDeleteConfirm = null },
        confirmButton = {
            TextButton(onClick = {
                vm.deleteServer(showDeleteConfirm!!)
                showDeleteConfirm = null
            }, colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            )) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteConfirm = null }) {
                Text("Cancel")
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
