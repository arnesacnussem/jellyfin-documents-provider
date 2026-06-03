package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arne.jellyfindocumentsprovider.data.AppDependencies
import arne.jellyfindocumentsprovider.hacks.readable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ConfirmAction { CleanIncomplete, CleanAll }

data class CacheEntryDisplay(
    val id: Long,
    val name: String,
    val fileSize: Long,
    val cachedSize: Long,
    val chunks: List<LongRange>,
    val isComplete: Boolean,
    val localPath: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun CacheMgrScreen() {
    var cacheEntries by remember { mutableStateOf<List<CacheEntryDisplay>>(emptyList()) }
    var thumbCount by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var confirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val repos = AppDependencies.repos
                thumbCount = repos.thumbCache.countWithData()
                val cacheInfos = repos.cacheInfo.findAll()
                cacheEntries = cacheInfos.mapNotNull { ci ->
                    val vf = repos.virtualFile.findByDocumentId(ci.vfDocId)
                    if (vf != null) {
                        val cachedSize = ci.chunks.sumOf { it.last - it.first + 1 }
                        CacheEntryDisplay(
                            id = ci.id,
                            name = vf.displayName,
                            fileSize = vf.size,
                            cachedSize = cachedSize,
                            chunks = ci.chunks.toList(),
                            isComplete = ci.isCompleted ||
                                (vf.size > 0 && ci.chunks.noGapsIn(0 until vf.size)),
                            localPath = ci.localPath,
                        )
                    } else null
                }
            }
            isLoading = false
            delay(2000)
        }
    }

    val totalCacheSize = cacheEntries.sumOf { it.cachedSize }
    val completeCount = cacheEntries.count { it.isComplete }

    fun deleteEntry(entry: CacheEntryDisplay) {
        cacheEntries = cacheEntries.filter { it.id != entry.id }
        scope.launch(Dispatchers.IO) {
            val repos = AppDependencies.repos
            repos.cacheInfo.delete(entry.id)
            File(entry.localPath).delete()
        }
    }

    fun cleanIncomplete() {
        val incomplete = cacheEntries.filter { !it.isComplete }
        cacheEntries = cacheEntries.filter { it.isComplete }
        scope.launch(Dispatchers.IO) {
            val repos = AppDependencies.repos
            incomplete.forEach { entry ->
                repos.cacheInfo.delete(entry.id)
                File(entry.localPath).delete()
            }
        }
    }

    fun cleanAll() {
        cacheEntries = emptyList()
        thumbCount = 0
        scope.launch(Dispatchers.IO) {
            val repos = AppDependencies.repos
            val allInfos = repos.cacheInfo.findAll()
            allInfos.forEach { File(it.localPath).delete() }
            repos.cacheInfo.deleteAll()
            repos.thumbCache.deleteAll()
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stats section
        item {
            StatsSection(
                thumbCount = thumbCount,
                totalCacheSize = totalCacheSize,
                completeCount = completeCount,
            )
        }

        // Action buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { confirmDialog = ConfirmAction.CleanIncomplete },
                    modifier = Modifier.weight(1f),
                    enabled = cacheEntries.any { !it.isComplete },
                ) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Clean Incomplete")
                }
                Button(
                    onClick = { confirmDialog = ConfirmAction.CleanAll },
                    modifier = Modifier.weight(1f),
                    enabled = cacheEntries.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Clean All")
                }
            }
        }

        if (cacheEntries.isEmpty()) {
            item {
                Text(
                    "No cached files",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Cached Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(cacheEntries, key = { it.id }) { entry ->
                SwipeableCacheFileRow(
                    entry = entry,
                    onDelete = { deleteEntry(it) },
                )
            }
        }
    }

    // Confirmation dialog
    confirmDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = {
                Text(
                    when (action) {
                        ConfirmAction.CleanIncomplete -> "Clean Incomplete Files"
                        ConfirmAction.CleanAll -> "Clean All Cache"
                    }
                )
            },
            text = {
                Text(
                    when (action) {
                        ConfirmAction.CleanIncomplete ->
                            "Delete all incomplete cached files? This cannot be undone."
                        ConfirmAction.CleanAll ->
                            "Delete all cached files and thumbnails? This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDialog = null
                        when (action) {
                            ConfirmAction.CleanIncomplete -> cleanIncomplete()
                            ConfirmAction.CleanAll -> cleanAll()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        when (action) {
                            ConfirmAction.CleanIncomplete -> "Clean"
                            ConfirmAction.CleanAll -> "Delete All"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCacheFileRow(
    entry: CacheEntryDisplay,
    onDelete: (CacheEntryDisplay) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(entry)
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                },
                label = "swipe_bg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        },
    ) {
        CacheFileRow(entry)
    }
}

@Composable
fun StatsSection(thumbCount: Long, totalCacheSize: Long, completeCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            label = "Thumbnails",
            value = thumbCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Cache Size",
            value = totalCacheSize.readable,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Complete",
            value = "$completeCount",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CacheFileRow(entry: CacheEntryDisplay) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = if (entry.isComplete) entry.fileSize.readable
                           else "${entry.cachedSize.readable} / ${entry.fileSize.readable}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!entry.isComplete) {
                Spacer(Modifier.height(6.dp))
                CacheBitmap(
                    chunks = entry.chunks,
                    fileSize = entry.fileSize,
                )
            }
        }
    }
}

@Composable
fun CacheBitmap(
    chunks: List<LongRange>,
    fileSize: Long,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val cachedColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        drawRoundRect(
            color = backgroundColor,
            cornerRadius = CornerRadius(2f, 2f),
        )

        if (fileSize <= 0 || chunks.isEmpty()) return@Canvas

        for (chunk in chunks) {
            val startFraction = chunk.first.toFloat() / fileSize.toFloat()
            val endFraction = chunk.last.toFloat() / fileSize.toFloat()
            val x = startFraction * canvasWidth
            val width = (endFraction - startFraction) * canvasWidth

            if (width > 0.5f) {
                drawRoundRect(
                    color = cachedColor,
                    topLeft = Offset(x, 0f),
                    size = Size(width.coerceAtLeast(1f), canvasHeight),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
    }
}
