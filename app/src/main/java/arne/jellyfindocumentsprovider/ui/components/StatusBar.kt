package arne.jellyfindocumentsprovider.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arne.jellyfindocumentsprovider.common.EventCategory
import arne.jellyfindocumentsprovider.common.StatusEventManager

@Composable
fun StatusBar(modifier: Modifier = Modifier) {
    val events by StatusEventManager.events.collectAsState()

    AnimatedVisibility(
        visible = events.isNotEmpty(),
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            events.groupBy { it.category }.forEach { (category, categoryEvents) ->
                StatusChip(
                    category = category,
                    count = categoryEvents.size,
                    message = categoryEvents.firstOrNull()?.message ?: "",
                    icon = when (category) {
                        EventCategory.SYNC -> Icons.Filled.Sync
                        EventCategory.METADATA -> Icons.Filled.CloudDownload
                        EventCategory.NETWORK -> Icons.Filled.Download
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(category: EventCategory, count: Int, message: String, icon: ImageVector) {
    val label = when (category) {
        EventCategory.SYNC -> "Sync"
        EventCategory.METADATA -> "Meta"
        EventCategory.NETWORK -> "Net"
    }

    AssistChip(
        onClick = {},
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
