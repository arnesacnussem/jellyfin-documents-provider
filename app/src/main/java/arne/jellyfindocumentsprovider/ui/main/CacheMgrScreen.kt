package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arne.jellyfindocumentsprovider.data.AppDependencies
import arne.jellyfindocumentsprovider.hacks.readable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

data class CacheEntryDisplay(
    val name: String,
    val fileSize: Long,
    val cachedSize: Long,
    val chunks: List<LongRange>,
    val isComplete: Boolean,
)

@Composable
@Preview
fun CacheMgrScreen() {
    var cacheEntries by remember { mutableStateOf<List<CacheEntryDisplay>>(emptyList()) }
    var thumbCount by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val repos = AppDependencies.repos
                thumbCount = repos.thumbCache.count()
                val cacheInfos = repos.cacheInfo.findAll()
                cacheEntries = cacheInfos.mapNotNull { ci ->
                    val vf = repos.virtualFile.findByDocumentId(ci.vfDocId)
                    if (vf != null) {
                        val cachedSize = ci.chunks.sumOf { it.last - it.first + 1 }
                        CacheEntryDisplay(
                            name = vf.displayName,
                            fileSize = vf.size,
                            cachedSize = cachedSize,
                            chunks = ci.chunks.toList(),
                            isComplete = ci.isCompleted ||
                                (vf.size > 0 && ci.chunks.noGapsIn(0 until vf.size)),
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

            items(cacheEntries, key = { it.name }) { entry ->
                CacheFileRow(entry)
            }
        }
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

        // Background (uncached area)
        drawRoundRect(
            color = backgroundColor,
            cornerRadius = CornerRadius(2f, 2f),
        )

        if (fileSize <= 0 || chunks.isEmpty()) return@Canvas

        // Draw each cached chunk as a filled rectangle
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
