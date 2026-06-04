package arne.jellyfindocumentsprovider.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arne.jellyfindocumentsprovider.common.EventCategory
import arne.jellyfindocumentsprovider.common.StatusEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusChips(
    events: List<StatusEvent>,
    onCategoryClick: (EventCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        events.groupBy { it.category }.forEach { (category, categoryEvents) ->
            StatusChip(
                category = category,
                count = categoryEvents.size,
                icon = when (category) {
                    EventCategory.SYNC -> Icons.Filled.Sync
                    EventCategory.METADATA -> Icons.Filled.CloudDownload
                    EventCategory.NETWORK -> Icons.Filled.Download
                },
                onClick = { onCategoryClick(category) },
            )
        }
    }
}

@Composable
fun StatusChip(
    category: EventCategory,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val label = when (category) {
        EventCategory.SYNC -> "Sync"
        EventCategory.METADATA -> "Meta"
        EventCategory.NETWORK -> "Net"
    }

    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = if (count > 1) "$label ($count)" else label,
                fontSize = 11.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
    )
}

@Composable
fun StatusDetailDialog(
    category: EventCategory,
    events: List<StatusEvent>,
    onDismiss: () -> Unit,
) {
    val title = when (category) {
        EventCategory.SYNC -> "Sync Events"
        EventCategory.METADATA -> "Metadata Events"
        EventCategory.NETWORK -> "Network Events"
    }
    val icon = when (category) {
        EventCategory.SYNC -> Icons.Filled.Sync
        EventCategory.METADATA -> Icons.Filled.CloudDownload
        EventCategory.NETWORK -> Icons.Filled.Download
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title)
            }
        },
        text = {
            if (events.isEmpty()) {
                Text("No active events")
            } else {
                LazyColumn {
                    items(events) { event ->
                        EventDetailRow(event)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun EventDetailRow(event: StatusEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = event.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = timeFormat.format(Date(event.startTime)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (event.message.isNotBlank()) {
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (event.progress >= 0f) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { event.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}
