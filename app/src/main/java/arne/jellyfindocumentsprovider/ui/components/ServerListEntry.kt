package arne.jellyfindocumentsprovider.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import logcat.logcat
import kotlin.math.roundToInt

data class ServerListEntryInfo(
    val db: Long,
    val id: String,
    val name: String,
    val url: String,
    val itemCount: Long,
    val user: String,
    val libCount: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun ServerItem(
    @PreviewParameter(ServerItemProvider::class) info: ServerListEntryInfo,
    delete: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val dragState = remember {
        AnchoredDraggableState(
            initialValue = MyDragAnchor.Start,
            anchors = with(density) {
                DraggableAnchors {
                    MyDragAnchor.Start at 0.dp.toPx()
                    MyDragAnchor.End at -128.dp.toPx()
                }
            },
            positionalThreshold = { 0.3f * it },
            velocityThreshold = { with(density) { Int.MAX_VALUE.dp.toPx() } },
            snapAnimationSpec = tween(),
            decayAnimationSpec = exponentialDecay(),
        )
    }
    var height by remember { mutableStateOf(0.dp) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .fillMaxHeight()
                    .background(color = MaterialTheme.colorScheme.error)
                    .clickable {
                        coroutineScope.launch {
                            dragState.snapTo(MyDragAnchor.Start)
                            logcat { "request for delete ${info.url}" }
                            delete()
                        }
                    }, contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    "Delete",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }

        Box(modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .offset {
                IntOffset(
                    x = dragState
                        .requireOffset()
                        .roundToInt(), y = 0
                )
            }
            .anchoredDraggable(
                state = dragState,
                orientation = Orientation.Horizontal,
            )
            .background(
                color = MaterialTheme.colorScheme.background
            )
            .onGloballyPositioned { coordinates ->
                height = with(density) {
                    coordinates.size.height.toDp()
                }
            }
            .clickable {
                onClick()
            }) {
            Card(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "${info.name} (${info.user})",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            info.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("Library: ${info.libCount}")
                        Text("Items: ${info.itemCount}")
                    }
                }
            }
        }
    }
}


class ServerItemProvider : PreviewParameterProvider<ServerListEntryInfo> {
    override val values = sequenceOf(
        ServerListEntryInfo(
            url = "https://example.com",
            name = "Jellyfin Server",
            user = "user",
            db = 0,
            id = "server uuid",
            itemCount = 0,
            libCount = 0
        )
    )
}


private enum class MyDragAnchor { Start, End }
