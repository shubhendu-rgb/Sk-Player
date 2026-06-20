package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.repository.VideoItem
import com.example.ui.viewmodel.RecentlyPlayedItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalVideosTab(
    localVideos: List<VideoItem>,
    isScanning: Boolean,
    recentlyPlayedList: List<RecentlyPlayedItem>,
    onVideoPlay: (VideoItem) -> Unit,
    onRecentlyPlayedPlay: (RecentlyPlayedItem) -> Unit,
    onAddToPlaylist: (VideoItem) -> Unit,
    onTriggerScan: () -> Unit,
    onRenameVideo: (VideoItem, String) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    videoGridSize: Int = 2,
    uiCornerRadius: Int = 12,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Database and Playlists access
    val db = remember { com.example.data.database.AppDatabase.getDatabase(context) }
    val playlistDao = remember { db.playlistDao() }
    val playlistsFlow = remember { playlistDao.getAllPlaylists() }
    val playlists by playlistsFlow.collectAsState(initial = emptyList())

    // Selection State
    val selectedVideos = remember { mutableStateListOf<VideoItem>() }
    val isSelectionMode = selectedVideos.isNotEmpty()

    // Dialog & Flow States
    var showSelectionMenu by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchAddToPlaylistSub by remember { mutableStateOf(false) }
    var isExtractingAudioState by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = isSelectionMode) {
        selectedVideos.clear()
    }

    // Blur check logic for background
    val isBlurred = showSelectionMenu || showBatchRenameDialog || showBatchDeleteConfirm || showBatchAddToPlaylistSub || isExtractingAudioState || showNewPlaylistDialog

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (isScanning && localVideos.isEmpty()) {
            // Scanning Indicator
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Scanning storage for videos...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        } else if (localVideos.isEmpty()) {
            // Empty state view
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "No Videos found",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No local videos found.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Give permission or trigger storage scans.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onTriggerScan) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Storage")
                }
            }
        } else {
            // Main content column (blurred recursively when menu/popups are shown)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (isBlurred) 16.dp else 0.dp)
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
                                text = if (isSelectionMode) "${selectedVideos.size} selected" else "${localVideos.size} files discovered",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontWeight = if (isSelectionMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Show deselect button if selection mode
                        if (isSelectionMode) {
                            IconButton(
                                onClick = { selectedVideos.clear() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Deselect All",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Three dotted icon on top right near reload icon - shown when videos are selected
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
                        }

                        IconButton(
                            onClick = onTriggerScan,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Store",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val gridState = rememberLazyGridState()

                val uniqueVideos = remember(localVideos) {
                    val seen = mutableSetOf<String>()
                    localVideos.filter { seen.add(it.id) }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(videoGridSize),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 150.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!isSelectionMode && recentlyPlayedList.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            RecentlyPlayedBar(
                                recentlyPlayedList = recentlyPlayedList,
                                onItemClick = onRecentlyPlayedPlay,
                                uiCornerRadius = uiCornerRadius,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    items(uniqueVideos, key = { it.id }) { video ->
                        val isSelected = selectedVideos.any { it.id == video.id }
                        val currentProgress = recentlyPlayedList.find { it.uri == video.uri }?.progress ?: 0L
                        VideoCard(
                            video = video,
                            isSelected = isSelected,
                            uiCornerRadius = uiCornerRadius,
                            progress = currentProgress,
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) {
                                        selectedVideos.removeAll { it.id == video.id }
                                    } else {
                                        selectedVideos.add(video)
                                    }
                                } else {
                                    onVideoPlay(video)
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
                    }
                }
            }
        }

        // --- CUSTOM BLURRED OVERLAY INTERFACE POPUP ---
        if (showSelectionMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showSelectionMenu = false }
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(300.dp)
                        .clickable(enabled = false) { }
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
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

                        // 2. Delete Option
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

                        // 3. Extract Audio Option
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract Audio to MP3", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Add to Playlist Option
                        Button(
                            onClick = {
                                showSelectionMenu = false
                                showBatchAddToPlaylistSub = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add to Playlist", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Share Option
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

                        TextButton(
                            onClick = { showSelectionMenu = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- SUB POPUP dialog: RENAME ---
        if (showBatchRenameDialog) {
            val firstVideo = selectedVideos.firstOrNull()
            if (firstVideo != null) {
                var renameText by remember(firstVideo) { mutableStateOf(firstVideo.title.substringBeforeLast(".")) }
                AlertDialog(
                    onDismissRequest = { showBatchRenameDialog = false },
                    shape = RoundedCornerShape(uiCornerRadius.dp),
                    title = { Text("Rename Selected items") },
                    text = {
                        Column {
                            Text("Enter new name. If multiple items are selected, sequential suffixes will be added.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                label = { Text("File Name Prefix") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (renameText.isNotBlank()) {
                                    selectedVideos.forEachIndexed { index, videoItem ->
                                        val finalName = if (selectedVideos.size > 1) {
                                            "${renameText}_${index + 1}"
                                        } else {
                                            renameText
                                        }
                                        onRenameVideo(videoItem, finalName)
                                    }
                                    selectedVideos.clear()
                                }
                                showBatchRenameDialog = false
                            }
                        ) {
                            Text("Rename")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatchRenameDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            } else {
                showBatchRenameDialog = false
            }
        }

        // --- SUB POPUP dialog: DELETE CONFIRMATION ---
        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                shape = RoundedCornerShape(uiCornerRadius.dp),
                title = { Text("Confirm Batch Deletion") },
                text = { Text("Are you sure you want to permanently delete these ${selectedVideos.size} video files from storage?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            selectedVideos.forEach { video ->
                                onDeleteVideo(video)
                            }
                            selectedVideos.clear()
                            showBatchDeleteConfirm = false
                        }
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- SUB POPUP dialog: BATCH SAVE TO PLAYLIST ---
        if (showBatchAddToPlaylistSub) {
            var selectedPlaylistIdSub by remember { mutableStateOf<Int?>(null) }
            AlertDialog(
                onDismissRequest = { showBatchAddToPlaylistSub = false },
                shape = RoundedCornerShape(uiCornerRadius.dp),
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
                                Button(onClick = { showNewPlaylistDialog = true }) {
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
                            TextButton(onClick = { showNewPlaylistDialog = true }) {
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

        // --- SUB POPUP dialog: CREATE A PLAYLIST ---
        if (showNewPlaylistDialog) {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewPlaylistDialog = false },
                shape = RoundedCornerShape(uiCornerRadius.dp),
                title = { Text("New Playlist Name") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist Name") },
                        placeholder = { Text("e.g. My Favorites") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                scope.launch {
                                    playlistDao.insertPlaylist(
                                        com.example.data.database.PlaylistEntity(
                                            name = newName,
                                            description = "Quick discovery playlist"
                                        )
                                    )
                                }
                                newName = ""
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

        // --- EXTRACING PROGRESS MODAL DIALOG ---
        if (isExtractingAudioState) {
            AlertDialog(
                onDismissRequest = { },
                shape = RoundedCornerShape(uiCornerRadius.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: VideoItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    uiCornerRadius: Int = 16,
    progress: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cachedThumbnailPath by remember(video.id, video.uri) { mutableStateOf<String?>(null) }
    var thumbnailLoaded by remember(video.id, video.uri) { mutableStateOf(false) }
    LaunchedEffect(video.id, video.uri) {
        cachedThumbnailPath = com.example.ThumbnailCacheManager.getOrCreateThumbnail(context, video.id, video.uri)
        thumbnailLoaded = true
    }
    val finalThumb = if (thumbnailLoaded) (cachedThumbnailPath ?: video.thumbnailUri) else null

    val durationText = remember(video.duration) { formatDuration(video.duration) }
    val formatText = remember(video) { getVideoFormatExtension(video) }
    val sizeText = remember(video.size) { formatSize(video.size) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(uiCornerRadius.dp)
            ),
        shape = RoundedCornerShape(uiCornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                // Async image for thumbnail if present, else fallback
                if (finalThumb != null) {
                    AsyncImage(
                        model = finalThumb,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Modern styled gradient display
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                                        Color.Black.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )
                }

                // Playback progress bar
                if (progress > 0 && video.duration > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }

                // Foreground overlay with duration tag, play action, and selection mark
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // Check mark circle displayed top-right purely when selected
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected Indicator",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(16.dp)
                            )
                        }
                    }

                    // Duration pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Play icon shown center when NOT selected
                    if (!isSelected) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Playback button",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            // Description Details
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Video container format label
                    Surface(
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = formatText,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
fun RecentlyPlayedBar(
    recentlyPlayedList: List<RecentlyPlayedItem>,
    onItemClick: (RecentlyPlayedItem) -> Unit,
    uiCornerRadius: Int = 12,
    modifier: Modifier = Modifier
) {
    if (recentlyPlayedList.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(uiCornerRadius.dp)
            )
            .clip(RoundedCornerShape(uiCornerRadius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse History" else "Expand History",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            val uniqueRecentlyPlayed = remember(recentlyPlayedList) {
                val seen = mutableSetOf<String>()
                recentlyPlayedList.filter { seen.add(it.uri) }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uniqueRecentlyPlayed, key = { it.uri }) { item ->
                    RecentlyPlayedCard(item = item, onClick = { onItemClick(item) }, uiCornerRadius = uiCornerRadius)
                }
            }
        }
    }
}

@Composable
fun RecentlyPlayedCard(
    item: RecentlyPlayedItem,
    onClick: () -> Unit,
    uiCornerRadius: Int = 12,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cachedThumbnailPath by remember(item.id, item.uri) { mutableStateOf<String?>(null) }
    var thumbnailLoaded by remember(item.id, item.uri) { mutableStateOf(false) }
    LaunchedEffect(item.id, item.uri) {
        cachedThumbnailPath = com.example.ThumbnailCacheManager.getOrCreateThumbnail(context, item.id, item.uri)
        thumbnailLoaded = true
    }
    val finalThumb = if (thumbnailLoaded) (cachedThumbnailPath ?: item.thumbnailUri) else null

    Card(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(uiCornerRadius.dp)
            ),
        shape = RoundedCornerShape(uiCornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (finalThumb != null) {
                    AsyncImage(
                        model = finalThumb,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                                        Color.Black.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )
                }

                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .align(Alignment.Center)
                )
            }

            val progressPercent = if (item.duration > 0) item.progress.toFloat() / item.duration else 0f
            LinearProgressIndicator(
                progress = { progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${formatDurationPart(item.progress)} / ${formatDurationPart(item.duration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun formatDurationPart(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// Extract file format extension cleanly
private fun getVideoFormatExtension(video: VideoItem): String {
    val uriStr = video.uri.lowercase()
    return when {
        uriStr.endsWith(".mkv") || video.mimeType.contains("matroska") -> "MKV"
        uriStr.endsWith(".webm") || video.mimeType.contains("webm") -> "WEBM"
        uriStr.endsWith(".m3u8") || video.mimeType.contains("mpegurl") -> "HLS"
        uriStr.endsWith(".mov") || video.mimeType.contains("quicktime") -> "MOV"
        uriStr.endsWith(".avi") || video.mimeType.contains("avi") || video.mimeType.contains("x-msvideo") -> "AVI"
        else -> "MP4"
    }
}

// Convert bytes to clean representation string
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "N/A"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb > 1) {
        String.format("%.1f MB", mb)
    } else {
        String.format("%.0f KB", kb)
    }
}

// Convert duration ms to clean MM:SS format
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}
