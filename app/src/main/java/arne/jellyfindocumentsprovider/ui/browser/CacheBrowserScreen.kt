package arne.jellyfindocumentsprovider.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import arne.jellyfindocumentsprovider.hacks.readable

@Composable
@Preview
fun CacheBrowserScreen(viewModel: CacheBrowserViewModel = viewModel()) {
    val entries = viewModel.filteredEntries
    val isLoading = viewModel.isLoading
    val downloadingIds = viewModel.downloadingIds

    var showFilterMenu by remember { mutableStateOf(false) }
    var showArtistDialog by remember { mutableStateOf(false) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<CacheBrowserEntry?>(null) }

    LaunchedEffect(viewModel.searchQuery) { viewModel.applyFilters() }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = viewModel.searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            filterMode = viewModel.filterMode,
            jumpArtist = viewModel.jumpArtist,
            jumpAlbum = viewModel.jumpAlbum,
            onFilterClick = { showFilterMenu = true },
            onJumpArtist = { showArtistDialog = true },
            onJumpAlbum = { showAlbumDialog = true },
            onClearJump = { viewModel.clearJumps() },
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No items found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.virtualFileId }) { entry ->
                CacheBrowserItemRow(
                    entry = entry,
                    isDownloading = entry.virtualFileId in downloadingIds,
                    onCache = { viewModel.startCache(entry) },
                    onDelete = { confirmDelete = entry },
                )
            }
        }

        HorizontalDivider()
        BottomStatsBar(
            totalCount = viewModel.filteredEntries.size,
            totalCacheSize = viewModel.totalCacheSize,
        )
    }

    FilterDropdownMenu(
        expanded = showFilterMenu,
        current = viewModel.filterMode,
        onSelect = {
            viewModel.updateFilterMode(it)
            showFilterMenu = false
        },
        onDismiss = { showFilterMenu = false },
    )

    if (showArtistDialog) {
        ArtistSelectionDialog(
            artists = viewModel.artists,
            current = viewModel.jumpArtist,
            onSelect = {
                viewModel.updateJumpArtist(it)
                showArtistDialog = false
            },
            onClear = {
                viewModel.clearJumps()
                showArtistDialog = false
            },
            onDismiss = { showArtistDialog = false },
        )
    }

    if (showAlbumDialog) {
        AlbumSelectionDialog(
            albums = viewModel.albums,
            current = viewModel.jumpAlbum,
            onSelect = {
                viewModel.updateJumpAlbum(it)
                showAlbumDialog = false
            },
            onClear = {
                viewModel.clearJumps()
                showAlbumDialog = false
            },
            onDismiss = { showAlbumDialog = false },
        )
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete Cache") },
            text = { Text("Delete cached data for \"${entry.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCache(entry)
                    confirmDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filterMode: BrowserFilter,
    jumpArtist: String?,
    jumpAlbum: String?,
    onFilterClick: () -> Unit,
    onJumpArtist: () -> Unit,
    onJumpAlbum: () -> Unit,
    onClearJump: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search title, artist, album...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                label = filterMode.label,
                icon = Icons.Filled.FilterList,
                onClick = onFilterClick,
            )
            if (jumpArtist != null || jumpAlbum != null) {
                FilterChip(
                    label = jumpArtist ?: jumpAlbum ?: "",
                    icon = if (jumpArtist != null) Icons.Filled.Person else Icons.Filled.Album,
                    onClick = onClearJump,
                    trailing = Icons.Filled.Clear,
                )
            } else {
                FilterChip(
                    label = "Artist",
                    icon = Icons.Filled.Person,
                    onClick = onJumpArtist,
                )
                FilterChip(
                    label = "Album",
                    icon = Icons.Filled.Album,
                    onClick = onJumpAlbum,
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: ImageVector? = null,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 13.sp, maxLines = 1)
            trailing?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun FilterDropdownMenu(
    expanded: Boolean,
    current: BrowserFilter,
    onSelect: (BrowserFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        BrowserFilter.entries.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Text(
                        mode.label,
                        fontWeight = if (mode == current) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun ArtistSelectionDialog(
    artists: List<String>,
    current: String?,
    onSelect: (String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(artists, search) {
        if (search.isBlank()) artists
        else artists.filter { it.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Artist") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search artist...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    if (current != null) {
                        item {
                            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                                Text("Clear Selection", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    items(filtered) { artist ->
                        TextButton(
                            onClick = { onSelect(artist) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                artist,
                                fontWeight = if (artist == current) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
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
private fun AlbumSelectionDialog(
    albums: List<Pair<String, String?>>,
    current: String?,
    onSelect: (String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(albums, search) {
        if (search.isBlank()) albums
        else albums.filter { it.first.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Album") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search album...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    if (current != null) {
                        item {
                            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                                Text("Clear Selection", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    items(filtered, key = { it.first }) { (name, _) ->
                        TextButton(
                            onClick = { onSelect(name) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                name,
                                fontWeight = if (name == current) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
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
private fun CacheBrowserItemRow(
    entry: CacheBrowserEntry,
    isDownloading: Boolean,
    onCache: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${entry.artist} · ${entry.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.fileCacheStatus is CacheStatus.NotCached && !isDownloading) {
                    IconButton(onClick = onCache) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = "Cache",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (entry.fileCacheStatus !is CacheStatus.NotCached) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete cache",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CacheStatusBadge(
                    icon = when (entry.fileCacheStatus) {
                        is CacheStatus.Complete -> Icons.Filled.MusicNote
                        is CacheStatus.Partial -> Icons.Outlined.MusicNote
                        CacheStatus.NotCached -> Icons.Outlined.MusicNote
                    },
                    label = when (val s = entry.fileCacheStatus) {
                        CacheStatus.NotCached -> "Not cached"
                        is CacheStatus.Partial -> "${(s.progressPercent() * 100).toInt()}%"
                        is CacheStatus.Complete -> s.cachedSize.readable
                    },
                    color = when (entry.fileCacheStatus) {
                        CacheStatus.NotCached -> CacheColors.gray
                        is CacheStatus.Partial -> CacheColors.yellow
                        is CacheStatus.Complete -> CacheColors.green
                    },
                )
                CacheStatusBadge(
                    icon = when (entry.thumbStatus) {
                        ThumbStatus.CACHED -> Icons.Filled.Image
                        else -> Icons.Outlined.Image
                    },
                    label = when (entry.thumbStatus) {
                        ThumbStatus.CACHED -> "Art"
                        ThumbStatus.NOT_CACHED -> "No art"
                        ThumbStatus.NONE -> "None"
                    },
                    color = when (entry.thumbStatus) {
                        ThumbStatus.CACHED -> CacheColors.green
                        else -> CacheColors.gray
                    },
                )
                CacheStatusBadge(
                    icon = when (entry.lyricsStatus) {
                        LyricsStatus.CACHED -> Icons.Filled.Lyrics
                        LyricsStatus.NOT_CACHED -> Icons.Outlined.Lyrics
                    },
                    label = when (entry.lyricsStatus) {
                        LyricsStatus.CACHED -> "Lyrics"
                        LyricsStatus.NOT_CACHED -> "No lyrics"
                    },
                    color = when (entry.lyricsStatus) {
                        LyricsStatus.CACHED -> CacheColors.green
                        LyricsStatus.NOT_CACHED -> CacheColors.gray
                    },
                )
            }

            if (entry.fileCacheStatus is CacheStatus.Partial) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { entry.fileCacheStatus.progressPercent() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CacheStatusBadge(
    icon: ImageVector,
    label: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
        )
    }
}

@Composable
private fun BottomStatsBar(
    totalCount: Int,
    totalCacheSize: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Items: $totalCount",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            "Cache: ${totalCacheSize.readable}",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private object CacheColors {
    val green = Color(0xFF4CAF50)
    val yellow = Color(0xFFFF9800)
    val gray = Color(0xFF9E9E9E)
}
