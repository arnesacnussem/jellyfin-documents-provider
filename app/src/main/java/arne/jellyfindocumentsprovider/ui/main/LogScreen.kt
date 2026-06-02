package arne.jellyfindocumentsprovider.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arne.jellyfindocumentsprovider.common.InMemoryLogBuffer
import arne.jellyfindocumentsprovider.common.LogEntry
import kotlinx.coroutines.launch
import logcat.LogPriority

@Composable
fun LogScreen() {
    var entries by remember { mutableStateOf(InMemoryLogBuffer.snapshot()) }
    var filterLevel by remember { mutableStateOf(LogPriority.INFO) }
    var showFilterMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        InMemoryLogBuffer.newEntryFlow.collect {
            entries = InMemoryLogBuffer.snapshot()
        }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Logs", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = {
                    InMemoryLogBuffer.clear()
                    entries = emptyList()
                }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.padding(end = 4.dp))
                    Text("Clear")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                label = filterLevel.name,
                selected = false,
                onClick = { showFilterMenu = true },
            )
            DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                LogPriority.entries.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.name) },
                        onClick = {
                            filterLevel = level
                            showFilterMenu = false
                        }
                    )
                }
            }

            val counts = entries.groupBy { it.level }.mapValues { it.value.size }

            LogPriority.entries.forEach { level ->
                val count = counts[level] ?: 0
                if (count > 0) {
                    LevelChip(level = level, count = count)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
    )
}

@Composable
private fun LevelChip(level: LogPriority, count: Int) {
    val color = when (level) {
        LogPriority.VERBOSE -> Color.Gray
        LogPriority.DEBUG -> Color(0xFF2196F3)
        LogPriority.INFO -> Color(0xFF4CAF50)
        LogPriority.WARN -> Color(0xFFFF9800)
        LogPriority.ERROR -> Color(0xFFF44336)
        LogPriority.ASSERT -> Color(0xFF9C27B0)
    }
    Text(
        text = "${level.name.first()}($count)",
        color = color,
        fontSize = 11.sp,
    )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
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
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = entry.message,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
