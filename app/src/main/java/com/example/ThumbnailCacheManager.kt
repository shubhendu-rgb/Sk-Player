package com.example

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailCacheManager {
    private const val TAG = "ThumbnailCacheManager"
    private const val CACHE_DIR_NAME = "video_thumbnails"

    fun getThumbnailFile(context: Context, videoId: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File(cacheDir, "thumb_$videoId.jpg")
    }

    suspend fun getOrCreateThumbnail(context: Context, videoId: String, videoUri: String): String? = withContext(Dispatchers.IO) {
        val file = getThumbnailFile(context, videoId)
        if (file.exists() && file.length() > 0) {
            return@withContext Uri.fromFile(file).toString()
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val uriToLoad = if (videoUri.startsWith("content://")) Uri.parse(videoUri) else {
                    val f = File(videoUri)
                    if (f.exists()) Uri.fromFile(f) else null
                }
                
                if (uriToLoad != null) {
                    val bitmap = context.contentResolver.loadThumbnail(uriToLoad, android.util.Size(512, 512), null)
                    if (bitmap != null) {
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                        }
                        bitmap.recycle()
                        return@withContext Uri.fromFile(file).toString()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ContentResolver loadThumbnail failed for $videoUri: ${e.message}")
            }
        }

        // Generate using MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            if (videoUri.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(videoUri))
            } else {
                retriever.setDataSource(videoUri)
            }
            // Get frame at 1 second
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()

            if (bitmap != null) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
                bitmap.recycle()
                return@withContext Uri.fromFile(file).toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail for $videoUri: ${e.message}")
        }
        return@withContext null
    }

    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (f in files) {
                        f.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear thumbnail cache: ${e.message}")
        }
    }
}
