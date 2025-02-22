package arne.jellyfindocumentsprovider.ui.main

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import arne.jellyfindocumentsprovider.ServerWizardActivity
import arne.jellyfindocumentsprovider.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun App(appViewModel: AppViewModel = viewModel()) {
    var slideDirection by remember { mutableIntStateOf(1) }
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
    val loading = appViewModel.loading.collectAsState()
    val backStackEntry = navController.currentBackStackEntryAsState()
    Scaffold(modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ), title = {
                Text("Home")
            }, actions = {
                IconButton(onClick = {
                    context.startActivity(
                        Intent(
                            context, ServerWizardActivity::class.java
                        )
                    )
                }, content = { Icon(Icons.Filled.Add, contentDescription = "Add Server") })
            })
            if (loading.value) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        bottomBar = {
            BottomAppBar {
                NavigationBar {
                    listOf(
                        AppRoute.Home, AppRoute.Cache, AppRoute.Settings
                    ).forEach {
                        val selected = it.name == backStackEntry.value?.destination?.route
                        NavigationBarItem(icon = {
                            Icon(
                                if (selected) it.selectedIcon else it.icon,
                                contentDescription = null
                            )
                        }, label = { Text(it.name) }, selected = selected, onClick = {
                            if (selected) return@NavigationBarItem

                            slideDirection = if (it.name == AppRoute.Cache.name) -1 else 1
                            navController.navigate(it.name.lowercase()) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        })
                    }
                }
            }
        }) { innerPadding ->
        AppContent(
            navController, innerPadding, slideDirection, viewModelStoreOwner
        )
    }
}

@Composable
fun AppContent(
    navController: NavHostController,
    innerPadding: PaddingValues,
    slideDirection: Int,
    viewModelStoreOwner: ViewModelStoreOwner
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Home.name,
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxHeight()
            .fillMaxWidth(),
//        enterTransition = {
//            slideInHorizontally(
//                initialOffsetX = { it },
//                animationSpec = tween(durationMillis = 300)
//            )
//        },
//        exitTransition = {
//            slideOutHorizontally(
//                targetOffsetX = { -it },
//                animationSpec = tween(durationMillis = 300)
//            )
//        }
//        exitTransition = {
//            slideOutHorizontally(
//                targetOffsetX = { -slideDirection * it },
//                animationSpec = tween(durationMillis = 300)
//            )
//        },
    ) {
        composable(AppRoute.Home.name) { Wrapper(viewModelStoreOwner) { HomeScreen() } }
        composable(AppRoute.Cache.name) { Wrapper(viewModelStoreOwner) { CacheMgrScreen() } }
        composable(AppRoute.Settings.name) { Wrapper(viewModelStoreOwner) { SettingScreen() } }
    }
}

@Composable
fun Wrapper(
    viewModelStoreOwner: ViewModelStoreOwner, content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides viewModelStoreOwner,
    ) {
        content()
    }
}

sealed class AppRoute(
    val name: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    data object Home : AppRoute("Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Cache : AppRoute("Caches", Icons.Outlined.Inbox, Icons.Filled.Inbox)
    data object Settings : AppRoute("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}