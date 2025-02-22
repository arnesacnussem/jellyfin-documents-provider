package arne.jellyfindocumentsprovider.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

val LocalSnackbarScope = staticCompositionLocalOf<CoroutineScope> {
    error("No SnackbarScope provided")
}

@Composable
fun useLocalSnackbar(): (suspend SnackbarHostState.() -> Unit) -> Unit {
    val hostState = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current

    return remember { { block -> scope.launch { block(hostState) } } }
}