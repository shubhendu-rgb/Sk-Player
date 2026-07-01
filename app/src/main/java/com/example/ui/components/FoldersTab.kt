package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.repository.VideoItem

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.blur
import kotlinx.coroutines.launch

data class FolderItem(
    val name: String,
    val path: String,
    val videos: List<VideoItem>,
    val totalSize: Long
)

@OptIn(ExperimentalAnimationApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FoldersTab(
    localVideos: List<VideoItem>,
    playlists: List<com.example.data.database.PlaylistEntity>,
    recentlyPlayedList: List<com.example.ui.viewmodel.RecentlyPlayedItem>,
    onCreatePlaylist: (String) -> Unit,
    onVideoPlay: (VideoItem, String?) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    onRenameVideo: (VideoItem, String) -> Unit,
    onHideVideo: (VideoItem) -> Unit,
    folderVideosSortOption: String,
    onFolderVideosSortOptionChange: (String) -> Unit,
    isUiBlurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null
) {
    var activeFolderKey by remember { mutableStateOf<String?>(null) }
    var activePlaylistId by remember { mutableStateOf<Int?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Selection State for folder videos
    val selectedVideos = remember { mutableStateListOf<VideoItem>() }
    val isSelectionMode = selectedVideos.isNotEmpty()

    // Dialog & Flow States
    var showSelectionMenu by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchAddToPlaylistSub by remember { mutableStateOf(false) }
    var isExtractingAudioState by remember { mutableStateOf(false) }
    var showNewPlaylistDialogForFolderBatch by remember { mutableStateOf(false) }

    // Clear selection if folder path or playlist changes
    LaunchedEffect(activeFolderKey, activePlaylistId) {
        selectedVideos.clear()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { com.example.data.database.AppDatabase.getDatabase(context) }
    val playlistDao = remember { db.playlistDao() }

    val activePlaylistItems by remember(activePlaylistId) {
        if (activePlaylistId != null) {
            playlistDao.getItemsForPlaylist(activePlaylistId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    androidx.activity.compose.BackHandler(enabled = activeFolderKey != null || activePlaylistId != null || isSelectionMode) {
        if (isSelectionMode) {
            selectedVideos.clear()
        } else if (activeFolderKey != null) {
            activeFolderKey = null
        } else if (activePlaylistId != null) {
            activePlaylistId = null
        }
    }

    var folderSearchQuery by remember { mutableStateOf("") }

    // Reactively group local videos by folder category names
    val folders = remember(localVideos) {
        localVideos.groupBy { video ->
            if (video.id.startsWith("sim_")) {
                "Downloads"
            } else {
                java.io.File(video.uri).parentFile?.name ?: "Videos"
            }
        }.map { (name, list) ->
            FolderItem(
                name = name,
                path = name,
                videos = list,
                totalSize = list.sumOf { it.size }
            )
        }.sortedBy { it.name.lowercase() }
    }

    val totalStorageUsed = remember(folders) {
        folders.sumOf { it.totalSize }
    }

    val selectedFolder = remember(activeFolderKey, folders) {
        folders.find { it.path == activeFolderKey }
    }

    if (showNewPlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. Action Favorites") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName)
                            showNewPlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (selectedFolder != null) {
            val isBlurred = showSelectionMenu || showBatchDeleteConfirm || showBatchAddToPlaylistSub || isExtractingAudioState || showNewPlaylistDialog || showNewPlaylistDialogForFolderBatch
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (isBlurred && isUiBlurEnabled) 16.dp else 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                if (isSelectionMode) {
                                    selectedVideos.clear()
                                } else {
                                    activeFolderKey = null
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isSelectionMode) "Clear Selection" else "Return to Folders List",
                                tint = if (isSelectionMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isSelectionMode) "${selectedVideos.size} Selected" else selectedFolder.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isSelectionMode) {
                        IconButton(
                            onClick = { showSelectionMenu = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Batch Action Menu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        var isSortMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { isSortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort Folder Videos", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = isSortMenuExpanded,
                                onDismissRequest = { isSortMenuExpanded = false }
                            ) {
                                listOf("Date", "Title", "Size").forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = { onFolderVideosSortOptionChange(opt); isSortMenuExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Directory Path",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "/storage/emulated/0/${selectedFolder.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${selectedFolder.videos.size} items",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSize(selectedFolder.totalSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                val uniqueFolderVideos = remember(selectedFolder.videos, folderVideosSortOption) {
                    val seen = mutableSetOf<String>()
                    val unique = selectedFolder.videos.filter { it.uri.isNotEmpty() && seen.add(it.uri) }
                    when (folderVideosSortOption) {
                        "Date" -> unique.sortedByDescending { it.dateAdded }
                        "Title" -> unique.sortedBy { it.title.lowercase() }
                        "Size" -> unique.sortedByDescending { it.size }
                        else -> unique
                    }
                }
                val selectedVideoIds = remember(selectedVideos.size) {
                    selectedVideos.map { it.id }.toSet()
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uniqueFolderVideos, key = { it.id }) { video ->
                        val isSelected = selectedVideoIds.contains(video.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) {
                                                selectedVideos.removeAll { it.id == video.id }
                                            } else {
                                                selectedVideos.add(video)
                                            }
                                        } else {
                                            onVideoPlay(video, selectedFolder.name)
                                        }
                                    },
                                    onLongClick = {
                                        if (isSelected) {
                                            selectedVideos.removeAll { it.id == video.id }
                                        } else {
                                            selectedVideos.add(video)
                                        }
                                    }
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val finalThumb = video.thumbnailUri ?: video.uri
                                    if (finalThumb.isNotEmpty()) {
                                        AsyncImage(
                                            model = finalThumb,
                                            contentDescription = video.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatDuration(video.duration),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val currentProgress = recentlyPlayedList.find { it.uri == video.uri }?.progress ?: 0L
                                    if (currentProgress > 0 && video.duration > 0) {
                                        LinearProgressIndicator(
                                            progress = { (currentProgress.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .height(3.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = Color.White.copy(alpha = 0.3f),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatSize(video.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = "PLAY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (activePlaylistId != null) {
            val selectedPlaylist = playlists.find { it.id == activePlaylistId }
            if (selectedPlaylist != null) {
                // Playlist Detail View
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { activePlaylistId = null },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Return to Folders List",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = selectedPlaylist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Playlist Details",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Custom Group Selection",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${activePlaylistItems.size} items",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (activePlaylistItems.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No videos in this playlist yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 100.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(activePlaylistItems, key = { it.id }) { item ->
                                val video = VideoItem(
                                    id = "playlist_${item.id}",
                                    title = item.title,
                                    uri = item.uri,
                                    duration = item.duration,
                                    size = item.size,
                                    isLocal = true,
                                    thumbnailUri = item.thumbnailUri,
                                    mimeType = "video/*"
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onVideoPlay(video, null) }
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val finalThumb = video.thumbnailUri ?: video.uri
                                            if (finalThumb.isNotEmpty()) {
                                                AsyncImage(
                                                    model = finalThumb,
                                                    contentDescription = video.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Movie,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = formatDuration(video.duration),
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = video.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formatSize(video.size),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    text = "•",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                                Text(
                                                    text = "PLAY",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                activePlaylistId = null
            }
        } else {
            // Folders List Landing View
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onMenuClick != null) {
                            IconButton(onClick = onMenuClick) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Drawer Menu",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = "Sk Player",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${folders.size} folders found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                if (folders.isEmpty() && playlists.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "No local folders icon",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No content found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Scan local folders or create custom playlists to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    val uniqueFolders = remember(folders) {
                        val seen = mutableSetOf<String>()
                        folders.filter { it.path.isNotEmpty() && seen.add(it.path) }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Playlists Carousel directly at the top of Directories!
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "My Playlists",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { showNewPlaylistDialog = true }) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                if (playlists.isEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showNewPlaylistDialog = true }
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                "Tap to create your first playlist",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                                        contentPadding = PaddingValues(end = 16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(playlists, key = { it.id }) { playlist ->
                                            Card(
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .clickable { activePlaylistId = playlist.id }
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.FolderSpecial,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(
                                                        text = playlist.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Playlist",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Directories",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        items(uniqueFolders, key = { it.path }) { folder ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeFolderKey = folder.path }
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${folder.videos.size} video files",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // Storage Indicators on the right side of folder with sizes
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.wrapContentWidth()
                                    ) {
                                        Text(
                                            text = formatSize(folder.totalSize),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- BATCH SELECTION ACTION SHEET / MENU ---
        if (showSelectionMenu) {
            ModalBottomSheet(
                onDismissRequest = { showSelectionMenu = false },
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Batch Action Menu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "${selectedVideos.size} items highlighted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // Hide Option
                        Button(
                            onClick = {
                                showSelectionMenu = false
                                selectedVideos.forEach { video ->
                                    onHideVideo(video)
                                }
                                selectedVideos.clear()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hide Selection", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 1. Delete Option
                        Button(
                            onClick = {
                                showSelectionMenu = false
                                showBatchDeleteConfirm = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Selection", color = MaterialTheme.colorScheme.onErrorContainer)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Extract Audio Option
                        Button(
                            onClick = {
                                showSelectionMenu = false
                                isExtractingAudioState = true
                                scope.launch {
                                    var successCount = 0
                                    selectedVideos.forEach { v ->
                                        val output = com.example.AudioExtractor.extractAudio(context, v.uri, v.title)
                                        if (output != null) {
                                            successCount++
                                        }
                                    }
                                    isExtractingAudioState = false
                                    android.widget.Toast.makeText(
                                        context,
                                        "Extracted $successCount audio track(s) to /storage/emulated/0/Music/",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    selectedVideos.clear()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract Audio to MP3", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Add to Playlist Option
                        Button(
                            onClick = {
                                showSelectionMenu = false
                                showBatchAddToPlaylistSub = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add to Playlist", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Share Option
                        Button(
                            onClick = {
                                showSelectionMenu = false
                                val uris = selectedVideos.map { android.net.Uri.parse(it.uri) }
                                if (uris.isNotEmpty()) {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND_MULTIPLE
                                        type = "video/*"
                                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Videos")
                                    context.startActivity(shareIntent)
                                    selectedVideos.clear()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Selection", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

        // --- BATCH DELETE CONFIRMATION DIALOG ---
        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = { Text("Delete selected videos?") },
                text = { Text("This will permanently remove ${selectedVideos.size} files from your device storage.") },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedVideos.forEach { video ->
                                onDeleteVideo(video)
                            }
                            selectedVideos.clear()
                            showBatchDeleteConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- BATCH ADD TO PLAYLIST DIALOG ---
        if (showBatchAddToPlaylistSub) {
            var selectedPlaylistIdSub by remember { mutableStateOf<Int?>(null) }
            AlertDialog(
                onDismissRequest = { showBatchAddToPlaylistSub = false },
                title = {
                    Column {
                        Text("Save ${selectedVideos.size} files to playlist", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        if (playlists.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No playlists available yet.", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(onClick = { showNewPlaylistDialogForFolderBatch = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create Playlist")
                                }
                            }
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn {
                                items(playlists, key = { it.id }) { playlist ->
                                    val isPicked = selectedPlaylistIdSub == playlist.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedPlaylistIdSub = playlist.id }
                                            .padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = isPicked, onClick = { selectedPlaylistIdSub = playlist.id })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(playlist.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = selectedPlaylistIdSub != null,
                        onClick = {
                            selectedPlaylistIdSub?.let { pid ->
                                scope.launch {
                                    selectedVideos.forEach { video ->
                                        playlistDao.insertPlaylistItem(
                                            com.example.data.database.PlaylistItemEntity(
                                                playlistId = pid,
                                                title = video.title,
                                                uri = video.uri,
                                                duration = video.duration,
                                                size = video.size,
                                                thumbnailUri = video.thumbnailUri ?: ""
                                            )
                                        )
                                    }
                                    android.widget.Toast.makeText(context, "Added ${selectedVideos.size} items to Playlist!", android.widget.Toast.LENGTH_SHORT).show()
                                    selectedVideos.clear()
                                }
                            }
                            showBatchAddToPlaylistSub = false
                        }
                    ) {
                        Text("Add Selection")
                    }
                },
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playlists.isNotEmpty()) {
                            TextButton(onClick = { showNewPlaylistDialogForFolderBatch = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New List")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        TextButton(onClick = { showBatchAddToPlaylistSub = false }) {
                            Text("Close")
                        }
                    }
                }
            )
        }

        // --- SUB POPUP dialog: CREATE A PLAYLIST FOR BATCH ---
        if (showNewPlaylistDialogForFolderBatch) {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewPlaylistDialogForFolderBatch = false },
                title = { Text("New Playlist") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist Name") },
                        placeholder = { Text("e.g. Folder Tracks") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreatePlaylist(newName)
                                showNewPlaylistDialogForFolderBatch = false
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewPlaylistDialogForFolderBatch = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- EXTRACTING PROGRESS MODAL DIALOG ---
        if (isExtractingAudioState) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Extracting Audio Tracks") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Processing high quality MP3 conversion. Please do not close the app...", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = { }
            )
        }
    }
}


