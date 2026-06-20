package com.example.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistItemEntity
import com.example.data.repository.StatusMedia
import com.example.data.repository.VideoItem
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TabOption {
    object Local : TabOption
    object Statuses : TabOption
    object Folders : TabOption
    object Cloud : TabOption
}

data class RecentlyPlayedItem(
    val id: String,
    val title: String,
    val uri: String,
    val thumbnailUri: String?,
    val duration: Long,
    val progress: Long,
    val lastPlayedTime: Long
)

class VideoViewModel(private val repository: VideoRepository) : ViewModel() {

    // Bottom Navigation States
    private val _currentTab = MutableStateFlow<TabOption>(TabOption.Local)
    val currentTab: StateFlow<TabOption> = _currentTab.asStateFlow()

    // Playlist Selection for playlist detail views
    private val _activePlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val activePlaylist: StateFlow<PlaylistEntity?> = _activePlaylist.asStateFlow()

    private val _activePlaylistItems = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())
    val activePlaylistItems: StateFlow<List<PlaylistItemEntity>> = _activePlaylistItems.asStateFlow()

    // Media Data Streams
    private val _localVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val localVideos: StateFlow<List<VideoItem>> = _localVideos.asStateFlow()

    val cloudVideos: List<VideoItem> = repository.getSampleCloudVideos()

    val playlists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _statuses = MutableStateFlow<List<StatusMedia>>(emptyList())
    val statuses: StateFlow<List<StatusMedia>> = _statuses.asStateFlow()

    // Loading & Operation indicators
    private val _isRefreshingStatuses = MutableStateFlow(false)
    val isRefreshingStatuses: StateFlow<Boolean> = _isRefreshingStatuses.asStateFlow()

    private val _isScanningLocal = MutableStateFlow(false)
    val isScanningLocal: StateFlow<Boolean> = _isScanningLocal.asStateFlow()

    // Now Playing reference for active Media3 playback
    private val _nowPlaying = MutableStateFlow<VideoItem?>(null)
    val nowPlaying: StateFlow<VideoItem?> = _nowPlaying.asStateFlow()

    private val _directPlay = MutableStateFlow<Boolean>(false)
    val directPlay: StateFlow<Boolean> = _directPlay.asStateFlow()

    // Speed parameter persistence (0.25x to 2.0x)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Manage status saving response
    private val _saveStatusActionResult = MutableStateFlow<String?>(null)
    val saveStatusActionResult: StateFlow<String?> = _saveStatusActionResult.asStateFlow()

    // Add To Playlist dialog selection state
    private val _videoToAddToPlaylist = MutableStateFlow<VideoItem?>(null)
    val videoToAddToPlaylist: StateFlow<VideoItem?> = _videoToAddToPlaylist.asStateFlow()

    // Customization & History states
    private val _themeMode = MutableStateFlow("MY_THEME")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _customBgUri = MutableStateFlow<String?>(null)
    val customBgUri: StateFlow<String?> = _customBgUri.asStateFlow()

    private val _gallerySortOption = MutableStateFlow("NAME_ASC")
    val gallerySortOption: StateFlow<String> = _gallerySortOption.asStateFlow()

    private val _playerUiDesign = MutableStateFlow("MIDNIGHT")
    val playerUiDesign: StateFlow<String> = _playerUiDesign.asStateFlow()

    private val _seekBarDesign = MutableStateFlow("CLASSIC")
    val seekBarDesign: StateFlow<String> = _seekBarDesign.asStateFlow()

    private val _videoFilter = MutableStateFlow("ORIGINAL")
    val videoFilter: StateFlow<String> = _videoFilter.asStateFlow()

    private val _videoResizeMode = MutableStateFlow(0) // 0 is RESIZE_MODE_FIT
    val videoResizeMode: StateFlow<Int> = _videoResizeMode.asStateFlow()

    private val _videoGridSize = MutableStateFlow(2)
    val videoGridSize: StateFlow<Int> = _videoGridSize.asStateFlow()

    private val _uiCornerRadius = MutableStateFlow(12)
    val uiCornerRadius: StateFlow<Int> = _uiCornerRadius.asStateFlow()

    private val _isTransparentNav = MutableStateFlow(false)
    val isTransparentNav: StateFlow<Boolean> = _isTransparentNav.asStateFlow()

    private val _customFontUri = MutableStateFlow<String?>(null)
    val customFontUri: StateFlow<String?> = _customFontUri.asStateFlow()

    private val _isSubtitleEnabled = MutableStateFlow(true)
    val isSubtitleEnabled: StateFlow<Boolean> = _isSubtitleEnabled.asStateFlow()

    private val _subtitleHasBackground = MutableStateFlow(false)
    val subtitleHasBackground: StateFlow<Boolean> = _subtitleHasBackground.asStateFlow()

    private val _subtitleTextColor = MutableStateFlow(android.graphics.Color.WHITE)
    val subtitleTextColor: StateFlow<Int> = _subtitleTextColor.asStateFlow()

    private val _subtitleHasOutline = MutableStateFlow(true)
    val subtitleHasOutline: StateFlow<Boolean> = _subtitleHasOutline.asStateFlow()

    private val _folderVideosSortOption = MutableStateFlow("Date")
    val folderVideosSortOption: StateFlow<String> = _folderVideosSortOption.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    private val _playerBackgroundOpacity = MutableStateFlow(0.65f)
    val playerBackgroundOpacity: StateFlow<Float> = _playerBackgroundOpacity.asStateFlow()

    private val _navigationButtonOpacity = MutableStateFlow(0.15f)
    val navigationButtonOpacity: StateFlow<Float> = _navigationButtonOpacity.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<RecentlyPlayedItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<RecentlyPlayedItem>> = _recentlyPlayed.asStateFlow()

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
        _themeMode.value = prefs.getString("theme_mode", "MY_THEME") ?: "MY_THEME"
        _customBgUri.value = prefs.getString("custom_bg_uri", null)
        _gallerySortOption.value = prefs.getString("gallery_sort", "NAME_ASC") ?: "NAME_ASC"
        _playerUiDesign.value = prefs.getString("player_ui", "MIDNIGHT") ?: "MIDNIGHT"
        _playerBackgroundOpacity.value = prefs.getFloat("player_bg_opacity", 0.65f)
        _navigationButtonOpacity.value = prefs.getFloat("nav_btn_opacity", 0.15f)
        _seekBarDesign.value = prefs.getString("seekbar_design", "CLASSIC") ?: "CLASSIC"
        _videoFilter.value = prefs.getString("video_filter", "ORIGINAL") ?: "ORIGINAL"
        _videoResizeMode.value = prefs.getInt("video_resize_mode", 0)
        _videoGridSize.value = prefs.getInt("video_grid_size", 2)
        _uiCornerRadius.value = prefs.getInt("ui_corner_radius", 12)
        _isTransparentNav.value = prefs.getBoolean("is_transparent_nav", false)
        _customFontUri.value = prefs.getString("custom_font_uri", null)
        _isSubtitleEnabled.value = prefs.getBoolean("is_subtitle_enabled", true)
        _subtitleHasBackground.value = prefs.getBoolean("subtitle_has_bg", false)
        _subtitleTextColor.value = prefs.getInt("subtitle_text_color", android.graphics.Color.WHITE)
        _subtitleHasOutline.value = prefs.getBoolean("subtitle_has_outline", true)
        _folderVideosSortOption.value = prefs.getString("folder_videos_sort", "Date") ?: "Date"
        loadRecentlyPlayed(context)
    }

    fun setCustomBgUri(context: Context, uri: String?) {
        _customBgUri.value = uri
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("custom_bg_uri", uri)
            .apply()
    }

    fun setThemeMode(context: Context, mode: String) {
        _themeMode.value = mode
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", mode)
            .apply()
    }

    fun setGallerySortOption(context: Context, option: String) {
        _gallerySortOption.value = option
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("gallery_sort", option)
            .apply()
        sortLocalVideos()
    }

    fun setPlayerUiDesign(context: Context, design: String) {
        _playerUiDesign.value = design
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("player_ui", design)
            .apply()
    }

    fun setSeekBarDesign(context: Context, design: String) {
        _seekBarDesign.value = design
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("seekbar_design", design)
            .apply()
    }

    fun setFolderVideosSortOption(context: Context, option: String) {
        _folderVideosSortOption.value = option
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("folder_videos_sort", option)
            .apply()
    }

    fun setVideoFilter(context: Context, filter: String) {
        _videoFilter.value = filter
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("video_filter", filter)
            .apply()
    }

    fun setVideoResizeMode(context: Context, mode: Int) {
        _videoResizeMode.value = mode
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putInt("video_resize_mode", mode)
            .apply()
    }

    fun setVideoGridSize(context: Context, size: Int) {
        val boundedSize = size.coerceIn(1, 3)
        _videoGridSize.value = boundedSize
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putInt("video_grid_size", boundedSize)
            .apply()
    }

    fun setUiCornerRadius(context: Context, radius: Int) {
        _uiCornerRadius.value = radius
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putInt("ui_corner_radius", radius)
            .apply()
    }

    fun setCustomFontUri(context: Context, uri: String?) {
        _customFontUri.value = uri
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("custom_font_uri", uri)
            .apply()
    }

    fun setIsSubtitleEnabled(context: Context, isEnabled: Boolean) {
        _isSubtitleEnabled.value = isEnabled
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("is_subtitle_enabled", isEnabled)
            .apply()
    }

    fun setSubtitleHasBackground(context: Context, hasBackground: Boolean) {
        _subtitleHasBackground.value = hasBackground
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("subtitle_has_bg", hasBackground)
            .apply()
    }

    fun setSubtitleTextColor(context: Context, color: Int) {
        _subtitleTextColor.value = color
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putInt("subtitle_text_color", color)
            .apply()
    }

    fun setSubtitleHasOutline(context: Context, hasOutline: Boolean) {
        _subtitleHasOutline.value = hasOutline
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("subtitle_has_outline", hasOutline)
            .apply()
    }

    fun setIsTransparentNav(context: Context, isTransparent: Boolean) {
        _isTransparentNav.value = isTransparent
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("is_transparent_nav", isTransparent)
            .apply()
    }

    fun setPlayerBackgroundOpacity(context: Context, opacity: Float) {
        _playerBackgroundOpacity.value = opacity
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("player_bg_opacity", opacity)
            .apply()
    }

    fun setNavigationButtonOpacity(context: Context, opacity: Float) {
        val capped = opacity.coerceIn(0f, 1f)
        _navigationButtonOpacity.value = capped
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("nav_btn_opacity", capped)
            .apply()
    }

    private fun sortLocalVideos() {
        val currentList = _localVideos.value
        val option = _gallerySortOption.value
        val sorted = when (option) {
            "NAME_ASC", "TITLE_ASC" -> currentList.sortedBy { it.title.lowercase() }
            "NAME_DESC", "TITLE_DESC" -> currentList.sortedByDescending { it.title.lowercase() }
            "DURATION_ASC" -> currentList.sortedBy { it.duration }
            "DURATION_DESC" -> currentList.sortedByDescending { it.duration }
            "SIZE_DESC" -> currentList.sortedByDescending { it.size }
            "SIZE_ASC" -> currentList.sortedBy { it.size }
            "DATE_DESC" -> currentList.sortedByDescending { it.dateAdded }
            "DATE_ASC" -> currentList.sortedBy { it.dateAdded }
            else -> currentList
        }
        _localVideos.value = sorted
    }

    fun loadRecentlyPlayed(context: Context) {
        val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("recently_played_list", "") ?: ""
        val items = deserializeRecentlyPlayed(serialized)
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val filtered = items.filter { now - it.lastPlayedTime <= sevenDaysMs }.take(10)
        _recentlyPlayed.value = filtered
    }

    fun recordVideoPlayback(context: Context, video: VideoItem, progress: Long, duration: Long) {
        val currentList = _recentlyPlayed.value.toMutableList()
        val index = currentList.indexOfFirst { it.uri == video.uri }
        
        val actualProgress = if (duration > 0 && duration - progress <= 5000L) 0L else progress

        val newItem = RecentlyPlayedItem(
            id = video.id,
            title = video.title,
            uri = video.uri,
            thumbnailUri = video.thumbnailUri,
            duration = duration,
            progress = actualProgress,
            lastPlayedTime = System.currentTimeMillis()
        )
        
        if (index != -1) {
            currentList.removeAt(index)
        }
        currentList.add(0, newItem)
        
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val filtered = currentList.filter { now - it.lastPlayedTime <= sevenDaysMs }.take(10)
        
        _recentlyPlayed.value = filtered
        
        val serialized = serializeRecentlyPlayed(filtered)
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .putString("recently_played_list", serialized)
            .apply()
    }

    fun clearRecentlyPlayed(context: Context) {
        _recentlyPlayed.value = emptyList()
        context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE).edit()
            .remove("recently_played_list")
            .apply()
    }

    private fun serializeRecentlyPlayed(items: List<RecentlyPlayedItem>): String {
        return items.joinToString(separator = "##ITEM##") { item ->
            listOf(
                item.id,
                item.title,
                item.uri,
                item.thumbnailUri ?: "",
                item.duration.toString(),
                item.progress.toString(),
                item.lastPlayedTime.toString()
            ).joinToString(separator = "##FIELD##")
        }
    }

    private fun deserializeRecentlyPlayed(serialized: String): List<RecentlyPlayedItem> {
        if (serialized.isBlank()) return emptyList()
        return try {
            serialized.split("##ITEM##").mapNotNull { itemStr ->
                val fields = itemStr.split("##FIELD##")
                if (fields.size >= 7) {
                    RecentlyPlayedItem(
                        id = fields[0],
                        title = fields[1],
                        uri = fields[2],
                        thumbnailUri = fields[3].ifEmpty { null },
                        duration = fields[4].toLongOrNull() ?: 0L,
                        progress = fields[5].toLongOrNull() ?: 0L,
                        lastPlayedTime = fields[6].toLongOrNull() ?: 0L
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun selectTab(tab: TabOption) {
        _currentTab.value = tab
        // If switching tab, close playlist details safely
        _activePlaylist.value = null
    }

    fun selectPlaylist(playlist: PlaylistEntity?) {
        _activePlaylist.value = playlist
        if (playlist != null) {
            viewModelScope.launch {
                repository.getItemsForPlaylist(playlist.id).collect { items ->
                    _activePlaylistItems.value = items
                }
            }
        } else {
            _activePlaylistItems.value = emptyList()
        }
    }

    fun scanDeviceVideos(context: Context) {
        viewModelScope.launch {
            _isScanningLocal.value = true
            _localVideos.value = emptyList() // Removes all loaded videos immediately
            val results = repository.scanLocalVideos(context)
            _localVideos.value = results
            sortLocalVideos()
            _isScanningLocal.value = false
        }
    }

    fun refreshWhatsAppStatuses(context: Context) {
        viewModelScope.launch {
            _isRefreshingStatuses.value = true
            _statuses.value = emptyList()
            val results = repository.getWhatsAppStatuses(context)
            _statuses.value = results
            _isRefreshingStatuses.value = false
        }
    }

    private var playingQueueContext: String? = null

    fun startPlaying(video: VideoItem, queueContext: String? = null, directPlay: Boolean = false) {
        playingQueueContext = queueContext
        _directPlay.value = directPlay
        _nowPlaying.value = video
    }

    fun stopPlaying() {
        _nowPlaying.value = null
        playingQueueContext = null
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    // Database operation actions
    fun createPlaylist(name: String, desc: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.createPlaylist(name, desc)
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            if (_activePlaylist.value?.id == playlist.id) {
                _activePlaylist.value = null
            }
        }
    }

    fun requestAddToPlaylist(video: VideoItem?) {
        _videoToAddToPlaylist.value = video
    }

    fun confirmAddToPlaylist(playlistId: Int, video: VideoItem) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, video)
            _videoToAddToPlaylist.value = null
        }
    }

    fun removeFromPlaylist(itemId: Int) {
        viewModelScope.launch {
            repository.deletePlaylistItem(itemId)
            // Re-fetch items if playlist is selected
            val active = _activePlaylist.value
            if (active != null) {
                repository.getItemsForPlaylist(active.id).collect { items ->
                    _activePlaylistItems.value = items
                }
            }
        }
    }

    // Status saving action
    fun saveStatus(context: Context, status: StatusMedia) {
        viewModelScope.launch {
            val success = repository.saveStatusToLocal(context, status)
            if (success) {
                _saveStatusActionResult.value = "Status Saved successfully to Gallery!"
                Toast.makeText(context, "Saved to Movies folder!", Toast.LENGTH_SHORT).show()
                // Refresh status items to update the save icon
                refreshWhatsAppStatuses(context)
            } else {
                _saveStatusActionResult.value = "Failed saving status to local storage."
                Toast.makeText(context, "Failed to save status media.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun renameLocalVideo(context: Context, video: VideoItem, newName: String) {
        viewModelScope.launch {
            val success = repository.renameLocalFile(context, video, newName)
            if (success) {
                Toast.makeText(context, "Video renamed to $newName", Toast.LENGTH_SHORT).show()
                scanDeviceVideos(context)
            } else {
                Toast.makeText(context, "Failed to rename video.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteLocalVideo(context: Context, video: VideoItem) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && 
            !android.os.Environment.isExternalStorageManager()) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please enable 'All Files Access' to delete videos", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please enable 'All Files Access' to delete videos", Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    Toast.makeText(context, "All Files Access permission is required to delete videos.", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        viewModelScope.launch {
            val success = repository.deleteLocalFile(context, video)
            if (success) {
                Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                scanDeviceVideos(context)
            } else {
                Toast.makeText(context, "Failed to delete video.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun renameStatus(context: Context, status: StatusMedia, newName: String) {
        viewModelScope.launch {
            val success = repository.renameStatusFile(context, status, newName)
            if (success) {
                Toast.makeText(context, "Status renamed to $newName", Toast.LENGTH_SHORT).show()
                refreshWhatsAppStatuses(context)
            } else {
                Toast.makeText(context, "Failed to rename status.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteStatus(context: Context, status: StatusMedia) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && 
            !android.os.Environment.isExternalStorageManager()) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please enable 'All Files Access' to delete statuses", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please enable 'All Files Access' to delete statuses", Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    Toast.makeText(context, "All Files Access permission is required to delete statuses.", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        viewModelScope.launch {
            val success = repository.deleteStatusFile(context, status)
            if (success) {
                Toast.makeText(context, "Status deleted", Toast.LENGTH_SHORT).show()
                refreshWhatsAppStatuses(context)
            } else {
                Toast.makeText(context, "Failed to delete status.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearSaveStatusNotification() {
        _saveStatusActionResult.value = null
    }

    // Auto Play Next Video switch state
    private val _autoPlayNext = MutableStateFlow(true)
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
    }

    fun hasNextVideo(): Boolean {
        val current = _nowPlaying.value ?: return false
        val list = getPlayingQueue(current)
        if (list.isEmpty()) return false
        val currentIndex = list.indexOfFirst { it.uri == current.uri }
        return currentIndex != -1 && currentIndex < list.size - 1
    }

    fun hasPreviousVideo(): Boolean {
        val current = _nowPlaying.value ?: return false
        val list = getPlayingQueue(current)
        if (list.isEmpty()) return false
        val currentIndex = list.indexOfFirst { it.uri == current.uri }
        return currentIndex > 0
    }

    fun playNextVideo() {
        val current = _nowPlaying.value ?: return
        val list = getPlayingQueue(current)
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.uri == current.uri }
        if (currentIndex != -1 && currentIndex < list.size - 1) {
            _nowPlaying.value = list[currentIndex + 1]
        }
    }

    fun playPreviousVideo() {
        val current = _nowPlaying.value ?: return
        val list = getPlayingQueue(current)
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.uri == current.uri }
        if (currentIndex > 0) {
            _nowPlaying.value = list[currentIndex - 1]
        }
    }

    private fun getPlayingQueue(current: VideoItem): List<VideoItem> {
        // 0. If played from a specific folder context
        if (playingQueueContext?.startsWith("folder:") == true) {
            val folderName = playingQueueContext!!.substringAfter("folder:")
            return _localVideos.value.filter {
                val pName = java.io.File(it.uri).parentFile?.name ?: "Videos"
                if (folderName == "Downloads") {
                    it.id.startsWith("sim_")
                } else {
                    pName == folderName && !it.id.startsWith("sim_")
                }
            }.let { filtered ->
                val option = _folderVideosSortOption.value
                when (option) {
                    "Date" -> filtered.sortedByDescending { it.dateAdded }
                    "Title" -> filtered.sortedBy { it.title.lowercase() }
                    "Size" -> filtered.sortedByDescending { it.size }
                    else -> filtered
                }
            }
        }

        // 1. If it explicitly starts with a playlist prefix
        if (current.id.startsWith("playlist_")) {
            return _activePlaylistItems.value.map { item ->
                VideoItem(
                    id = "playlist_${item.id}",
                    title = item.title,
                    uri = item.uri,
                    duration = item.duration,
                    size = item.size,
                    thumbnailUri = item.thumbnailUri,
                    author = "My Saved Collection"
                )
            }
        }

        // 2. Scan available queues for a match by URI first, ensuring history/recent selections locate correct contexts
        if (_localVideos.value.any { it.uri == current.uri }) {
            return _localVideos.value
        }

        val statusList = _statuses.value.filter { it.isVideo }.map { status ->
            VideoItem(
                id = status.id,
                title = status.path.substringAfterLast('/'),
                uri = status.simulatedUrl ?: status.uri,
                mimeType = "video/mp4",
                isLocal = false,
                duration = 30000L,
                size = status.size
            )
        }
        if (statusList.any { it.uri == current.uri }) {
            return statusList
        }

        if (cloudVideos.any { it.uri == current.uri }) {
            return cloudVideos
        }

        // 3. Fallback based on original traits or defaults
        return if (current.isLocal) _localVideos.value else cloudVideos
    }
}

class VideoViewModelFactory(private val repository: VideoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
