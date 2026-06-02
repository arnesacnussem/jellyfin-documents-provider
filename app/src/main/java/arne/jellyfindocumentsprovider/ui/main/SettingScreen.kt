package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arne.jellyfindocumentsprovider.common.BitrateLimitType
import arne.jellyfindocumentsprovider.common.BitrateLimits
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.WaveType
import arne.jellyfindocumentsprovider.common.encryptedPrefs
import arne.jellyfindocumentsprovider.common.getEnum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun SettingScreen() {
    val context = LocalContext.current
    val prefs = remember { context.encryptedPrefs() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Bitrate Limit Type", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))

        val bitrateLimitType by remember { mutableStateOf(prefs.getEnum<BitrateLimitType>(PrefKeys.BITRATE_LIMIT_TYPE)) }
        var bitrateLimitTypeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = bitrateLimitTypeExpanded, onExpandedChange = { bitrateLimitTypeExpanded = it }) {
            OutlinedTextField(
                value = bitrateLimitType.readable,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bitrateLimitTypeExpanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(expanded = bitrateLimitTypeExpanded, onDismissRequest = { bitrateLimitTypeExpanded = false }) {
                BitrateLimitType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.readable) },
                        onClick = {
                            prefs.edit().putString(PrefKeys.BITRATE_LIMIT_TYPE.name, type.name).apply()
                            bitrateLimitTypeExpanded = false
                        }
                    )
                }
            }
        }

        Text("Bitrate Limit", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        val bitrateLimit by remember { mutableStateOf(prefs.getEnum<BitrateLimits>(PrefKeys.BITRATE_LIMIT)) }
        var bitrateLimitExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = bitrateLimitExpanded, onExpandedChange = { bitrateLimitExpanded = it }) {
            OutlinedTextField(
                value = bitrateLimit.readable,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bitrateLimitExpanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(expanded = bitrateLimitExpanded, onDismissRequest = { bitrateLimitExpanded = false }) {
                BitrateLimits.entries.forEach { limit ->
                    DropdownMenuItem(
                        text = { Text(limit.readable) },
                        onClick = {
                            prefs.edit().putString(PrefKeys.BITRATE_LIMIT.name, limit.name).apply()
                            bitrateLimitExpanded = false
                        }
                    )
                }
            }
        }

        Text("Wave Type", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        val waveType by remember { mutableStateOf(prefs.getEnum<WaveType>(PrefKeys.WAVE_TYPE)) }
        var waveTypeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = waveTypeExpanded, onExpandedChange = { waveTypeExpanded = it }) {
            OutlinedTextField(
                value = waveType.description,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = waveTypeExpanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(expanded = waveTypeExpanded, onDismissRequest = { waveTypeExpanded = false }) {
                WaveType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.description) },
                        onClick = {
                            prefs.edit().putString(PrefKeys.WAVE_TYPE.name, type.name).apply()
                            waveTypeExpanded = false
                        }
                    )
                }
            }
        }
    }
}
