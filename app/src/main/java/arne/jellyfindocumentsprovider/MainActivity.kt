package arne.jellyfindocumentsprovider

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import arne.jellyfindocumentsprovider.common.LocalNavController
import arne.jellyfindocumentsprovider.common.LocalSnackbarHostState
import arne.jellyfindocumentsprovider.common.LocalSnackbarScope
import arne.jellyfindocumentsprovider.ui.globalCreateChannels
import arne.jellyfindocumentsprovider.ui.main.App
import arne.jellyfindocumentsprovider.ui.main.ServerSetting
import arne.jellyfindocumentsprovider.ui.theme.JellyfinDocumentsProviderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JellyfinDocumentsProviderTheme {
                Router()
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.globalCreateChannels()
    }
}


@Composable
fun Router() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    CompositionLocalProvider(
        LocalNavController provides navController,
        LocalSnackbarScope provides snackbarScope,
        LocalSnackbarHostState provides snackbarHostState
    ) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") { App() }
            composable(
                "server-setting/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) {
                it.arguments?.getLong("id")?.let { id ->
                    ServerSetting(id)
                } ?: Text(
                    "Server Settings/ID required",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    }
}