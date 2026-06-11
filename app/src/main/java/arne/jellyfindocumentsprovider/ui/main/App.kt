package arne.jellyfindocumentsprovider.ui.main

import android.content.Intent
import android.preference.PreferenceManager
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import arne.jellyfindocumentsprovider.ServerWizardActivity
import arne.jellyfindocumentsprovider.common.EventCategory
import arne.jellyfindocumentsprovider.common.InMemoryLogBuffer
import arne.jellyfindocumentsprovider.common.LocalSnackbarHostState
import arne.jellyfindocumentsprovider.common.PrefKeys
import arne.jellyfindocumentsprovider.common.StatusEventManager
import arne.jellyfindocumentsprovider.common.getEnum
import arne.jellyfindocumentsprovider.ui.browser.CacheBrowserScreen
import arne.jellyfindocumentsprovider.ui.components.StatusChips
import arne.jellyfindocumentsprovider.ui.components.StatusDetailDialog
import logcat.LogPriority

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun App(appViewModel: AppViewModel = viewModel()) {
    var slideDirection by remember { mutableIntStateOf(1) }
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val workManager = remember { WorkManager.getInstance(context) }
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
    val backStackEntry = navController.currentBackStackEntryAsState()

    val progress by appViewModel.progress.collectAsState()
    val sync by appViewModel.sync.collectAsState()
    val events by StatusEventManager.events.collectAsState()
    var detailCategory by remember { mutableStateOf<EventCategory?>(null) }
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var logFilterLevel by remember {
        val saved = prefs.getEnum<LogPriority>(PrefKeys.LOG_LEVEL)
        InMemoryLogBuffer.setUiLogLevel(saved)
        mutableStateOf(saved)
    }
    var showLogFilterMenu by remember { mutableStateOf(false) }
    LaunchedEffect(sync) {
        with(appViewModel) {
            workManager.observeProgress()
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ), title = {
                Text(backStackEntry.value?.destination?.route ?: "Home")
            }, actions = {
                StatusChips(events = events, onCategoryClick = { detailCategory = it })
                if (backStackEntry.value?.destination?.route == AppRoute.Logs.name) {
                    AssistChip(
                        onClick = { showLogFilterMenu = true },
                        label = { Text(logFilterLevel.name, fontSize = 11.sp) },
                    )
                    DropdownMenu(expanded = showLogFilterMenu, onDismissRequest = { showLogFilterMenu = false }) {
                        LogPriority.entries.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level.name) },
                                onClick = {
                                    logFilterLevel = level
                                    InMemoryLogBuffer.setUiLogLevel(level)
                                    prefs.edit().putString(PrefKeys.LOG_LEVEL.name, level.name).apply()
                                    showLogFilterMenu = false
                                }
                            )
                        }
                    }
                    IconButton(onClick = {
                        InMemoryLogBuffer.clear()
                    }, content = { Icon(Icons.Filled.Clear, contentDescription = "Clear logs") })
                }
                if (backStackEntry.value?.destination?.route == AppRoute.Home.name) {
                    IconButton(onClick = {
                        with(appViewModel) {
                            workManager.requestFavoritesSync()
                        }
                    }, content = { Icon(Icons.Filled.Star, contentDescription = "Sync Favorites") })
                    IconButton(onClick = {
                        with(appViewModel) {
                            workManager.requestSync()
                        }
                    }, content = { Icon(Icons.Filled.Sync, contentDescription = "Sync") })
                    IconButton(onClick = {
                        context.startActivity(
                            Intent(
                                context, ServerWizardActivity::class.java
                            )
                        )
                    }, content = { Icon(Icons.Filled.Add, contentDescription = "Add Server") })
                }
            })
        },
        bottomBar = {
            BottomAppBar {
                NavigationBar {
                    listOf(
                        AppRoute.Home, AppRoute.Browse, AppRoute.Cache, AppRoute.Logs, AppRoute.Settings
                    ).forEach {
                        val selected = it.name == backStackEntry.value?.destination?.route
                        NavigationBarItem(icon = {
                            Icon(
                                if (selected) it.selectedIcon else it.icon,
                                contentDescription = null
                            )
                        }, label = { Text(it.name) }, selected = selected, onClick = {
                            if (selected) return@NavigationBarItem

                            val routes = listOf(AppRoute.Home, AppRoute.Browse, AppRoute.Cache, AppRoute.Logs, AppRoute.Settings)
                            val currentIndex = routes.indexOfFirst { r -> r.name == backStackEntry.value?.destination?.route }
                            val targetIndex = routes.indexOf(it)
                            slideDirection = if (targetIndex > currentIndex) 1 else -1

                            navController.navigate(it.name.lowercase()) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        })
                    }
                }
            }
        }) { innerPadding ->

        Column(modifier = Modifier.padding(innerPadding)) {
            if (progress > 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            NavHost(
                navController = navController,
                startDestination = AppRoute.Home.name,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { slideDirection * it },
                        animationSpec = tween(durationMillis = 300)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -slideDirection * it },
                        animationSpec = tween(durationMillis = 300)
                    )
                },
            ) {
                composable(AppRoute.Home.name) { Wrapper(viewModelStoreOwner) { HomeScreen() } }
                composable(AppRoute.Browse.name) { Wrapper(viewModelStoreOwner) { CacheBrowserScreen() } }
                composable(AppRoute.Cache.name) { Wrapper(viewModelStoreOwner) { CacheMgrScreen() } }
                composable(AppRoute.Logs.name) { LogScreen(logFilterLevel) }
                composable(AppRoute.Settings.name) { Wrapper(viewModelStoreOwner) { SettingScreen() } }
            }
        }

        detailCategory?.let { category ->
            StatusDetailDialog(
                category = category,
                events = events.filter { it.category == category },
                onDismiss = { detailCategory = null },
            )
        }
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
    data object Browse : AppRoute("Browse", Icons.Outlined.FolderOpen, Icons.Filled.FolderOpen)
    data object Cache : AppRoute("Caches", Icons.Outlined.Inbox, Icons.Filled.Inbox)
    data object Logs : AppRoute("Logs", Icons.AutoMirrored.Outlined.FormatListBulleted, Icons.AutoMirrored.Filled.FormatListBulleted)
    data object Settings : AppRoute("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}