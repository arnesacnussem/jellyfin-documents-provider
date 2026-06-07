package arne.jellyfindocumentsprovider.ui.serverWizard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun ServerInfoScreen(
    viewModel: ServerWizardViewModel = viewModel(),
    next: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var passwordVisible by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val quickConnectCode by viewModel.quickConnectCode.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(url, username, password) {
        viewModel.markServerInvalid()
    }

    var hasAutoNavigated by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state == ServerWizardViewModel.State.QUICK_CONNECT_DONE && !hasAutoNavigated) {
            hasAutoNavigated = true
            next()
        }
    }

    Column {
        if (state == ServerWizardViewModel.State.WAITING_QUICK_CONNECT) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = quickConnectCode,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Text(
                    text = "Enter this code on your Jellyfin server",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { viewModel.cancelQuickConnect() }) {
                    Text("Cancel")
                }
            }
        } else {
            OutlinedTextField(
                value = url,
                onValueChange = { viewModel.url.value = it },
                label = { Text("Server URL") },
                placeholder = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.username.value = it },
                label = { Text("Username") },
                placeholder = { Text("Username or leave blank to use token") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.password.value = it },
                label = { Text("Password") },
                placeholder = { Text("Password or token") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )
        }

        if (state != ServerWizardViewModel.State.WAITING_QUICK_CONNECT && state != ServerWizardViewModel.State.QUICK_CONNECT_DONE) {
            val isLoading = state == ServerWizardViewModel.State.VALIDATING_SERVER
            val isReady = state.ordinal >= ServerWizardViewModel.State.VALID_SERVER.ordinal

            if (isReady) {
                Button(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    onClick = { next() }
                ) { Text("Next") }
            } else {
                Row(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                        onClick = {
                            coroutineScope.launch { viewModel.testServer() }
                        }
                    ) {
                        Box(modifier = Modifier.animateContentSize()) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 4.dp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            } else {
                                Text("Test Server")
                            }
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = url.isNotBlank(),
                        onClick = {
                            coroutineScope.launch { viewModel.initiateQuickConnect() }
                        }
                    ) { Text("Quick Connect") }
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
