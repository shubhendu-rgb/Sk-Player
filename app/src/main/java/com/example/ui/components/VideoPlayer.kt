package com.example.ui.components

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.telephony.TelephonyManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.repository.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import coil.imageLoader
import coil.request.ImageRequest

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    video: VideoItem,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onPlayPrevious: (() -> Unit)? = null,
    isAutoPlayEnabled: Boolean = true,
    onAutoPlayToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    initialPlaybackPosition: Long = 0L,
    onProgressUpdate: (Long, Long) -> Unit = { _, _ -> },
    playerUiDesign: String = "MIDNIGHT",
    seekBarDesign: String = "CLASSIC",
    playerBackgroundOpacity: Float = 0.65f,
    navigationButtonOpacity: Float = 0.15f,
    isInPipMode: Boolean = false,
    initialVideoFilter: String = "ORIGINAL",
    onVideoFilterChange: (String) -> Unit = {},
    initialVideoResizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onVideoResizeModeChange: (Int) -> Unit = {},
    uiCornerRadius: Int = 12,
    holdToSpeedEnabled: Boolean = true,
    holdToSpeedValue: Float = 2f,
    hasSubtitleBackground: Boolean = false,
    subtitleTextColor: Int = android.graphics.Color.WHITE,
    hasSubtitleOutline: Boolean = true,
    isSubtitleEnabled: Boolean = true,
    onSubtitleToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Controller overlay interaction visibility states
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isPlayingState by remember { mutableStateOf(true) }
    var isHoldToSpeedActive by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var resizeModeState by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(initialVideoResizeMode) }

    var activeFilterKey by remember { mutableStateOf(initialVideoFilter) }
    var activeFilterName by remember { mutableStateOf("Original") }
    var showFiltersMenu by remember { mutableStateOf(false) }

    val identityMatrix = remember {
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    val warmSunsetMatrix = remember {
        floatArrayOf(
            1.2f, 0f, 0f, 0f, 15f,
            0f, 1.1f, 0f, 0f, 5f,
            0f, 0f, 0.8f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    val cyberpunkMatrix = remember {
        floatArrayOf(
            0.9f, 0f, 0.3f, 0f, 20f,
            0f, 0.9f, 0f, 0f, -5f,
            0.2f, 0f, 1.2f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    val vintageNoirMatrix = remember {
        floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 10f,
            0.299f, 0.587f, 0.114f, 0f, 10f,
            0.299f, 0.587f, 0.114f, 0f, 10f,
            0f,     0f,     0f,     1f, 0f
        )
    }
    val coolMistMatrix = remember {
        floatArrayOf(
            0.85f, 0f, 0f, 0f, -10f,
            0f, 0.95f, 0f, 0f, -5f,
            0f, 0f, 1.25f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    val mutedForestMatrix = remember {
        floatArrayOf(
            0.7f, 0.2f, 0.1f, 0f, 5f,
            0.1f, 0.8f, 0.2f, 0f, 10f,
            0.2f, 0.2f, 0.6f, 0f, -5f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    val hdrVividMatrix = remember {
        floatArrayOf(
            1.35f, -0.1f, -0.1f, 0f, 10f,
            -0.1f, 1.35f, -0.1f, 0f, 10f,
            -0.1f, -0.1f, 1.35f, 0f, 10f,
            0.25f, 0.25f, 0.25f, 1f, 0f
        )
    }
    val hdrCinemaMatrix = remember {
        floatArrayOf(
            1.4f, -0.05f, -0.1f, 0f, 15f,
            -0.05f, 1.35f, -0.05f, 0f, 5f,
            -0.1f, -0.1f, 1.2f, 0f, -15f,
            0.25f, 0.25f, 0.25f, 1f, 0f
        )
    }
    val hdrSunsetMatrix = remember {
        floatArrayOf(
            1.5f,  0.1f, -0.1f, 0f, 20f,
            0.1f,  1.2f, -0.1f, 0f, 5f,
           -0.2f, -0.1f,  1.1f, 0f, -10f,
            0.2f,  0.2f,  0.2f, 1f, 0f
        )
    }
    val hdrCrystalMatrix = remember {
        floatArrayOf(
            1.1f, -0.1f,  0.2f, 0f, 5f,
           -0.1f,  1.3f, -0.1f, 0f, 10f,
            0.1f,  0.1f,  1.5f, 0f, 25f,
            0.2f,  0.2f,  0.2f, 1f, 0f
        )
    }
    val hdrDeepContrastMatrix = remember {
        floatArrayOf(
            1.6f, -0.2f, -0.2f, 0f, -15f,
           -0.2f,  1.6f, -0.2f, 0f, -15f,
           -0.2f, -0.2f,  1.6f, 0f, -15f,
            0.3f,  0.3f,  0.3f, 1f, 0f
        )
    }
    val hdrGoldenGlowMatrix = remember {
        floatArrayOf(
            1.4f,  0.2f, -0.1f, 0f, 25f,
            0.1f,  1.4f, -0.1f, 0f, 15f,
           -0.2f, -0.2f,  0.9f, 0f, -20f,
            0.2f,  0.2f,  0.2f, 1f, 0f
        )
    }
    val hdrUltraBrightMatrix = remember {
        floatArrayOf(
            1.5f, -0.05f, -0.05f, 0f, 30f,
           -0.05f,  1.5f, -0.05f, 0f, 30f,
           -0.05f, -0.05f,  1.5f, 0f, 30f,
            0.25f,  0.25f,  0.25f, 1f, 0f
        )
    }
    val glitchRgbSplitMatrix = remember {
        floatArrayOf(
            1.4f, 0.4f, -0.2f, 0f, 15f,
            -0.2f, 0.8f, 0.4f, 0f, -10f,
            0.4f, -0.2f, 1.4f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    val glitchWaveMatrix = remember {
        floatArrayOf(
            0.7f, 0.9f, -0.3f, 0f, 35f,
            -0.3f, 1.2f, 0.6f, 0f, -25f,
            0.8f, -0.4f, 0.9f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    
    val activity = context as? android.app.Activity
    val originalOrientation = remember { activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    var isAutoRotateEnabled by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        if (isAutoRotateEnabled) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val handleClose = {
        activity?.requestedOrientation = originalOrientation
        onClose()
    }

    // Immersive system bar concealment - keeping status bar visible
    DisposableEffect(context) {
        val activity = context as? android.app.Activity
        val window = activity?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val currentActivity = context as? android.app.Activity
    LaunchedEffect(areControlsVisible) {
        val window = currentActivity?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (areControlsVisible) {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    val trackSelector = remember {
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
    }

    var preferredCodec by remember { mutableStateOf("H264") } // "H264" = H.264 (AVC) [Primary], "H265" = H.265 (HEVC) [Secondary]
    var selectedQuality by remember { mutableStateOf("Auto") }

    LaunchedEffect(preferredCodec, selectedQuality) {
        val targetMime = if (preferredCodec == "H265") {
            MimeTypes.VIDEO_H265
        } else {
            MimeTypes.VIDEO_H264
        }
        val builder = trackSelector.buildUponParameters()
            .setPreferredVideoMimeType(targetMime)

        when (selectedQuality) {
            "1080p" -> builder.setMaxVideoSize(1920, 1080)
            "720p" -> builder.setMaxVideoSize(1280, 720)
            "480p" -> builder.setMaxVideoSize(854, 480)
            "360p" -> builder.setMaxVideoSize(640, 360)
            else -> builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        trackSelector.setParameters(builder)
    }

    // Initialize ExoPlayer with browser user-agent and cross-protocol routing support for online streaming compatibility
    val exoPlayer = remember {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // handleAudioFocus = true
            )
            .build().apply {
                playWhenReady = true
            }
    }

    val mediaSession = remember(exoPlayer) {
        try {
            androidx.media3.session.MediaSession.Builder(context, exoPlayer)
                .setId("playstatus_video_player_session_" + System.currentTimeMillis())
                .build()
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "Error constructing MediaSession: ${e.message}")
            null
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxStreamVol = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }

    var currentVolumeState by remember {
        val initialVolumeIndex = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val initialVolState = initialVolumeIndex.toFloat() / maxStreamVol.coerceAtLeast(1)
        mutableStateOf(initialVolState)
    }
    var loudnessEnhancer by remember { mutableStateOf<android.media.audiofx.LoudnessEnhancer?>(null) }

    fun applyVolume(vol: Float) {
        val targetVol = vol.coerceIn(0f, 2f)
        currentVolumeState = targetVol
        if (targetVol > 1f) {
            try {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxStreamVol, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            exoPlayer.volume = 1f
            val gain = ((targetVol - 1f) * 2000).toInt() // Max 2000 mB boost
            try {
                if (loudnessEnhancer == null && exoPlayer.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                    val enhancer = android.media.audiofx.LoudnessEnhancer(exoPlayer.audioSessionId)
                    enhancer.enabled = true
                    loudnessEnhancer = enhancer
                }
                loudnessEnhancer?.let { enhancer ->
                    if (!enhancer.enabled) {
                        enhancer.enabled = true
                    }
                    enhancer.setTargetGain(gain)
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "LoudnessEnhancer error: ${e.message}")
            }
        } else {
            val targetIndex = (targetVol * maxStreamVol + 0.5f).toInt()
            try {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetIndex, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            exoPlayer.volume = 1f
            try {
                loudnessEnhancer?.setTargetGain(0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }



    // Set media source
    LaunchedEffect(video) {
        val uriStr = video.uri
        val mediaUri = if (uriStr.startsWith("content://") || uriStr.startsWith("http://") || uriStr.startsWith("https://") || uriStr.startsWith("file://")) {
            android.net.Uri.parse(uriStr)
        } else {
            android.net.Uri.fromFile(java.io.File(uriStr))
        }

        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(video.title)
            .setArtist(video.author ?: "Sk Player")
            .setDisplayTitle(video.title)
            .setArtworkUri(video.thumbnailUri?.let { android.net.Uri.parse(it) } ?: mediaUri)
            .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaMetadata(mediaMetadata)
        
        // Explicitly support adaptive DASH and HLS streaming formats.
        // For standard progressive streams (MP4, MKV, WebM, etc.), we leave mimeType empty.
        // This allows ExoPlayer's content sensing/containment sniffer to operate dynamically, avoiding Source Errors.
        val mime = video.mimeType.lowercase()
        val uriLower = video.uri.lowercase()
        if (mime == "application/x-mpegurl" || mime == "application/vnd.apple.mpegurl" || uriLower.endsWith(".m3u8") || uriLower.contains("m3u8") || uriLower.contains(".m3u8?")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (mime == "application/dash+xml" || uriLower.endsWith(".mpd") || uriLower.contains("mpd") || uriLower.contains(".mpd?")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        } else if (uriLower.endsWith(".webm") || uriLower.contains("webm")) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_WEBM)
        } else if (uriLower.endsWith(".mp4") || uriLower.contains("mp4")) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }
        
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        if (initialPlaybackPosition > 0L) {
            exoPlayer.seekTo(initialPlaybackPosition)
        }
        exoPlayer.play()
    }

    // React to speed adjustments instantly
    LaunchedEffect(speed) {
        exoPlayer.setPlaybackParameters(PlaybackParameters(speed))
    }

    // Release player on finished viewing
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaSession?.release()
            } catch (e: Exception) {
                // Ignore
            }
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Media Notification and Remote Control Broadcast System
    val currentOnPlayNextUpdated by rememberUpdatedState(onPlayNext)
    val currentOnPlayPreviousUpdated by rememberUpdatedState(onPlayPrevious)

    val broadcastReceiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                when (intent.action) {
                    "com.example.PLAY_PAUSE" -> {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                    "com.example.PREVIOUS" -> {
                        currentOnPlayPreviousUpdated?.invoke()
                    }
                    "com.example.NEXT" -> {
                        currentOnPlayNextUpdated?.invoke()
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = android.content.IntentFilter().apply {
            addAction("com.example.PLAY_PAUSE")
            addAction("com.example.PREVIOUS")
            addAction("com.example.NEXT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(broadcastReceiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(12345)
        }
    }

    LaunchedEffect(video, isPlayingState) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val imageLoader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(video.thumbnailUri ?: video.uri)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                val srcDrawable = result.drawable
                val srcBitmap = (srcDrawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                
                val intentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                
                val prevIntent = android.content.Intent("com.example.PREVIOUS")
                val prevPI = PendingIntent.getBroadcast(context, 101, prevIntent, intentFlags)
                
                val playPauseIntent = android.content.Intent("com.example.PLAY_PAUSE")
                val playPausePI = PendingIntent.getBroadcast(context, 102, playPauseIntent, intentFlags)
                
                val nextIntent = android.content.Intent("com.example.NEXT")
                val nextPI = PendingIntent.getBroadcast(context, 103, nextIntent, intentFlags)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = "Video Playback"
                    val descriptionText = "Shows controls for currently playing video"
                    val importance = NotificationManager.IMPORTANCE_LOW
                    val channel = NotificationChannel("video_playback_channel", name, importance).apply {
                        description = descriptionText
                    }
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.createNotificationChannel(channel)
                }
                
                val notificationBuilder = NotificationCompat.Builder(context, "video_playback_channel")
                    .setSmallIcon(com.example.R.drawable.ic_play_arrow_m3)
                    .setContentTitle(video.title)
                    .setContentText(video.author ?: "Sk Player")
                    .setOngoing(isPlayingState)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                
                if (srcBitmap != null) {
                    notificationBuilder.setLargeIcon(srcBitmap)
                }
                
                val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (openIntent != null) {
                    val openPI = PendingIntent.getActivity(context, 100, openIntent, intentFlags)
                    notificationBuilder.setContentIntent(openPI)
                }
                
                notificationBuilder.addAction(
                    androidx.core.app.NotificationCompat.Action.Builder(
                        com.example.R.drawable.ic_skip_previous_m3,
                        "Previous",
                        prevPI
                    ).build()
                )
                
                notificationBuilder.addAction(
                    androidx.core.app.NotificationCompat.Action.Builder(
                        if (isPlayingState) com.example.R.drawable.ic_pause_m3 else com.example.R.drawable.ic_play_arrow_m3,
                        if (isPlayingState) "Pause" else "Play",
                        playPausePI
                    ).build()
                )
                
                notificationBuilder.addAction(
                    androidx.core.app.NotificationCompat.Action.Builder(
                        com.example.R.drawable.ic_skip_next_m3,
                        "Next",
                        nextPI
                    ).build()
                )
                
                mediaSession?.let { session ->
                    notificationBuilder.setStyle(
                        androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                            .setShowActionsInCompactView(0, 1, 2)
                    )
                }
                
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(12345, notificationBuilder.build())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Support device-level hardware back dismissals
    BackHandler {
        if (isLocked) {
            val toast = android.widget.Toast.makeText(context, "Screen is locked", android.widget.Toast.LENGTH_SHORT)
            toast.show()
        } else if (showSpeedDialog) {
            showSpeedDialog = false
        } else if (showAudioTrackDialog) {
            showAudioTrackDialog = false
        } else if (showFiltersMenu) {
            showFiltersMenu = false
        } else {
            handleClose()
        }
    }

    // Double tap feedback state
    var activeSeekFeedback by remember { mutableStateOf<String?>(null) } // "BACK" or "FORWARD"
    var seekDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Gesture dismiss state
    var gestureDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Autohide controls utility
    LaunchedEffect(areControlsVisible, isPlayingState, showSpeedDialog, showAudioTrackDialog, showFiltersMenu) {
        if (areControlsVisible && isPlayingState && !showSpeedDialog && !showAudioTrackDialog && !showFiltersMenu) {
            delay(5000)
            areControlsVisible = false
        }
    }

    // Continuous Position Tracker
    LaunchedEffect(isPlayingState, video) {
        while (true) {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                if (totalDuration > 0) {
                    onProgressUpdate(currentPosition, totalDuration)
                }
            }
            isPlayingState = exoPlayer.isPlaying
            delay(250) // Reduced delay for smoother UI
        }
    }

    // Player Status Listener
    val currentIsAutoPlayEnabled by rememberUpdatedState(isAutoPlayEnabled)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)

    val playerListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlayingState = exoPlayer.isPlaying
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_ENDED) {
                    if (currentIsAutoPlayEnabled) {
                        currentOnPlayNext?.invoke()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoPlayer", "ExoPlayer playback error: ${error.message}", error)
                android.widget.Toast.makeText(
                    context,
                    "Playback failed: ${error.localizedMessage ?: error.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        loudnessEnhancer?.release()
                        val enhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId)
                        enhancer.enabled = true
                        loudnessEnhancer = enhancer
                        // Apply current boost gain
                        if (currentVolumeState > 1f) {
                            val gain = ((currentVolumeState - 1f) * 2000).toInt()
                            enhancer.setTargetGain(gain)
                        } else {
                            enhancer.setTargetGain(0)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VideoPlayer", "Failed to init LoudnessEnhancer: ${e.message}")
                    }
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        exoPlayer.addListener(playerListener)
        onDispose {
            exoPlayer.removeListener(playerListener)
            try {
                loudnessEnhancer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Keep screen on while video player is active (prevents auto screen off)
    DisposableEffect(activity) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val currentTracks = exoPlayer.currentTracks
    val audioTracks = remember(currentTracks) {
        val list = mutableListOf<AudioTrackInfo>()
        val groups = currentTracks.groups
        for (i in 0 until groups.size) {
            val group = groups[i]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (j in 0 until group.length) {
                    val format = group.getTrackFormat(j)
                    val isSelected = group.isTrackSelected(j)
                    list.add(
                        AudioTrackInfo(
                            groupIndex = i,
                            trackIndex = j,
                            language = format.language ?: "und",
                            label = format.label ?: "Audio Track ${list.size + 1} (${format.language ?: "und"})",
                            isSelected = isSelected,
                            mediaTrackGroup = group.mediaTrackGroup
                        )
                    )
                }
            }
        }
        list
    }

    val prefs = remember(context) { context.getSharedPreferences("playstatus_prefs", android.content.Context.MODE_PRIVATE) }
    var gestureDragLeft by remember { mutableStateOf(true) }
    var gestureVolumeVal by remember { mutableStateOf(currentVolumeState) }
    var gestureBrightnessVal by remember {
        val savedBrightness = prefs.getFloat("saved_brightness", 0.5f)
        mutableStateOf(savedBrightness)
    }
    var activeGestureType by remember { mutableStateOf<String?>(null) } // "VOLUME" or "BRIGHTNESS"
    var lastActiveGestureType by remember { mutableStateOf("VOLUME") }
    var activeGestureValue by remember { mutableStateOf(0.5f) }
    var dragDirection by remember { mutableStateOf("NONE") }
    var dragAccumulatedX by remember { mutableStateOf(0f) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    var gestureDragStartedPosition by remember { mutableStateOf(0L) }

    LaunchedEffect(activeGestureType) {
        if (activeGestureType != null) {
            lastActiveGestureType = activeGestureType!!
        }
    }

    LaunchedEffect(gestureBrightnessVal) {
        val activity = context as? android.app.Activity
        val attrs = activity?.window?.attributes
        if (attrs != null) {
            attrs.screenBrightness = gestureBrightnessVal
            activity.window.attributes = attrs
        }
        prefs.edit().putFloat("saved_brightness", gestureBrightnessVal).apply()
    }

    DisposableEffect(activity) {
        val originalBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        onDispose {
            activity?.window?.let { window ->
                val attrs = window.attributes
                attrs.screenBrightness = originalBrightness
                window.attributes = attrs
            }
        }
    }

    // Listen for phone call ringing/active state to automatically pause media playback
    DisposableEffect(context, exoPlayer) {
        val phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                try {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    if (state == TelephonyManager.EXTRA_STATE_RINGING || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                        exoPlayer.pause()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        try {
            context.registerReceiver(phoneStateReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        var phoneStateListener: android.telephony.PhoneStateListener? = null
        if (telephonyManager != null) {
            try {
                phoneStateListener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java", ReplaceWith("onCallStateChanged"))
                    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                        if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                            exoPlayer.pause()
                        }
                    }
                }
                telephonyManager.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        onDispose {
            try {
                context.unregisterReceiver(phoneStateReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            try {
                if (telephonyManager != null && phoneStateListener != null) {
                    telephonyManager.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(activeGestureType, activeSeekFeedback) {
        if (activeGestureType != null || activeSeekFeedback != null) {
            areControlsVisible = false
        }
    }

    LaunchedEffect(areControlsVisible) {
        if (areControlsVisible) {
            activeGestureType = null
            activeSeekFeedback = null
        }
    }

    DisposableEffect(currentVolumeState) {
        PlayerVolumeKeyHandler.onVolumeKey = { isUp ->
            val step = 1f / maxStreamVol.coerceAtLeast(1).toFloat()
            val delta = if (isUp) step else -step
            val target = (currentVolumeState + delta).coerceIn(0f, 2f)
            applyVolume(target)
            
            // Show custom HUD in app
            activeGestureType = "VOLUME"
            activeGestureValue = target
            gestureVolumeVal = target
            
            gestureDismissJob?.cancel()
            gestureDismissJob = coroutineScope.launch {
                delay(1200)
                activeGestureType = null
                dragDirection = "NONE"
            }
            true
        }
        onDispose {
            PlayerVolumeKeyHandler.onVolumeKey = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
            }
    ) {
        val requiresTextureView = activeFilterKey != "ORIGINAL"
        
        key(requiresTextureView) {
            // The Underlay Video Canvas
            AndroidView(
                factory = { ctx ->
                    val layoutRes = if (requiresTextureView) com.example.R.layout.player_texture_view else com.example.R.layout.player_surface_view
                    val view = LayoutInflater.from(ctx).inflate(layoutRes, null) as PlayerView
                    view.apply {
                        player = exoPlayer
                        useController = false // Use custom beautiful overlay controls instead
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = resizeModeState
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = resizeModeState
                    playerView.subtitleView?.let { subtitleView ->
                        subtitleView.visibility = if (isSubtitleEnabled) android.view.View.VISIBLE else android.view.View.INVISIBLE
                        
                        val bgColor = if (hasSubtitleBackground) android.graphics.Color.parseColor("#80000000") else android.graphics.Color.TRANSPARENT
                        val edgeColor = if (hasSubtitleOutline) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
                        val edgeType = if (hasSubtitleOutline) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                        
                        val style = androidx.media3.ui.CaptionStyleCompat(
                            subtitleTextColor, // foreground
                            bgColor, // background
                            android.graphics.Color.TRANSPARENT, // windowColor
                            edgeType, // edgeType
                            edgeColor, // edgeColor
                            null // typeface
                        )
                        subtitleView.setStyle(style)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (activeFilterKey == "ORIGINAL") {
                            drawContent()
                        } else {
                            val matrixArray = when (activeFilterKey) {
                                "WARM_SUNSET" -> warmSunsetMatrix
                                "CYBERPUNK" -> cyberpunkMatrix
                                "VINTAGE_NOIR" -> vintageNoirMatrix
                                "COOL_MIST" -> coolMistMatrix
                                "MUTED_FOREST" -> mutedForestMatrix
                                "HDR_VIVID" -> hdrVividMatrix
                                "HDR_CINEMA" -> hdrCinemaMatrix
                                "HDR_SUNSET" -> hdrSunsetMatrix
                                "HDR_CRYSTAL" -> hdrCrystalMatrix
                                "HDR_DEEP_CONTRAST" -> hdrDeepContrastMatrix
                                "HDR_GOLDEN_GLOW" -> hdrGoldenGlowMatrix
                                "HDR_ULTRA_BRIGHT" -> hdrUltraBrightMatrix
                                "GLITCH_RGB_SPLIT" -> glitchRgbSplitMatrix
                                "GLITCH_WAVE" -> glitchWaveMatrix
                                else -> identityMatrix
                            }
                            val nativePaint = android.graphics.Paint().apply {
                                colorFilter = android.graphics.ColorMatrixColorFilter(
                                    android.graphics.ColorMatrix(matrixArray)
                                )
                            }
                            drawContext.canvas.nativeCanvas.saveLayer(
                                0f, 0f, size.width, size.height,
                                nativePaint,
                                android.graphics.Canvas.ALL_SAVE_FLAG
                            )
                            drawContent()
                            drawContext.canvas.nativeCanvas.restore()
                        }
                    }
            )
        }

        // Gesture Detection Overlay Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, holdToSpeedEnabled, holdToSpeedValue, speed) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = {
                                // Do nothing when locked, touches are disabled
                            }
                        )
                        return@pointerInput
                    }
                    detectTapGestures(
                        onPress = {
                            if (holdToSpeedEnabled && isPlayingState) {
                                val pressJob = coroutineScope.launch {
                                    delay(400)
                                    isHoldToSpeedActive = true
                                    areControlsVisible = false
                                    exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(holdToSpeedValue)
                                }
                                tryAwaitRelease()
                                pressJob.cancel()
                                if (isHoldToSpeedActive) {
                                    isHoldToSpeedActive = false
                                    exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                                }
                            } else {
                                tryAwaitRelease()
                            }
                        },
                        onTap = {
                            areControlsVisible = !areControlsVisible
                        },
                        onDoubleTap = { offset ->
                            val width = size.width
                            val x = offset.x
                            val seekAmount = 10000L
                            if (x < width * 0.35f) {
                                val seekPos = (exoPlayer.currentPosition - seekAmount).coerceAtLeast(0L)
                                exoPlayer.seekTo(seekPos)
                                currentPosition = seekPos
                                activeSeekFeedback = "BACK"
                                seekDismissJob?.cancel()
                                seekDismissJob = coroutineScope.launch {
                                    delay(700)
                                    activeSeekFeedback = null
                                }
                            } else if (x > width * 0.65f) {
                                val seekPos = (exoPlayer.currentPosition + seekAmount).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(seekPos)
                                currentPosition = seekPos
                                activeSeekFeedback = "FORWARD"
                                seekDismissJob?.cancel()
                                seekDismissJob = coroutineScope.launch {
                                    delay(700)
                                    activeSeekFeedback = null
                                }
                            } else {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                                isPlayingState = exoPlayer.isPlaying
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            gestureDismissJob?.cancel()
                            areControlsVisible = false
                            dragDirection = "NONE"
                            dragAccumulatedX = 0f
                            dragAccumulatedY = 0f
                            isSeeking = true
                            gestureDragStartedPosition = exoPlayer.currentPosition
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val threshold = 15f
                            if (dragDirection == "NONE") {
                                dragAccumulatedX += dragAmount.x
                                dragAccumulatedY += dragAmount.y
                                if (kotlin.math.abs(dragAccumulatedX) > threshold || kotlin.math.abs(dragAccumulatedY) > threshold) {
                                    if (kotlin.math.abs(dragAccumulatedX) > kotlin.math.abs(dragAccumulatedY)) {
                                        dragDirection = "HORIZONTAL"
                                        activeGestureType = "SEEK"
                                    } else {
                                        dragDirection = "VERTICAL"
                                        val isLeft = change.position.x < size.width / 2f
                                        gestureDragLeft = isLeft
                                        if (isLeft) {
                                            val activity = context as? android.app.Activity
                                            val attrs = activity?.window?.attributes
                                            val currentBrightness = attrs?.screenBrightness ?: 0.5f
                                            gestureBrightnessVal = if (currentBrightness < 0f) 0.5f else currentBrightness
                                            activeGestureType = "BRIGHTNESS"
                                            activeGestureValue = gestureBrightnessVal
                                        } else {
                                            gestureVolumeVal = currentVolumeState.coerceIn(0f, 2f)
                                            activeGestureType = "VOLUME"
                                            activeGestureValue = gestureVolumeVal
                                        }
                                    }
                                }
                            } else if (dragDirection == "HORIZONTAL") {
                                dragAccumulatedX += dragAmount.x
                                val seekSecondsMultiplier = 120L
                                val seekDiff = (dragAccumulatedX * seekSecondsMultiplier).toLong()
                                val targetPos = (gestureDragStartedPosition + seekDiff).coerceIn(0L, exoPlayer.duration.coerceAtLeast(1L))
                                currentPosition = targetPos
                                activeGestureType = "SEEK"
                            } else {
                                val sensitivity = 0.0025f
                                if (gestureDragLeft) {
                                    gestureBrightnessVal = (gestureBrightnessVal - dragAmount.y * sensitivity).coerceIn(0f, 1f)
                                    activeGestureValue = gestureBrightnessVal
                                    activeGestureType = "BRIGHTNESS"
                                    
                                    val activity = context as? android.app.Activity
                                    val attrs = activity?.window?.attributes
                                    if (attrs != null) {
                                        attrs.screenBrightness = gestureBrightnessVal
                                        activity.window.attributes = attrs
                                    }
                                } else {
                                    gestureVolumeVal = (gestureVolumeVal - dragAmount.y * sensitivity).coerceIn(0f, 2f)
                                    activeGestureValue = gestureVolumeVal
                                    activeGestureType = "VOLUME"
                                    applyVolume(gestureVolumeVal)
                                }
                            }
                        },
                        onDragEnd = {
                            isSeeking = false
                            if (dragDirection == "HORIZONTAL") {
                                exoPlayer.seekTo(currentPosition)
                            }
                            gestureDismissJob?.cancel()
                            gestureDismissJob = coroutineScope.launch {
                                delay(1200)
                                activeGestureType = null
                                dragDirection = "NONE"
                            }
                        },
                        onDragCancel = {
                            isSeeking = false
                            if (dragDirection == "HORIZONTAL") {
                                exoPlayer.seekTo(currentPosition)
                            }
                            gestureDismissJob?.cancel()
                            gestureDismissJob = coroutineScope.launch {
                                delay(500)
                                activeGestureType = null
                                dragDirection = "NONE"
                            }
                        }
                    )
                }
        )

        // Mid-Screen Gesture Value Indicator HUD (Volume / Brightness)
        AnimatedVisibility(
            visible = if (isInPipMode) false else activeGestureType != null && !areControlsVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                shape = RoundedCornerShape(uiCornerRadius.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                ),
                modifier = Modifier
                    .width(180.dp)
                    .padding(16.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(uiCornerRadius.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                        val gestureType = activeGestureType ?: lastActiveGestureType
                        val value = activeGestureValue
                        val isVolume = gestureType == "VOLUME"
                        val isSeek = gestureType == "SEEK"
                        
                        val icon = if (isSeek) {
                            Icons.Default.FastForward
                        } else if (isVolume) {
                            if (value == 0f) Icons.Default.VolumeMute
                            else if (value < 0.5f) Icons.Default.VolumeDown
                            else Icons.Default.VolumeUp
                        } else {
                            if (value < 0.35f) Icons.Default.BrightnessLow
                            else if (value < 0.7f) Icons.Default.BrightnessMedium
                            else Icons.Default.BrightnessHigh
                        }
                        
                        Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isVolume && value > 1f) Color.Red else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = if (isSeek) "Slide Seek" else if (isVolume) {
                            if (value > 1f) "Volume Boost" else "Volume"
                        } else "Brightness",
                        color = if (isVolume && value > 1f) Color.Red else Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val displayProgress = if (isSeek) {
                        if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f
                    } else {
                        value
                    }
                    
                    if (isVolume) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.25f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val normalProgress = value.coerceAtMost(1f)
                            val boostProgress = if (value > 1f) (value - 1f).coerceAtMost(1f) else 0f
                            
                            // Normal Volume Segment (0% - 100%)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(normalProgress)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(3.dp))
                            
                            // Boost Volume Segment (100% - 200%) in RED Color
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(boostProgress)
                                        .background(Color.Red)
                                )
                            }
                        }
                    } else {
                        LinearProgressIndicator(
                            progress = { displayProgress.coerceIn(0f, 1f) },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = if (isSeek) {
                            "${formatTime(currentPosition)} / ${formatTime(totalDuration)}"
                        } else if (isVolume) {
                            if (value > 1f) {
                                "100% + Red Boost ${((value - 1f) * 100).toInt()}%"
                            } else {
                                "${(value * 100).toInt()}%"
                            }
                        } else {
                            "${(value * 100).toInt()}%"
                        },
                        color = if (isVolume && value > 1f) Color.Red else Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isVolume && value > 1f) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Hold to Speed Custom Overlay
        AnimatedVisibility(
            visible = isHoldToSpeedActive && !isInPipMode,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                val offsetAnim by infiniteTransition.animateFloat(
                    initialValue = -5f,
                    targetValue = 5f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(durationMillis = 300, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    )
                )

                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Hold to Speed",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = offsetAnim.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${String.format("%.1f", holdToSpeedValue)}x Speed",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Double-Tap Seek Feedback
        var lastSeekFeedback by remember { mutableStateOf("FORWARD") }
        LaunchedEffect(activeSeekFeedback) {
            if (activeSeekFeedback != null) {
                lastSeekFeedback = activeSeekFeedback!!
            }
        }

        AnimatedVisibility(
            visible = if (isInPipMode) false else activeSeekFeedback != null && !areControlsVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(if (lastSeekFeedback == "FORWARD") Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
        ) {
            val isForward = lastSeekFeedback == "FORWARD"
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        if (isForward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                        else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Text(
                    text = if (isForward) "+ 10s" else "- 10s",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Custom Gorgeous Immersive Controller HUD Overlay
        AnimatedVisibility(
            visible = if (isInPipMode) false else areControlsVisible && activeGestureType == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.fillMaxSize()
        ) {
            val hudActiveTrackColor: Color
            val hudInactiveTrackColor: Color
            val hudThumbColor: Color
            val mainPlayBtnBgColor: Color
            val mainPlayBtnContentColor: Color
            val skipBtnBgColor: Color
            val skipBtnContentColor: Color
            val isMinimal = playerUiDesign == "MINIMAL"
            val isClassic = playerUiDesign == "CLASSIC"

            val dynamicPrimary = MaterialTheme.colorScheme.primary
            val dynamicOnPrimary = MaterialTheme.colorScheme.onPrimary
            val dynamicSecondaryContainer = MaterialTheme.colorScheme.secondaryContainer
            val dynamicOnSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
            val dynamicTertiary = MaterialTheme.colorScheme.tertiary
            val dynamicSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            val dynamicOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
            val dynamicSurface = MaterialTheme.colorScheme.surface

            when (playerUiDesign) {
                "MINIMAL" -> {
                    hudActiveTrackColor = dynamicPrimary
                    hudInactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    hudThumbColor = dynamicPrimary
                    mainPlayBtnBgColor = Color.Transparent
                    mainPlayBtnContentColor = dynamicPrimary
                    skipBtnBgColor = Color.Transparent
                    skipBtnContentColor = Color.White
                }
                "CLASSIC" -> {
                    hudActiveTrackColor = Color.Red
                    hudInactiveTrackColor = Color.White.copy(alpha = 0.4f)
                    hudThumbColor = Color.Red
                    mainPlayBtnBgColor = Color.White
                    mainPlayBtnContentColor = Color.Black
                    skipBtnBgColor = Color.Black.copy(alpha = navigationButtonOpacity)
                    skipBtnContentColor = Color.White
                }
                "NEON_DREAM" -> {
                    hudActiveTrackColor = Color(0xFFFF007F)
                    hudInactiveTrackColor = Color(0xFF00F0FF).copy(alpha = 0.25f)
                    hudThumbColor = Color(0xFFFF007F)
                    mainPlayBtnBgColor = Color(0xFF00F0FF)
                    mainPlayBtnContentColor = Color.Black
                    skipBtnBgColor = Color(0xFFFF007F).copy(alpha = navigationButtonOpacity)
                    skipBtnContentColor = Color(0xFF00F0FF)
                }
                "MATERIAL_YOU" -> {
                    hudActiveTrackColor = dynamicPrimary
                    hudInactiveTrackColor = dynamicPrimary.copy(alpha = 0.25f)
                    hudThumbColor = dynamicTertiary
                    mainPlayBtnBgColor = dynamicSecondaryContainer
                    mainPlayBtnContentColor = dynamicOnSecondaryContainer
                    skipBtnBgColor = dynamicSurfaceVariant.copy(alpha = navigationButtonOpacity)
                    skipBtnContentColor = dynamicOnSurfaceVariant
                }
                "SOLAR_BURST" -> {
                    hudActiveTrackColor = Color(0xFFFF6F00)
                    hudInactiveTrackColor = Color(0xFFFFD54F).copy(alpha = 0.3f)
                    hudThumbColor = Color(0xFFFFB300)
                    mainPlayBtnBgColor = Color(0xFFFFB300)
                    mainPlayBtnContentColor = Color(0xFF3E2723)
                    skipBtnBgColor = Color(0xFFFF6F00).copy(alpha = navigationButtonOpacity)
                    skipBtnContentColor = Color(0xFFFFB300)
                }
                else -> { // MIDNIGHT
                    hudActiveTrackColor = dynamicPrimary
                    hudInactiveTrackColor = Color.White.copy(alpha = 0.35f)
                    hudThumbColor = dynamicPrimary
                    mainPlayBtnBgColor = dynamicPrimary
                    mainPlayBtnContentColor = dynamicOnPrimary
                    skipBtnBgColor = Color.Black.copy(alpha = navigationButtonOpacity)
                    skipBtnContentColor = Color.White
                }
            }

            val playButtonShape = when (playerUiDesign) {
                "NEON_DREAM" -> RoundedCornerShape(16.dp)
                "MATERIAL_YOU" -> RoundedCornerShape(24.dp)
                "SOLAR_BURST" -> RoundedCornerShape(6.dp)
                else -> CircleShape
            }

            val controlItemBgColor = when (playerUiDesign) {
                "MINIMAL" -> Color.White.copy(alpha = navigationButtonOpacity)
                "CLASSIC" -> Color.White.copy(alpha = navigationButtonOpacity)
                "NEON_DREAM" -> Color(0xFFFF007F).copy(alpha = navigationButtonOpacity)
                "MATERIAL_YOU" -> dynamicSecondaryContainer.copy(alpha = navigationButtonOpacity)
                "SOLAR_BURST" -> Color(0xFFFFB300).copy(alpha = navigationButtonOpacity)
                else -> Color.White.copy(alpha = navigationButtonOpacity)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isMinimal) {
                            Modifier.background(Color.Transparent)
                        } else {
                            val gradientColors = when (playerUiDesign) {
                                "NEON_DREAM" -> listOf(Color(0xFF0D0214).copy(alpha = 0.85f), Color.Transparent, Color(0xFF14020D).copy(alpha = 0.9f))
                                "SOLAR_BURST" -> listOf(Color(0xFF211510).copy(alpha = 0.85f), Color.Transparent, Color(0xFF140B04).copy(alpha = 0.9f))
                                "MATERIAL_YOU" -> listOf(dynamicSurface.copy(alpha = 0.75f), Color.Transparent, dynamicSurface.copy(alpha = 0.85f))
                                else -> listOf(Color.Black.copy(alpha = playerBackgroundOpacity), Color.Transparent, Color.Black.copy(alpha = (playerBackgroundOpacity + 0.1f).coerceAtMost(1f)))
                            }
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = gradientColors
                                )
                            )
                        }
                    )
            ) {
                // Header (Back button, Music button, Title, Format identifier)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (playerUiDesign == "CLASSIC") Color.Black.copy(alpha = 0.85f) else Color.Transparent)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = handleClose,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = navigationButtonOpacity), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close Player",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { showAudioTrackDialog = true },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = navigationButtonOpacity), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Select Language Track",
                            tint = if (audioTracks.size > 1) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            val sendIntent: android.content.Intent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "video/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(video.uri))
                                putExtra(android.content.Intent.EXTRA_SUBJECT, video.title)
                                putExtra(android.content.Intent.EXTRA_TEXT, "Check out this video: ${video.title}")
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Video")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = navigationButtonOpacity), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Video",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val isPortrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                        if (!isPortrait) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (video.author.isNotBlank()) {
                            Text(
                                text = video.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }


                    // Codec Switcher Chip
                    Surface(
                        color = if (preferredCodec == "H264") 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) 
                        else 
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable {
                                preferredCodec = if (preferredCodec == "H264") "H265" else "H264"
                                android.widget.Toast.makeText(
                                    context, 
                                    "Codec: ${if (preferredCodec == "H264") "H.264 AVC (Primary)" else "H.265 HEVC (Secondary)"}", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (preferredCodec == "H264") Icons.Default.CheckCircle else Icons.Default.OfflineBolt,
                                contentDescription = null,
                                tint = if (preferredCodec == "H264") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (preferredCodec == "H264") "H.264 AVC" else "H.265 HEVC",
                                color = if (preferredCodec == "H264") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Compact Autoplay toggle next to the header title
                    Switch(
                        checked = isAutoPlayEnabled,
                        onCheckedChange = onAutoPlayToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .scale(0.8f)
                    )
                }

                // Center Navigation & Play/Pause (Previous, Play/Pause, Next)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Previous Button
                    IconButton(
                        onClick = { onPlayPrevious?.invoke() },
                        enabled = onPlayPrevious != null,
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                if (isMinimal) Color.Transparent
                                else if (onPlayPrevious != null) skipBtnBgColor
                                else skipBtnBgColor.copy(alpha = (navigationButtonOpacity / 3f).coerceAtLeast(0.05f)),
                                playButtonShape
                            )
                            .let {
                                if (isMinimal && onPlayPrevious != null) {
                                    it.border(1.5.dp, Color.White.copy(alpha = 0.6f), playButtonShape)
                                } else it
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Video",
                            tint = if (onPlayPrevious != null) skipBtnContentColor else skipBtnContentColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Play Toggle
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                            isPlayingState = exoPlayer.isPlaying
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                mainPlayBtnBgColor,
                                playButtonShape
                            )
                            .let {
                                if (isMinimal) {
                                    it.border(2.dp, hudActiveTrackColor, playButtonShape)
                                } else it
                            }
                    ) {
                        Icon(
                            imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause video",
                            tint = mainPlayBtnContentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Next Button
                    IconButton(
                        onClick = { onPlayNext?.invoke() },
                        enabled = onPlayNext != null,
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                if (isMinimal) Color.Transparent
                                else if (onPlayNext != null) skipBtnBgColor
                                else skipBtnBgColor.copy(alpha = (navigationButtonOpacity / 3f).coerceAtLeast(0.05f)),
                                playButtonShape
                            )
                            .let {
                                if (isMinimal && onPlayNext != null) {
                                    it.border(1.5.dp, Color.White.copy(alpha = 0.6f), playButtonShape)
                                } else it
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Video",
                            tint = if (onPlayNext != null) skipBtnContentColor else skipBtnContentColor.copy(alpha = 0.35f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Footer section - Timeline, Progress, Speed dial trigger
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(if (playerUiDesign == "CLASSIC") Color.Black.copy(alpha = 0.85f) else Color.Transparent)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    // Progress Slider Timeline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        
                        if (seekBarDesign == "SNAKE") {
                            SnakeSeekBar(
                                value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                                onValueChange = { percent ->
                                    isSeeking = true
                                    currentPosition = (percent * totalDuration).toLong()
                                },
                                onValueChangeFinished = {
                                    isSeeking = false
                                    exoPlayer.seekTo(currentPosition)
                                },
                                activeColor = hudActiveTrackColor,
                                inactiveColor = hudInactiveTrackColor,
                                thumbColor = hudThumbColor,
                                isPlaying = isPlayingState,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )
                        } else {
                            Slider(
                                value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                                onValueChange = { percent ->
                                    isSeeking = true
                                    currentPosition = (percent * totalDuration).toLong()
                                },
                                onValueChangeFinished = {
                                    isSeeking = false
                                    exoPlayer.seekTo(currentPosition)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = hudActiveTrackColor,
                                    inactiveTrackColor = hudInactiveTrackColor,
                                    thumbColor = hudThumbColor
                                )
                            )
                        }

                        Text(
                            text = formatTime(totalDuration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speed, Aspect Ratio, and Rotation Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Section: Speed + Aspect Ratio Adjuster
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Speed Trigger Chip
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(controlItemBgColor)
                                        .clickable { showSpeedDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Speed,
                                        contentDescription = "Playback Speed Indicator",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Speed: ${speed}x",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Aspect Ratio View Angel (Fit Screen / Crop Zoom) Chip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(controlItemBgColor)
                                    .clickable {
                                        resizeModeState = if (resizeModeState == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        } else {
                                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                        onVideoResizeModeChange(resizeModeState)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (resizeModeState == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) Icons.Default.AspectRatio else Icons.Default.Crop,
                                    contentDescription = "Toggle Video View",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Filters Chip (Adaptive Color Grading/HDR options)
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (activeFilterKey != "ORIGINAL") MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            else controlItemBgColor
                                        )
                                        .clickable { showFiltersMenu = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterBAndW,
                                        contentDescription = "Color Filter Selector",
                                        tint = if (activeFilterKey != "ORIGINAL") MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Subtitle Toggle Chip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSubtitleEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        else controlItemBgColor
                                    )
                                    .clickable { onSubtitleToggle(!isSubtitleEnabled) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSubtitleEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                    contentDescription = "Toggle Subtitle",
                                    tint = if (isSubtitleEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Right Section: Screen Rotation Dialog Controller & PIP
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isAutoRotateEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        else controlItemBgColor
                                    )
                                    .clickable {
                                        if (isAutoRotateEnabled) {
                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                                            isAutoRotateEnabled = false
                                            android.widget.Toast.makeText(context, "Rotation locked in current state", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                            isAutoRotateEnabled = true
                                            android.widget.Toast.makeText(context, "Full sensor auto-rotation enabled", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Auto Rotate Screen",
                                    tint = if (isAutoRotateEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Picture in Picture (PiP) Pop-up Player Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(controlItemBgColor)
                                    .clickable {
                                        val activity = context as? android.app.Activity
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            val params = android.app.PictureInPictureParams.Builder().build()
                                            activity?.enterPictureInPictureMode(params)
                                        } else {
                                            activity?.enterPictureInPictureMode()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPicture,
                                    contentDescription = "Picture in Picture Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lock Icon overlay (left middle)
        AnimatedVisibility(
            visible = if (isInPipMode) false else areControlsVisible || isLocked,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            androidx.compose.material3.IconButton(
                onClick = { 
                    isLocked = !isLocked
                    if (isLocked) {
                        areControlsVisible = false
                    } else {
                        areControlsVisible = true
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isLocked) "Unlock" else "Lock",
                    tint = Color.White
                )
            }
        }

        // Better Filter & Visual Enhancements Dialog
        if (showFiltersMenu) {
            AlertDialog(
                onDismissRequest = { showFiltersMenu = false },
                shape = RoundedCornerShape(uiCornerRadius.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterBAndW,
                            contentDescription = "Filters",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Visual Color Effects",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Trending Color Grading",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val trendingFilters = listOf(
                            "ORIGINAL" to "Original",
                            "WARM_SUNSET" to "Warm Sunset 🌅",
                            "CYBERPUNK" to "Cyberpunk Neo 🔮",
                            "VINTAGE_NOIR" to "Vintage Noir 🎬",
                            "COOL_MIST" to "Cool Mist ❄️",
                            "MUTED_FOREST" to "Muted Forest 🌿"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val chunkedTrending = trendingFilters.chunked(2)
                            chunkedTrending.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (key, name) ->
                                        val isSelected = activeFilterKey == key
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    activeFilterKey = key
                                                    activeFilterName = name
                                                    onVideoFilterChange(key)
                                                }
                                                .border(
                                                    width = 2.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (rowItems.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Text(
                            text = "HDR Visual Enhancers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val hdrFilters = listOf(
                            "HDR_VIVID" to "HDR Vivid ✨",
                            "HDR_CRYSTAL" to "HDR Crystal Clear 💎",
                            "HDR_DEEP_CONTRAST" to "HDR Deep Contrast 🌓",
                            "HDR_ULTRA_BRIGHT" to "HDR Ultra Bright 🔆"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val chunkedHdr = hdrFilters.chunked(2)
                            chunkedHdr.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (key, name) ->
                                        val isSelected = activeFilterKey == key
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    activeFilterKey = key
                                                    activeFilterName = name
                                                    onVideoFilterChange(key)
                                                }
                                                .border(
                                                    width = 2.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (rowItems.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showFiltersMenu = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Apply",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            activeFilterKey = "ORIGINAL"
                            activeFilterName = "Original"
                            onVideoFilterChange("ORIGINAL")
                            showFiltersMenu = false
                        }
                    ) {
                        Text("Reset")
                    }
                }
              )
        }

        // Custom Audio Track Picker Dialog
        if (showAudioTrackDialog) {
            AlertDialog(
                onDismissRequest = { showAudioTrackDialog = false },
                shape = RoundedCornerShape(uiCornerRadius.dp),
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Audio Language")
                    }
                },
                text = {
                    if (audioTracks.isEmpty()) {
                        Text(
                            "No multi-language audio tracks detected for this file.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            audioTracks.forEachIndexed { index, track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (track.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        track.mediaTrackGroup,
                                                        listOf(track.trackIndex)
                                                    )
                                                )
                                                .build()
                                            showAudioTrackDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (track.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (track.isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioTrackDialog = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        // Custom Playback Speed Slider Dialog
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                shape = RoundedCornerShape(uiCornerRadius.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Playback Speed",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = String.format("Current Speed: %.2fx", speed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Slider(
                            value = speed,
                            onValueChange = { newSpeed ->
                                val rounded = (newSpeed * 100).roundToInt() / 100f
                                onSpeedChange(rounded)
                            },
                            valueRange = 0.25f..2.0f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.25x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("1.0x (Normal)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Presets",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { preset ->
                                OutlinedButton(
                                    onClick = { onSpeedChange(preset) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = if (speed == preset) {
                                        ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Text(
                                        text = "${preset}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

// Format duration long to MM:SS string
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

// Read MIME extension and label file formats gracefully
private fun getFormatLabel(video: VideoItem): String {
    val m = video.mimeType.lowercase()
    val u = video.uri.lowercase()
    return when {
         m.contains("mpegurl") || m.contains("hls") || u.contains(".m3u8") -> "HLS STREAM"
         m.contains("dash") || u.contains(".mpd") || m.contains("mpd") -> "DASH STREAM"
         m.contains("webm") || u.endsWith(".webm") -> "WEBM"
         m.contains("matroska") || u.endsWith(".mkv") -> "MKV CODR"
         m.contains("quicktime") || m.contains("mov") || u.endsWith(".mov") -> "MOV"
         m.contains("avi") || m.contains("x-msvideo") || u.endsWith(".avi") -> "AVI"
         m.contains("mp4") || u.endsWith(".mp4") -> "MP4 FHD"
         else -> "VIDEO"
    }
}

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val isSelected: Boolean,
    val mediaTrackGroup: androidx.media3.common.TrackGroup
)

@Composable
fun SnakeSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SnakePhase"
    )

    Canvas(
        modifier = modifier
            .height(24.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (width > 0) {
                            val newValue = (offset.x / width).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        if (width > 0) {
                            val newValue = (change.position.x / width).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }
                    }
                )
            }
    ) {
        width = size.width
        height = size.height

        val thumbX = width * value
        val centerY = height / 2f

        // Draw Inactive Track
        drawLine(
            color = inactiveColor,
            start = Offset(thumbX, centerY),
            end = Offset(width, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Draw Active Track (Snake if playing, otherwise straight)
        if (isPlaying && !isDragging) {
            val path = Path()
            path.moveTo(0f, centerY)
            val amplitude = 4.dp.toPx()
            val frequency = 40.dp.toPx()
            
            for (x in 0..thumbX.toInt() step 2) {
                val dx = x.toFloat()
                val dy = centerY + Math.sin(((dx / frequency) * 2 * Math.PI + phase)).toFloat() * amplitude
                path.lineTo(dx, dy)
            }
            if (thumbX > 0) {
                val endDy = centerY + Math.sin(((thumbX / frequency) * 2 * Math.PI + phase)).toFloat() * amplitude
                path.lineTo(thumbX, endDy)
            }

            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round)
            )
        } else {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw Thumb
        drawCircle(
            color = thumbColor,
            radius = 8.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}
