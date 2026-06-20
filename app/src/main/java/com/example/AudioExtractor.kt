package com.example

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioExtractor {
    private const val TAG = "AudioExtractor"

    suspend fun extractAudio(context: Context, videoUri: String, videoTitle: String): File? = withContext(Dispatchers.IO) {
        try {
            val musicDir = File("/storage/emulated/0/Music")
            if (!musicDir.exists()) {
                musicDir.mkdirs()
            }

            val cleanTitle = videoTitle.replace(Regex("[^a-zA-Z0-9_.-]"), "_")

            val extractor = MediaExtractor()
            if (videoUri.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(videoUri), null)
            } else {
                extractor.setDataSource(videoUri)
            }

            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                extractor.release()
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val muxerFormat = when {
                mime.contains("opus") || mime.contains("vorbis") -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                mime.contains("amr") || mime.contains("3gpp") -> MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
                android.os.Build.VERSION.SDK_INT >= 29 && mime.contains("ogg") -> MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            val outputFile = File(musicDir, "$cleanTitle.mp3")

            val muxer = MediaMuxer(outputFile.absolutePath, muxerFormat)
            val muxerAudioTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = try { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) } catch (e: Exception) { 64 * 1024 }
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val info = android.media.MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }
                
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerAudioTrackIndex, buffer, info)
                
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            Log.d(TAG, "Audio successfully extracted to ${outputFile.absolutePath}")
            return@withContext outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio: ${e.message}")
        }
        return@withContext null
    }
}
