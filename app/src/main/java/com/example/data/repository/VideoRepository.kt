package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistItemDao
import com.example.data.database.PlaylistItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL

data class VideoItem(
    val id: String,
    val title: String,
    val uri: String,
    val duration: Long = 0L,
    val size: Long = 0L,
    val mimeType: String = "video/mp4",
    val isLocal: Boolean = false,
    val thumbnailUri: String? = null,
    val author: String = "Sk Player Local",
    val dateAdded: Long = 0L
)

data class StatusMedia(
    val id: String,
    val path: String,
    val uri: String,
    val isVideo: Boolean,
    val size: Long,
    val addedTime: Long,
    val isSaved: Boolean = false,
    val simulatedUrl: String? = null // For cloud-simulated mock backgrounds
)

class VideoRepository(private val dao: PlaylistItemDao) {

    // --- Room Database Stream Operations ---
    val allPlaylists: Flow<List<PlaylistEntity>> = dao.getAllPlaylists()

    fun getPlaylistById(id: Int): Flow<PlaylistEntity?> = dao.getPlaylistById(id)

    fun getItemsForPlaylist(playlistId: Int): Flow<List<PlaylistItemEntity>> =
        dao.getItemsForPlaylist(playlistId)

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        dao.insertPlaylist(PlaylistEntity(name = name, description = description))
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        dao.updatePlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlist)
    }

    suspend fun addToPlaylist(playlistId: Int, video: VideoItem) = withContext(Dispatchers.IO) {
        dao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                title = video.title,
                uri = video.uri,
                duration = video.duration,
                size = video.size,
                thumbnailUri = video.thumbnailUri ?: ""
            )
        )
    }

    suspend fun deletePlaylistItem(itemId: Int) = withContext(Dispatchers.IO) {
        dao.deletePlaylistItem(itemId)
    }

    private fun scanDirectoryForVideos(
        dir: File, 
        list: MutableList<VideoItem>, 
        seenPaths: MutableSet<String>, 
        prefs: android.content.SharedPreferences,
        depth: Int = 0
    ) {
        if (depth > 4) return
        if (!dir.exists() || !dir.isDirectory) return
        val files = try { dir.listFiles() } catch (e: Exception) { null } ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanDirectoryForVideos(file, list, seenPaths, prefs, depth + 1)
                }
            } else {
                val path = file.absolutePath
                if (seenPaths.contains(path)) continue
                
                val extension = file.extension.lowercase()
                val isVideo = extension == "mp4" || extension == "mkv" || extension == "webm" || extension == "avi" || extension == "3gp"
                if (isVideo) {
                    val size = file.length()
                    if (size <= 0) continue
                    val lastModified = file.lastModified()
                    
                    val hash = path.hashCode() and 0x7FFFFFFF
                    val id = "local_scanned_$hash"
                    if (prefs.getBoolean("deleted_$id", false)) continue
                    
                    val displayName = prefs.getString("title_override_$id", file.name) ?: file.name
                    val duration = 0L
                    
                    list.add(
                        VideoItem(
                            id = id,
                            title = displayName,
                            uri = path,
                            duration = duration,
                            size = size,
                            mimeType = "video/$extension",
                            isLocal = true,
                            thumbnailUri = Uri.fromFile(file).toString(),
                            dateAdded = lastModified
                        )
                    )
                    seenPaths.add(path)
                }
            }
        }
    }

    // --- Video Format Scanning & Fallback Library ---
    suspend fun scanLocalVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val seenPaths = mutableSetOf<String>()
        val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
        
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED
            )
            
            val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val path = cursor.getString(dataCol)
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol)
                    val dateAddedSec = cursor.getLong(dateCol)
                    
                    val videoId = "local_$id"
                    if (prefs.getBoolean("deleted_$videoId", false)) continue
                    val displayName = prefs.getString("title_override_$videoId", name ?: "Video $id") ?: "Video $id"

                    val videoUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()).toString()
                    if (path != null) {
                        seenPaths.add(path)
                    }
                    list.add(
                        VideoItem(
                            id = videoId,
                            title = displayName,
                            uri = path ?: videoUri,
                            duration = duration,
                            size = size,
                            mimeType = mime ?: "video/mp4",
                            isLocal = true,
                            thumbnailUri = videoUri,
                            dateAdded = dateAddedSec * 1000L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error query MediaStore: ${e.message}")
        }

        // Also recursively scan standard external public folders to discover any files that aren't registered yet in MediaStore
        try {
            val dirsToScan = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            )
            for (dir in dirsToScan) {
                scanDirectoryForVideos(dir, list, seenPaths, prefs)
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Direct directory scan failed: ${e.message}")
        }

        // If the device has no videos (like empty emulator folders), populate high-fidelity simulation entries
        if (list.isEmpty()) {
            val rawSims = getSimulatedLocalVideos()
            for (sim in rawSims) {
                if (!prefs.getBoolean("deleted_${sim.id}", false)) {
                    val title = prefs.getString("title_override_${sim.id}", sim.title) ?: sim.title
                    list.add(sim.copy(title = title))
                }
            }
        }

        list
    }

    private fun getSimulatedLocalVideos(): List<VideoItem> {
        return listOf(
            VideoItem(
                id = "sim_1",
                title = "[Local Video] Glacier Travel Vibe.mp4",
                uri = "https://assets.mixkit.co/videos/preview/mixkit-glacier-nature-travel-vibe-40742-large.mp4",
                duration = 27000,
                size = 12450000L,
                mimeType = "video/mp4",
                isLocal = true,
                thumbnailUri = "https://images.unsplash.com/photo-1519681393784-d120267933ba"
            ),
            VideoItem(
                id = "sim_2",
                title = "[Local Video] Forest Drone Flyover.mp4",
                uri = "https://assets.mixkit.co/videos/preview/mixkit-forest-aerial-flyover-with-sunbeams-39875-large.mp4",
                duration = 18000,
                size = 8900000L,
                mimeType = "video/mp4",
                isLocal = true,
                thumbnailUri = "https://images.unsplash.com/photo-1511497584788-876760111969"
            ),
            VideoItem(
                id = "sim_3",
                title = "[Local Video] Sunset City Drive.mkv",
                uri = "https://assets.mixkit.co/videos/preview/mixkit-under-bridges-at-sunset-41584-large.mp4",
                duration = 34000,
                size = 15700000L,
                mimeType = "video/x-matroska",
                isLocal = true,
                thumbnailUri = "https://images.unsplash.com/photo-1514565131-fce0801e5785"
            )
        )
    }

    fun getSampleCloudVideos(): List<VideoItem> {
        return emptyList()
    }

    // --- WhatsApp Status Saver Scan & Processing ---
    suspend fun getWhatsAppStatuses(context: Context): List<StatusMedia> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StatusMedia>()
        val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
        
        // 1. Scan via Selected SAF Folder if users granted folder access
        val safUriStr = prefs.getString("whatsapp_saf_uri", null)
        if (!safUriStr.isNullOrEmpty()) {
            try {
                val treeUri = Uri.parse(safUriStr)
                val documentFile = DocumentFile.fromTreeUri(context, treeUri)
                val files = documentFile?.listFiles() ?: emptyArray()
                for (doc in files) {
                    if (doc.isFile && doc.name != null && !doc.name!!.startsWith(".")) {
                        val lower = doc.name!!.lowercase()
                        val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".avi")
                        val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")
                        if (isVideo || isImage) {
                            val statusId = "whatsapp_${doc.name!!.hashCode()}"
                            if (prefs.getBoolean("deleted_$statusId", false)) continue
                            val displayName = prefs.getString("title_override_$statusId", doc.name) ?: doc.name!!
                            val savedFile = getPublicSavedFile(doc.name!!, isVideo)
                            list.add(
                                StatusMedia(
                                    id = statusId,
                                    path = doc.uri.toString(), // path will hold the document Uri
                                    uri = doc.uri.toString(),
                                    isVideo = isVideo,
                                    size = doc.length(),
                                    addedTime = doc.lastModified(),
                                    isSaved = savedFile?.exists() == true
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoRepository", "Error scanning statuses via SAF: ${e.message}")
            }
        }

        // 2. Scan standard and clone paths for WhatsApp Status folder
        val possibleDirs = listOf(
            File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
            File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp Business/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.gbwhatsapp/GBWhatsApp/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), "GBWhatsApp/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/.Statuses/"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Statuses/")
        )

        for (dir in possibleDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && !file.name.startsWith(".")) {
                            val lower = file.name.lowercase()
                            val isVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".avi")
                            val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")
                            
                            if (isVideo || isImage) {
                                val statusId = "whatsapp_${file.name.hashCode()}"
                                
                                // Prevent duplicates if already loaded from SAF
                                if (list.any { it.id == statusId }) continue
                                if (prefs.getBoolean("deleted_$statusId", false)) continue
                                
                                val displayName = prefs.getString("title_override_$statusId", file.name) ?: file.name
                                val savedFile = getPublicSavedFile(file.name, isVideo)
                                list.add(
                                    StatusMedia(
                                        id = statusId,
                                        path = file.absolutePath,
                                        uri = Uri.fromFile(file).toString(),
                                        isVideo = isVideo,
                                        size = file.length(),
                                        addedTime = file.lastModified(),
                                        isSaved = savedFile?.exists() == true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Include high-fidelity simulation entries (renamed/filtered)
        val rawSims = getSimulatedStatuses()
        for (sim in rawSims) {
            if (!prefs.getBoolean("deleted_${sim.id}", false)) {
                val overriddenName = prefs.getString("title_override_${sim.id}", sim.path) ?: sim.path
                list.add(sim.copy(path = overriddenName))
            }
        }
        
        // Sort newest statuses first
        list.sortByDescending { it.addedTime }
        list
    }

    private fun getSimulatedStatuses(): List<StatusMedia> {
        return emptyList()
    }

    // --- File operations: Edit (Rename, Delete) ---
    suspend fun renameLocalFile(context: Context, video: VideoItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("title_override_${video.id}", newName).apply()
            
            if (!video.id.startsWith("sim_")) {
                // Physical file rename attempt
                val file = File(video.uri)
                if (file.exists()) {
                    val parent = file.parentFile
                    val ext = file.extension
                    val nameWithExt = if (newName.endsWith(".$ext", ignoreCase = true)) newName else "$newName.$ext"
                    val destFile = File(parent, nameWithExt)
                    if (file.renameTo(destFile)) {
                        // Scan file
                        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error renaming local file: ${e.message}")
            false
        }
    }

    suspend fun deleteLocalFile(context: Context, video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("deleted_${video.id}", true).apply()
            
            if (!video.id.startsWith("sim_")) {
                val file = File(video.uri)
                if (file.exists()) {
                    file.delete()
                }
                
                val mediaId = video.id.substringAfter("local_").toLongOrNull()
                if (mediaId != null) {
                    val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    context.contentResolver.delete(
                        Uri.withAppendedPath(contentUri, mediaId.toString()),
                        null,
                        null
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error deleting local file: ${e.message}")
            false
        }
    }

    suspend fun renameStatusFile(context: Context, status: StatusMedia, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("title_override_${status.id}", newName).apply()
            
            if (!status.id.contains("status_sim_")) {
                val uriParsed = Uri.parse(status.path)
                if (uriParsed.scheme == "file" || uriParsed.scheme == null) {
                    val file = File(status.path)
                    if (file.exists()) {
                        val parent = file.parentFile
                        val ext = file.extension
                        val nameWithExt = if (newName.endsWith(".$ext", ignoreCase = true)) newName else "$newName.$ext"
                        val destFile = File(parent, nameWithExt)
                        if (file.renameTo(destFile)) {
                            Log.d("VideoRepository", "Status physically renamed to $nameWithExt")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error renaming status file: ${e.message}")
            false
        }
    }

    suspend fun deleteStatusFile(context: Context, status: StatusMedia): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("playstatus_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("deleted_${status.id}", true).apply()
            
            if (!status.id.contains("status_sim_")) {
                val uriParsed = Uri.parse(status.path)
                if (uriParsed.scheme == "file" || uriParsed.scheme == null) {
                    val file = File(status.path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error deleting status file: ${e.message}")
            false
        }
    }

    private fun getPublicSavedFile(fileName: String, isVideo: Boolean): File? {
        val rootDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        return File(rootDir, fileName)
    }

    suspend fun saveStatusToLocal(context: Context, status: StatusMedia): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = status.path.substringAfterLast('/')
            val isVideo = status.isVideo
            
            // For both genuine WhatsApp statuses (uri with file scheme) and simulated statuses (uri is internet content),
            // this function will retrieve and streams into public gallery storage!
            
            val outputStream: FileOutputStream
            val resolvedUri: Uri
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Modern Scoped Storage implementation (using public MediaStore database references)
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES
                    )
                }
                
                val collection = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                
                var insertedUri: Uri? = null
                try {
                    insertedUri = resolver.insert(collection, contentValues)
                } catch (e: Exception) {
                    Log.e("VideoRepository", "Inserting image to Movies folder failed, fallback to Pictures: ${e.message}")
                    val fallbackValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                        )
                    }
                    insertedUri = resolver.insert(collection, fallbackValues)
                }
                
                val finalUri = insertedUri ?: return@withContext false
                resolvedUri = finalUri
                outputStream = resolver.openOutputStream(finalUri) as FileOutputStream
            } else {
                // Android 9 and lower
                val destFile = getPublicSavedFile(fileName, isVideo) ?: return@withContext false
                outputStream = FileOutputStream(destFile)
                resolvedUri = Uri.fromFile(destFile)
            }

            outputStream.use { outputStreamWriter ->
                if (status.simulatedUrl != null || status.uri.startsWith("http")) {
                    // Simulated status requires downloading the cloud stream representation!
                    val url = URL(status.simulatedUrl ?: status.uri)
                    url.openStream().use { inputStream ->
                        inputStream.copyTo(outputStreamWriter)
                    }
                } else {
                    // Genuine WhatsApp status requires local file reader stream!
                    val file = File(status.path)
                    if (file.exists()) {
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStreamWriter)
                        }
                    } else {
                        // Attempt fallback from relative asset / URI stream
                        val uriParsed = Uri.parse(status.uri)
                        context.contentResolver.openInputStream(uriParsed)?.use { inputStream ->
                            inputStream.copyTo(outputStreamWriter)
                        }
                    }
                }
            }

            // Flush media player scanner to register saves immediately in consumer libraries
            MediaScannerConnection.scanFile(
                context,
                arrayOf(resolvedUri.path ?: ""),
                arrayOf(if (isVideo) "video/mp4" else "image/jpeg")
            ) { path, uri ->
                Log.d("VideoRepository", "Media scanned: $path, uri: $uri")
            }
            
            true
        } catch (e: Exception) {
            Log.e("VideoRepository", "Failed saving status: ${e.message}")
            false
        }
    }
}
