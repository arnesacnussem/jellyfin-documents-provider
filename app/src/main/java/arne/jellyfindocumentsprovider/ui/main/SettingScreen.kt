package arne.jellyfindocumentsprovider.ui.main

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arne.jellyfindocumentsprovider.common.PowerampScanToggle
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.getEnum

@Composable
@Preview
fun SettingScreen() {
    val context = LocalContext.current
    val prefs = remember {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var powerampScanEnabled by remember {
        mutableStateOf(
            prefs.getEnum<PowerampScanToggle>(PrefKeys.POWERAMP_SCAN_ON_SYNC) == PowerampScanToggle.ENABLED
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Notify Poweramp after sync",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Automatically trigger a Poweramp media scan when sync completes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = powerampScanEnabled,
                onCheckedChange = { checked ->
                    powerampScanEnabled = checked
                    prefs.edit()
                        .putString(
                            PrefKeys.POWERAMP_SCAN_ON_SYNC.name,
                            if (checked) PowerampScanToggle.ENABLED.name else PowerampScanToggle.DISABLED.name
                        )
                        .apply()
                },
            )
        }
    }
}
