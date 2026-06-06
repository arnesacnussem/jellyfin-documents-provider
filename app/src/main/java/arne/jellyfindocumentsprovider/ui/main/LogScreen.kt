package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arne.jellyfindocumentsprovider.common.InMemoryLogBuffer
import arne.jellyfindocumentsprovider.common.LogEntry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import logcat.LogPriority

@Composable
fun LogScreen(filterLevel: LogPriority = LogPriority.INFO) {
    var entries by remember { mutableStateOf(InMemoryLogBuffer.snapshot()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isFollowing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        InMemoryLogBuffer.newEntryFlow.collect {
            entries = InMemoryLogBuffer.snapshot()
        }
    }

    LaunchedEffect(Unit) {
        InMemoryLogBuffer.clearEvents.collect {
            entries = emptyList()
            isFollowing = true
        }
    }

    val filtered = entries.filter { it.level.priorityInt >= filterLevel.priorityInt }

    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty() && isFollowing) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Stop) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                isFollowing = lastVisible >= totalItems - 2
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        ) {
            items(filtered, key = { it.id }) { entry ->
                LogEntryRow(entry)
            }
        }

        if (!isFollowing && filtered.isNotEmpty()) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        isFollowing = true
                        listState.animateScrollToItem(filtered.size - 1)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Jump to bottom")
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogPriority.VERBOSE -> Color.Gray
        LogPriority.DEBUG -> Color(0xFF2196F3)
        LogPriority.INFO -> Color(0xFF4CAF50)
        LogPriority.WARN -> Color(0xFFFF9800)
        LogPriority.ERROR -> Color(0xFFF44336)
        LogPriority.ASSERT -> Color(0xFF9C27B0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.formattedTime,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = "[${entry.level.name.first()}]",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = levelColor,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = entry.tag,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF9E9E9E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = entry.message,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
