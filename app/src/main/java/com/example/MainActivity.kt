package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppDatabase
import com.example.data.repository.VideoRepository
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TabOption
import com.example.ui.viewmodel.VideoViewModel
import com.example.ui.viewmodel.VideoViewModelFactory
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import kotlinx.coroutines.delay

@Composable
fun AnimatedSplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2000L) // Show splash for 2 seconds
        onSplashFinished()
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 0.5f,
        animationSpec = tween(durationMillis = 1000)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Sk Player",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer(
                    alpha = alphaAnim,
                    scaleX = scaleAnim,
                    scaleY = scaleAnim
                )
            )
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: VideoViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.playlistDao()
        val repository = VideoRepository(dao)
        VideoViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle incoming intent
        handleIntent(intent)

        // Request higher refresh rate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var maxRefreshRate = 0f
            var bestModeId = 0
            val modes = window.windowManager.defaultDisplay.supportedModes
            for (mode in modes) {
                if (mode.refreshRate > maxRefreshRate) {
                    maxRefreshRate = mode.refreshRate
                    bestModeId = mode.modeId
                }
            }
            if (bestModeId != 0) {
                val params = window.attributes
                params.preferredDisplayModeId = bestModeId
                window.attributes = params
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val maxRate = window.context.display?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate ?: 0f
            if (maxRate > 0) window.attributes.preferredRefreshRate = maxRate
        }
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        enableEdgeToEdge()

        viewModel.loadPreferences(applicationContext)

        try {
            val intent = android.content.Intent(this, AppCleanupService::class.java)
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val customFontUri by viewModel.customFontUri.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeMode, customFontUri = customFontUri) {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    AnimatedSplashScreen(onSplashFinished = { showSplash = false })
                } else {
                    MainContentScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val fileName = try {
                    var name = uri.lastPathSegment ?: "External Video"
                    if (uri.scheme == "content") {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    name = cursor.getString(nameIndex)
                                }
                            }
                        }
                    }
                    name
                } catch (e: Exception) {
                    "External Video"
                }

                val video = com.example.data.repository.VideoItem(
                    id = uri.hashCode().toString(),
                    uri = uri.toString(),
                    title = fileName,
                    duration = 0L,
                    size = 0L,
                    dateAdded = System.currentTimeMillis()
                )
                viewModel.startPlaying(video, "external:", true)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setInPipMode(isInPictureInPictureMode)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val isUp = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> true
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> null
        }
        if (isUp != null) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                val handled = com.example.ui.components.PlayerVolumeKeyHandler.onVolumeKey?.invoke(isUp) == true
                if (handled) return true
            } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                if (com.example.ui.components.PlayerVolumeKeyHandler.onVolumeKey != null) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(12345)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainContentScreen(viewModel: VideoViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
    val activePlaylistItems by viewModel.activePlaylistItems.collectAsStateWithLifecycle()
    val localVideos by viewModel.localVideos.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val isScanningLocal by viewModel.isScanningLocal.collectAsStateWithLifecycle()
    val isRefreshingStatuses by viewModel.isRefreshingStatuses.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val videoToAddToPlaylist by viewModel.videoToAddToPlaylist.collectAsStateWithLifecycle()
    val autoPlayNext by viewModel.autoPlayNext.collectAsStateWithLifecycle()

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val gallerySortOption by viewModel.gallerySortOption.collectAsStateWithLifecycle()
    val playerUiDesign by viewModel.playerUiDesign.collectAsStateWithLifecycle()
    val seekBarDesign by viewModel.seekBarDesign.collectAsStateWithLifecycle()
    val videoFilter by viewModel.videoFilter.collectAsStateWithLifecycle()
    val videoResizeMode by viewModel.videoResizeMode.collectAsStateWithLifecycle()
    val videoGridSize by viewModel.videoGridSize.collectAsStateWithLifecycle()
    val uiCornerRadius by viewModel.uiCornerRadius.collectAsStateWithLifecycle()
    val isTransparentNav by viewModel.isTransparentNav.collectAsStateWithLifecycle()
    val customFontUri by viewModel.customFontUri.collectAsStateWithLifecycle()
    val isSubtitleEnabled by viewModel.isSubtitleEnabled.collectAsStateWithLifecycle()
    val subtitleHasBackground by viewModel.subtitleHasBackground.collectAsStateWithLifecycle()
    val subtitleTextColor by viewModel.subtitleTextColor.collectAsStateWithLifecycle()
    val subtitleHasOutline by viewModel.subtitleHasOutline.collectAsStateWithLifecycle()
    val folderVideosSortOption by viewModel.folderVideosSortOption.collectAsStateWithLifecycle()
    val playerBackgroundOpacity by viewModel.playerBackgroundOpacity.collectAsStateWithLifecycle()
    val navigationButtonOpacity by viewModel.navigationButtonOpacity.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val isInPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()

    // Slide options drawer parameters
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentActivity = LocalContext.current as? android.app.Activity
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Double back to exit & back one time goes to gallery screen
    if (nowPlaying == null) {
        BackHandler {
            if (videoToAddToPlaylist != null) {
                viewModel.requestAddToPlaylist(null)
            } else if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else if (currentTab !is TabOption.Local) {
                viewModel.selectTab(TabOption.Local)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000L) {
                    currentActivity?.finish()
                } else {
                    lastBackPressTime = currentTime
                    android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Determine platform-safe media permissions per SDK version
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    } else {
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    // SAF Directory Picker to select custom WhatsApp Status folders
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Log permission registration exception but don't break operation
            }
            
            val prefs = context.getSharedPreferences("playstatus_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("whatsapp_saf_uri", uri.toString()).apply()
            
            viewModel.refreshWhatsAppStatuses(context)
        }
    }

    // Automatically trigger media library indexing when app starts
    LaunchedEffect(Unit) {
        viewModel.scanDeviceVideos(context)
        viewModel.refreshWhatsAppStatuses(context)
    }

    // Automatically trigger media library indexing if files permissions are already granted
    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            viewModel.scanDeviceVideos(context)
            viewModel.refreshWhatsAppStatuses(context)
        }
    }

    val customBgUri by viewModel.customBgUri.collectAsStateWithLifecycle()

    val hazeState = remember { HazeState() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                modifier = Modifier.width(310.dp)
            ) {
                SidebarCustomizationContent(
                    themeMode = themeMode,
                    onThemeChange = { viewModel.setThemeMode(context, it) },
                    sortOption = gallerySortOption,
                    onSortOptionChange = { viewModel.setGallerySortOption(context, it) },
                    playerDesign = playerUiDesign,
                    onPlayerDesignChange = { viewModel.setPlayerUiDesign(context, it) },
                    seekBarDesign = seekBarDesign,
                    onSeekBarDesignChange = { viewModel.setSeekBarDesign(context, it) },
                    videoGridSize = videoGridSize,
                    onVideoGridSizeChange = { viewModel.setVideoGridSize(context, it) },
                    uiCornerRadius = uiCornerRadius,
                    onUiCornerRadiusChange = { viewModel.setUiCornerRadius(context, it) },
                    isTransparentNav = isTransparentNav,
                    onIsTransparentNavChange = { viewModel.setIsTransparentNav(context, it) },
                    playerBackgroundOpacity = playerBackgroundOpacity,
                    onPlayerBackgroundOpacityChange = { viewModel.setPlayerBackgroundOpacity(context, it) },
                    navigationButtonOpacity = navigationButtonOpacity,
                    onNavigationButtonOpacityChange = { viewModel.setNavigationButtonOpacity(context, it) },
                    customBgUri = customBgUri,
                    onCustomBgChange = { viewModel.setCustomBgUri(context, it) },
                    customFontUri = customFontUri,
                    onCustomFontChange = { viewModel.setCustomFontUri(context, it) },
                    hasSubtitleBackground = subtitleHasBackground,
                    onHasSubtitleBackgroundChange = { viewModel.setSubtitleHasBackground(context, it) },
                    subtitleTextColor = subtitleTextColor,
                    onSubtitleTextColorChange = { viewModel.setSubtitleTextColor(context, it) },
                    hasSubtitleOutline = subtitleHasOutline,
                    onHasSubtitleOutlineChange = { viewModel.setSubtitleHasOutline(context, it) },
                    onClearHistory = { viewModel.clearRecentlyPlayed(context) },
                    onCloseDrawer = { scope.launch { drawerState.close() } }
                )
            }
        },
        content = {
            val isDrawerOpen = drawerState.isOpen || drawerState.isAnimationRunning
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .then(
                        if (isDrawerOpen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(16.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (customBgUri != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = rememberAsyncImagePainter(model = customBgUri),
                            contentDescription = "Custom Background Wallpaper",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = (1.0f - (playerBackgroundOpacity * 0.70f)).coerceIn(0.10f, 1.00f)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = (playerBackgroundOpacity * 0.85f).coerceIn(0.00f, 0.95f)))
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize().haze(state = hazeState),
                    containerColor = Color.Transparent,
                     bottomBar = {
                        // Persistent custom bottom navigation bar
                        val containerBg = if (isTransparentNav) MaterialTheme.colorScheme.surface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primaryContainer
                        val hazeModifier = if (isTransparentNav) Modifier.hazeChild(state = hazeState, style = dev.chrisbanes.haze.HazeStyle(blurRadius = 24.dp, backgroundColor = Color.Transparent, tint = null as dev.chrisbanes.haze.HazeTint?)) else Modifier
                        val borderColor = MaterialTheme.colorScheme.primary

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(uiCornerRadius.dp))
                                .background(containerBg)
                                .then(hazeModifier)
                                .border(
                                    width = 1.dp,
                                    color = borderColor,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(uiCornerRadius.dp)
                                )
                        ) {
                            if (isTransparentNav) {
                                // Backdrop Layer with transparent Background
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                )

                                // Glowing Neon Cyan & Violet Edge Accent
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .height(1.5.dp)
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0xFF00F0FF).copy(alpha = 0.6f),
                                                    Color(0xFF8A2BE2).copy(alpha = 0.6f),
                                                    Color(0xFF00F0FF).copy(alpha = 0.6f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }

                            // Interactive Layer (kept perfectly crisp and sharp)
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentTab is TabOption.Local,
                                    onClick = { viewModel.selectTab(TabOption.Local) },
                                    label = { Text("Gallery") },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab is TabOption.Local) Icons.Default.Folder else Icons.Default.FolderOpen,
                                            contentDescription = "Gallery tab controller"
                                        )
                                    }
                                )
                                NavigationBarItem(
                                    selected = currentTab is TabOption.Statuses,
                                    onClick = { viewModel.selectTab(TabOption.Statuses) },
                                    label = { Text("Status Saver") },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab is TabOption.Statuses) Icons.Default.CloudDownload else Icons.Default.CloudDownload,
                                            contentDescription = "Statuses preservation saver"
                                        )
                                    }
                                )
                                NavigationBarItem(
                                    selected = currentTab is TabOption.Folders,
                                    onClick = { viewModel.selectTab(TabOption.Folders) },
                                    label = { Text("Folders") },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab is TabOption.Folders) Icons.Default.FolderOpen else Icons.Default.Folder,
                                            contentDescription = "Folders segment page selector"
                                        )
                                    }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (nowPlaying == null && recentlyPlayed.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    recentlyPlayed.firstOrNull()?.let { recent ->
                                        val videoItem = com.example.data.repository.VideoItem(
                                            id = recent.id,
                                            title = recent.title,
                                            uri = recent.uri,
                                            duration = recent.duration,
                                            size = 0L,
                                            mimeType = "video/mp4",
                                            isLocal = true,
                                            thumbnailUri = recent.thumbnailUri,
                                            dateAdded = recent.lastPlayedTime
                                        )
                                        viewModel.startPlaying(videoItem, directPlay = true)
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume play icon"
                                    )
                                },
                                text = {
                                    Text("Resume Play")
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                            )
                    ) {
                        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                        var hasAllFilesAccess by remember {
                            mutableStateOf(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    android.os.Environment.isExternalStorageManager()
                                } else {
                                    true
                                }
                            )
                        }

                        DisposableEffect(lifecycle) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        android.os.Environment.isExternalStorageManager()
                                    } else {
                                        true
                                    }
                                }
                            }
                            lifecycle.addObserver(observer)
                            onDispose {
                                lifecycle.removeObserver(observer)
                            }
                        }

                        val allGranted = permissionState.allPermissionsGranted && hasAllFilesAccess

                        if (!allGranted) {
                            // Elevated Permission Greeting/Pitch Overlay
                            RequestPermissionsBanner(
                                permissionState = permissionState,
                                hasAllFilesAccess = hasAllFilesAccess,
                                onRequestAllFilesAccess = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                context.startActivity(intent)
                                            } catch (ex: Exception) {
                                                android.widget.Toast.makeText(context, "Failed to open settings.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        } else {
                            // Fluid state transitions based on tab selection
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "TabTransition"
                            ) { tab ->
                                when (tab) {
                                    TabOption.Local -> {
                                        LocalVideosTab(
                                            localVideos = localVideos,
                                            isScanning = isScanningLocal,
                                            recentlyPlayedList = recentlyPlayed,
                                            onVideoPlay = { viewModel.startPlaying(it) },
                                            onRecentlyPlayedPlay = { item ->
                                                viewModel.startPlaying(
                                                    com.example.data.repository.VideoItem(
                                                        id = "recent_${item.uri.hashCode()}",
                                                        title = item.title,
                                                        uri = item.uri,
                                                        duration = item.duration,
                                                        size = 0L,
                                                        thumbnailUri = item.thumbnailUri,
                                                        author = "Playback History"
                                                    ),
                                                    directPlay = true
                                                )
                                            },
                                            onAddToPlaylist = { viewModel.requestAddToPlaylist(it) },
                                            onTriggerScan = { viewModel.scanDeviceVideos(context) },
                                            onRenameVideo = { video, newName -> viewModel.renameLocalVideo(context, video, newName) },
                                            onDeleteVideo = { video -> viewModel.deleteLocalVideo(context, video) },
                                            videoGridSize = videoGridSize,
                                            uiCornerRadius = uiCornerRadius,
                                            onMenuClick = { scope.launch { drawerState.open() } }
                                        )
                                    }
                                    TabOption.Statuses -> {
                                        StatusesTab(
                                            statuses = statuses,
                                            isRefreshing = isRefreshingStatuses,
                                            onSaveStatus = { viewModel.saveStatus(context, it) },
                                            onPlayVideo = { viewModel.startPlaying(it) },
                                            onRefresh = { viewModel.refreshWhatsAppStatuses(context) },
                                            onRenameStatus = { status, newName -> viewModel.renameStatus(context, status, newName) },
                                            onDeleteStatus = { status -> viewModel.deleteStatus(context, status) },
                                            onChooseWhatsAppFolder = {
                                                try {
                                                    val whatsappStatusesUri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses")
                                                    safLauncher.launch(whatsappStatusesUri)
                                                } catch (e: Exception) {
                                                    try {
                                                        val altUri = android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses")
                                                        safLauncher.launch(altUri)
                                                    } catch (ex: Exception) {
                                                        safLauncher.launch(null)
                                                    }
                                                }
                                            },
                                            onMenuClick = { scope.launch { drawerState.open() } }
                                        )
                                    }
                                    TabOption.Folders -> {
                                        FoldersTab(
                                            localVideos = localVideos,
                                            playlists = playlists,
                                            recentlyPlayedList = recentlyPlayed,
                                            onCreatePlaylist = { name -> viewModel.createPlaylist(name, "") },
                                            onVideoPlay = { video, folder -> viewModel.startPlaying(video, folder?.let { "folder:$it" }) },
                                            onDeleteVideo = { video -> viewModel.deleteLocalVideo(context, video) },
                                            folderVideosSortOption = folderVideosSortOption,
                                            onFolderVideosSortOptionChange = { viewModel.setFolderVideosSortOption(context, it) },
                                            onMenuClick = { scope.launch { drawerState.open() } }
                                        )
                                    }
                                    else -> {
                                        // Fallback or empty tab state
                                    }
                                }
                            }
                        }
                    }
                }

                // Full Screen immersive ExoPlayer overlay
                AnimatedVisibility(
                    visible = nowPlaying != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    nowPlaying?.let { video ->
                        val directPlay by viewModel.directPlay.collectAsStateWithLifecycle()
                        // Query history parameters to offer continuous playback seek positioning
                        val initialSeek = remember(video.uri, recentlyPlayed) {
                            recentlyPlayed.find { it.uri == video.uri }?.progress ?: 0L
                        }

                        // Determine if we should show the resume or startover dialog on watched videos
                        var confirmedSeek by remember(video.uri, directPlay) { 
                            mutableStateOf<Long?>(
                                if (directPlay) initialSeek else if (initialSeek <= 3000L) 0L else null
                            ) 
                        }

                        if (confirmedSeek == null) {
                            AlertDialog(
                                onDismissRequest = { confirmedSeek = 0L },
                                title = {
                                    Text(
                                        text = "Resume playing?",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                text = {
                                    Text(
                                        text = "You previously watched this video. Would you like to resume from where you left off (${formatTime(initialSeek)}), or start over from the beginning?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { confirmedSeek = initialSeek }
                                    ) {
                                        Text("Resume")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { confirmedSeek = 0L }
                                    ) {
                                        Text("Start Over")
                                    }
                                }
                            )
                        } else {
                            VideoPlayer(
                                video = video,
                                speed = playbackSpeed,
                                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                                onClose = { viewModel.stopPlaying() },
                                onPlayNext = if (viewModel.hasNextVideo()) { { viewModel.playNextVideo() } } else null,
                                onPlayPrevious = if (viewModel.hasPreviousVideo()) { { viewModel.playPreviousVideo() } } else null,
                                isAutoPlayEnabled = autoPlayNext,
                                onAutoPlayToggle = { viewModel.setAutoPlayNext(it) },
                                initialPlaybackPosition = confirmedSeek ?: 0L,
                                onProgressUpdate = { progress, total ->
                                    viewModel.recordVideoPlayback(context, video, progress, total)
                                },
                                playerUiDesign = playerUiDesign,
                                seekBarDesign = seekBarDesign,
                                playerBackgroundOpacity = playerBackgroundOpacity,
                                navigationButtonOpacity = navigationButtonOpacity,
                                isInPipMode = isInPipMode,
                                initialVideoFilter = videoFilter,
                                onVideoFilterChange = { viewModel.setVideoFilter(context, it) },
                                initialVideoResizeMode = videoResizeMode,
                                onVideoResizeModeChange = { viewModel.setVideoResizeMode(context, it) },
                                uiCornerRadius = uiCornerRadius,
                                hasSubtitleBackground = subtitleHasBackground,
                                subtitleTextColor = subtitleTextColor,
                                hasSubtitleOutline = subtitleHasOutline,
                                isSubtitleEnabled = isSubtitleEnabled,
                                onSubtitleToggle = { viewModel.setIsSubtitleEnabled(context, it) }
                            )
                        }
                    }
                }

                // Overlay dialog to associate clips with playlists
                if (videoToAddToPlaylist != null) {
                    videoToAddToPlaylist?.let { video ->
                        AddToPlaylistDialog(
                            video = video,
                            playlists = playlists,
                            onConfirm = { playlistId ->
                                viewModel.confirmAddToPlaylist(playlistId, video)
                            },
                            onDismiss = { viewModel.requestAddToPlaylist(null) },
                            onCreateNewPlaylist = { name ->
                                viewModel.createPlaylist(name, "Quick creation list")
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun SettingsCategoryHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onHeaderClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (!isExpanded) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SidebarCustomizationContent(
    themeMode: String,
    onThemeChange: (String) -> Unit,
    sortOption: String,
    onSortOptionChange: (String) -> Unit,
    playerDesign: String,
    onPlayerDesignChange: (String) -> Unit,
    seekBarDesign: String,
    onSeekBarDesignChange: (String) -> Unit,
    videoGridSize: Int,
    onVideoGridSizeChange: (Int) -> Unit,
    uiCornerRadius: Int,
    onUiCornerRadiusChange: (Int) -> Unit,
    isTransparentNav: Boolean,
    onIsTransparentNavChange: (Boolean) -> Unit,
    playerBackgroundOpacity: Float,
    onPlayerBackgroundOpacityChange: (Float) -> Unit,
    navigationButtonOpacity: Float,
    onNavigationButtonOpacityChange: (Float) -> Unit,
    customBgUri: String?,
    onCustomBgChange: (String?) -> Unit,
    customFontUri: String?,
    onCustomFontChange: (String?) -> Unit,
    hasSubtitleBackground: Boolean,
    onHasSubtitleBackgroundChange: (Boolean) -> Unit,
    subtitleTextColor: Int,
    onSubtitleTextColorChange: (Int) -> Unit,
    hasSubtitleOutline: Boolean,
    onHasSubtitleOutlineChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var showAboutPopup by remember { mutableStateOf(false) }

    BackHandler(enabled = expandedCategory != null || showAboutPopup) {
        if (showAboutPopup) {
            showAboutPopup = false
        } else {
            expandedCategory = null
        }
    }

    val themeLabels = mapOf(
        "MY_THEME" to "My Theme",
        "DARK" to "Dark Accent",
        "LIGHT" to "Pure Light",
        "COSMIC" to "Cosmic Slate",
        "AMBER" to "Sunset Amber",
        "FOREST" to "Forest Green",
        "PURPLE" to "Deep Purple"
    )

    val sortLabels = mapOf(
        "TITLE_ASC" to "Title (A to Z)",
        "NAME_ASC" to "Title (A to Z)",
        "TITLE_DESC" to "Title (Z to A)",
        "NAME_DESC" to "Title (Z to A)",
        "DURATION_ASC" to "Duration (Short first)",
        "DURATION_DESC" to "Duration (Long first)",
        "SIZE_ASC" to "Size (Low to High)",
        "SIZE_DESC" to "Size (High to Low)",
        "DATE_DESC" to "Date Added (Newest first)",
        "DATE_ASC" to "Date Added (Oldest first)"
    )

    val designLabels = mapOf(
        "MIDNIGHT" to "Midnight",
        "MINIMAL" to "Minimal HUD",
        "CLASSIC" to "Classic Dark",
        "NEON_DREAM" to "Neon Dream",
        "MATERIAL_YOU" to "Material You",
        "SOLAR_BURST" to "Solar Burst"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.40f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                expandedCategory = null
            }
            .verticalScroll(rememberScrollState())
    ) {
        // Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "App Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close customization drawer"
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        // 0. Subtitles Section
        val isSubtitleExpanded = expandedCategory == "SUBTITLE"
        var showColorPicker by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            SettingsCategoryHeader(
                title = "Subtitles",
                icon = Icons.Default.Subtitles,
                subtitle = "Appearance & Style",
                isExpanded = isSubtitleExpanded,
                onHeaderClick = {
                    expandedCategory = if (isSubtitleExpanded) null else "SUBTITLE"
                }
            )

            if (isSubtitleExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Background toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onHasSubtitleBackgroundChange(!hasSubtitleBackground) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtitle Background",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = hasSubtitleBackground,
                        onCheckedChange = { onHasSubtitleBackgroundChange(it) }
                    )
                }
                
                // Outline toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onHasSubtitleOutlineChange(!hasSubtitleOutline) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtitle Outline",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = hasSubtitleOutline,
                        onCheckedChange = { onHasSubtitleOutlineChange(it) }
                    )
                }

                // Color option
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showColorPicker = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Text Color",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(subtitleTextColor))
                            .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (showColorPicker) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showColorPicker = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { showColorPicker = false },
                    contentAlignment = Alignment.Center
                ) {
                    // We don't have BlurModifier available as standard modifier insideDialog easily without drawing behind, 
                    // so we just rely on Dialog's default dim or we could use custom surface
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                        modifier = Modifier.padding(16.dp).widthIn(max = 400.dp).clickable { /* consume */ }
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("Select Subtitle Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            val colors = listOf(
                                android.graphics.Color.WHITE, android.graphics.Color.BLACK,
                                android.graphics.Color.RED, android.graphics.Color.GREEN,
                                android.graphics.Color.BLUE, android.graphics.Color.YELLOW,
                                android.graphics.Color.CYAN, android.graphics.Color.MAGENTA,
                                android.graphics.Color.DKGRAY, android.graphics.Color.LTGRAY
                            )
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(colors.size) { index ->
                                    val c = colors[index]
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color(c))
                                            .border(if (c == subtitleTextColor) 3.dp else 1.dp, if (c == subtitleTextColor) MaterialTheme.colorScheme.primary else Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                            .clickable {
                                                onSubtitleTextColorChange(c)
                                                showColorPicker = false
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 1. Theme Customization Section
        val isThemeExpanded = expandedCategory == "THEME"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            SettingsCategoryHeader(
                title = "Visual Interface Theme",
                icon = Icons.Default.Palette,
                subtitle = themeLabels[themeMode] ?: "Dark Accent",
                isExpanded = isThemeExpanded,
                onHeaderClick = {
                    expandedCategory = if (isThemeExpanded) null else "THEME"
                }
            )

            if (isThemeExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip("MY_THEME", "My Theme", themeMode, onThemeChange, Modifier.weight(1f))
                    ThemeChip("DARK", "Dark Accent", themeMode, onThemeChange, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip("LIGHT", "Pure Light", themeMode, onThemeChange, Modifier.weight(1f))
                    ThemeChip("COSMIC", "Cosmic Slate", themeMode, onThemeChange, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip("AMBER", "Sunset Amber", themeMode, onThemeChange, Modifier.weight(1f))
                    ThemeChip("FOREST", "Forest Green", themeMode, onThemeChange, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip("PURPLE", "Deep Purple", themeMode, onThemeChange, Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 2. Sorting Options Section
        val isSortExpanded = expandedCategory == "SORT"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            SettingsCategoryHeader(
                title = "Gallery Library Sort",
                icon = Icons.Default.Sort,
                subtitle = sortLabels[sortOption] ?: "Title (A to Z)",
                isExpanded = isSortExpanded,
                onHeaderClick = {
                    expandedCategory = if (isSortExpanded) null else "SORT"
                }
            )

            if (isSortExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                val sortingOptions = listOf(
                    "TITLE_ASC" to "Title (A to Z)",
                    "TITLE_DESC" to "Title (Z to A)",
                    "DURATION_ASC" to "Duration (Short first)",
                    "DURATION_DESC" to "Duration (Long first)",
                    "SIZE_ASC" to "Size (Low to High)",
                    "SIZE_DESC" to "Size (High to Low)",
                    "DATE_DESC" to "Date Added (Newest first)",
                    "DATE_ASC" to "Date Added (Oldest first)"
                )

                sortingOptions.forEach { (optionKey, optionLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSortOptionChange(optionKey) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (sortOption == optionKey || (optionKey == "TITLE_ASC" && sortOption == "NAME_ASC") || (optionKey == "TITLE_DESC" && sortOption == "NAME_DESC")),
                            onClick = { onSortOptionChange(optionKey) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = optionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 3. Player Design Section
        val isPlayerExpanded = expandedCategory == "PLAYER"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            SettingsCategoryHeader(
                title = "Video Player HUD Style",
                icon = Icons.Default.Movie,
                subtitle = designLabels[playerDesign] ?: "Midnight",
                isExpanded = isPlayerExpanded,
                onHeaderClick = {
                    expandedCategory = if (isPlayerExpanded) null else "PLAYER"
                }
            )

            if (isPlayerExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DesignChip("MIDNIGHT", "Midnight", playerDesign, onPlayerDesignChange, Modifier.weight(1f))
                    DesignChip("MINIMAL", "Minimal HUD", playerDesign, onPlayerDesignChange, Modifier.weight(1f))
                    DesignChip("CLASSIC", "Classic Dark", playerDesign, onPlayerDesignChange, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DesignChip("NEON_DREAM", "Neon Dream", playerDesign, onPlayerDesignChange, Modifier.weight(1f))
                    DesignChip("MATERIAL_YOU", "Material You", playerDesign, onPlayerDesignChange, Modifier.weight(1f))
                    DesignChip("SOLAR_BURST", "Solar Burst", playerDesign, onPlayerDesignChange, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 4. UI Customization Section
        val isUiCustomExpanded = expandedCategory == "UI_CUSTOM"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            SettingsCategoryHeader(
                title = "UI Customization",
                icon = Icons.Default.Tune,
                subtitle = "Sliders & Buttons Opts",
                isExpanded = isUiCustomExpanded,
                onHeaderClick = {
                    expandedCategory = if (isUiCustomExpanded) null else "UI_CUSTOM"
                }
            )

            if (isUiCustomExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Player background transparency option with slider / progress bar
                Text(
                    text = "Player Background Opacity (${(playerBackgroundOpacity * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = playerBackgroundOpacity,
                    onValueChange = onPlayerBackgroundOpacityChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Control player navigation button's background transparency
                Text(
                    text = "Buttons Background Transparency (${(navigationButtonOpacity * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = navigationButtonOpacity,
                    onValueChange = onNavigationButtonOpacityChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Seek Bar Customization Design Option
                Text(
                    text = "Player Seek Bar Design",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DesignChip("CLASSIC", "Classic Slider", seekBarDesign, onSeekBarDesignChange, Modifier.weight(1f))
                    DesignChip("SNAKE", "Snake Style", seekBarDesign, onSeekBarDesignChange, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Transparent Nav Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onIsTransparentNavChange(!isTransparentNav) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transparent Nav",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isTransparentNav,
                        onCheckedChange = { onIsTransparentNavChange(it) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                val fontPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            context.contentResolver.takePersistableUriPermission(uri, flags)
                            
                            // copy to local cache
                            val file = java.io.File(context.cacheDir, "custom_font.ttf")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                file.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            onCustomFontChange(file.absolutePath)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }

                Text(
                    text = "App Font",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (customFontUri != null) {
                        OutlinedButton(
                            onClick = { onCustomFontChange(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                    Button(
                        onClick = {
                            fontPickerLauncher.launch(arrayOf("*/*")) // Allow all files, since system might not map otf/ttf correctly
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Select TTF/OTF")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Change shape (corner radius)
                Text(
                    text = "UI Corner Radius (${uiCornerRadius}dp)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = uiCornerRadius.toFloat(),
                    onValueChange = { onUiCornerRadiusChange(it.toInt()) },
                    valueRange = 0f..32f,
                    steps = 32,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Videos in list (grid size)
                Text(
                    text = "Videos in List (Grid Size: ${videoGridSize})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = videoGridSize.toFloat(),
                    onValueChange = { onVideoGridSizeChange(it.toInt()) },
                    valueRange = 1f..3f,
                    steps = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 5. Custom Background Image Section
        val isBgExpanded = expandedCategory == "CUSTOM_BG"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            SettingsCategoryHeader(
                title = "Main Screen Wallpaper",
                icon = Icons.Default.Image,
                subtitle = if (customBgUri != null) "Uploaded custom photo" else "System theme wallpaper",
                isExpanded = isBgExpanded,
                onHeaderClick = {
                    expandedCategory = if (isBgExpanded) null else "CUSTOM_BG"
                }
            )

            if (isBgExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Upload any custom picture to style the main player gallery screens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val destFile = java.io.File(context.filesDir, "custom_bg.jpg")
                                val outputStream = java.io.FileOutputStream(destFile)
                                inputStream.copyTo(outputStream)
                                inputStream.close()
                                outputStream.close()
                                onCustomBgChange(destFile.absolutePath)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onCustomBgChange(uri.toString())
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select Photo", style = MaterialTheme.typography.labelMedium)
                    }

                    if (customBgUri != null) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val destFile = java.io.File(context.filesDir, "custom_bg.jpg")
                                    if (destFile.exists()) {
                                        destFile.delete()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onCustomBgChange(null)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 5.5. Clear Storage Category
        val isStorageExpanded = expandedCategory == "CLEAR_STORAGE"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            SettingsCategoryHeader(
                title = "Clear Storage",
                icon = Icons.Default.Storage,
                subtitle = "Manage cached thumbnails and player history",
                isExpanded = isStorageExpanded,
                onHeaderClick = {
                    expandedCategory = if (isStorageExpanded) null else "CLEAR_STORAGE"
                }
            )

            if (isStorageExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clear Cache (Thumbnails) Option
                    Button(
                        onClick = {
                            com.example.ThumbnailCacheManager.clearCache(context)
                            android.widget.Toast.makeText(context, "Thumbnail cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Cache", style = MaterialTheme.typography.labelMedium)
                    }

                    // Clear History Option
                    Button(
                        onClick = {
                            onClearHistory()
                            android.widget.Toast.makeText(context, "Playback history cleared", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear History", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // 6. About Developer Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showAboutPopup = true }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "About Developer",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "About Developer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Shubhendu & App Information",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }

    if (showAboutPopup) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAboutPopup = false }) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .height(440.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                // Background Image
                Image(
                    painter = rememberAsyncImagePainter(model = com.example.R.drawable.dev_photo_1780656123513),
                    contentDescription = "Developer portrait sketch drawing",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Linear Gradient Overlay to make the content highly readable and visually gorgeous
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Close button at top right
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = { showAboutPopup = false },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close About popup",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Middle content: App Icon, Name, Developer Info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Developer Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(2.dp, Color(0xFF00F0FF), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = com.example.R.drawable.img_dev_profile_circular_1780657862688),
                                contentDescription = "Developer Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Text(
                            text = "Sk Player",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Developed by shubhendu",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    // Instagram link click button
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Button(
                        onClick = { uriHandler.openUri("https://www.instagram.com/sk_chintu_77_") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE1306C), // Instagram brand color
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Open Instagram Link",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "@sk_chintu_77_",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeChip(
    key: String,
    label: String,
    currentTheme: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = (currentTheme == key)
    Button(
        onClick = { onClick(key) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        modifier = modifier.height(38.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DesignChip(
    key: String,
    label: String,
    currentDesign: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = (currentDesign == key)
    Button(
        onClick = { onClick(key) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        modifier = modifier.height(38.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermissionsBanner(
    permissionState: MultiplePermissionsState,
    hasAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit
) {
    val needsStandard = !permissionState.allPermissionsGranted
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (needsStandard) Icons.Default.Notifications else Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (needsStandard) "Access Media Library & Notifications" else "All Storage Access Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (needsStandard) {
                "To show push notifications, discover device videos, and access WhatsApp status updates, Sk Player needs notification, photo, and video permissions."
            } else {
                "To let you delete files, manage video playlists, and automatically scan storage properly, Sk Player requires 'All Files Access' storage permission."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (needsStandard) {
            Button(
                onClick = { permissionState.launchMultiplePermissionRequest() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Grant Photos, Videos & Notification", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRequestAllFilesAccess,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Grant All Storage Access", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = (ms / (1000 * 60 * 60)) % 24
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
