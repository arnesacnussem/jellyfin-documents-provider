package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import logcat.LogPriority

@Composable
fun LogScreen(filterLevel: LogPriority = LogPriority.INFO) {
    var entries by remember { mutableStateOf(InMemoryLogBuffer.snapshot()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        InMemoryLogBuffer.newEntryFlow.collect {
            entries = InMemoryLogBuffer.snapshot()
        }
    }

    LaunchedEffect(Unit) {
        InMemoryLogBuffer.clearEvents.collect {
            entries = emptyList()
        }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    val filtered = entries.filter { it.level.priorityInt >= filterLevel.priorityInt }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
    ) {
        items(filtered, key = { System.identityHashCode(it) }) { entry ->
            LogEntryRow(entry)
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
