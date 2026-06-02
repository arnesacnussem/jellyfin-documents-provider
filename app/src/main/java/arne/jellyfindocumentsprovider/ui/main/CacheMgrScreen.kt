package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arne.jellyfindocumentsprovider.data.AppDependencies
import logcat.logcat

@Composable
@Preview
fun CacheMgrScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Cache Management", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Virtual Files: ${AppDependencies.repos.virtualFile.count()}")
        Text("Servers: ${AppDependencies.repos.server.count()}")

        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            logcat { "Cache cleanup requested" }
            // TODO: implement actual cache cleanup
        }) {
            Text("Clean Up Cache")
        }
    }
}
