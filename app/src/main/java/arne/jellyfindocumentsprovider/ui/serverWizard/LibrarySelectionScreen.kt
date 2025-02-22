package arne.jellyfindocumentsprovider.ui.serverWizard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import arne.jellyfindocumentsprovider.ui.serverWizard.ServerWizardViewModel.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.UUID

@Preview
@Composable
fun LibrarySelectionScreen(viewModel: ServerWizardViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            viewModel.loadLibraries()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (state != State.LOADED_LIBRARY) Alignment.Center else Alignment.TopCenter
    ) {

        when (state) {
            State.LOADING_LIBRARY -> {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(
                            Alignment.CenterHorizontally
                        )
                    )
                    Text(
                        "Loading libraries ...", modifier = Modifier.align(
                            Alignment.CenterHorizontally
                        )
                    )
                }
            }

            State.EMPTY_LIBRARY -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                ) {
                    Text(
                        "\\(o_o)/",
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .align(Alignment.CenterHorizontally),
                        fontSize = 64.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "No libraries found",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            State.LOADED_LIBRARY -> {
                Column {
                    libraries.map {
                        LibraryItem(
                            it,
                            onClick = {
                                viewModel.toggleLibraryChecked(it.id)
                            },
                        )
                    }

                    Button(
                        modifier = Modifier.padding(top = 24.dp),
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                viewModel.save(libraries, context)
                            }
                        }
                    ) {
                        Text("Save")
                    }
                }
            }

            else -> {
                Text("Unknown state: $state")
            }
        }
    }
}


@Composable
@Preview
fun LibraryItem(
    @PreviewParameter(LibraryProvider::class) library: ServerWizardViewModel.Library,
    onClick: () -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .border(
                width = if (library.checked) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CardDefaults.elevatedShape,
            ),
        onClick = onClick,
        shape = CardDefaults.elevatedShape,
    ) {
        ListItem(headlineContent = { Text("${library.name} (${library.type ?: "Unknown Type"})") },
            supportingContent = {
                Column {
                    Text("ID: ${library.id}")
                }
            })
    }
}

private class LibraryProvider : PreviewParameterProvider<ServerWizardViewModel.Library> {
    override val values: Sequence<ServerWizardViewModel.Library>
        get() = sequenceOf(
            ServerWizardViewModel.Library("Library 1", UUID.randomUUID().toString()),
            ServerWizardViewModel.Library(
                "Library 2", UUID.randomUUID().toString(), checked = true
            ),
            ServerWizardViewModel.Library("Library 3", UUID.randomUUID().toString()),
        )
}