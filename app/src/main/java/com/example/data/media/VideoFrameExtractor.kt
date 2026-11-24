package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class ExtractedFrame(
    val bitmap: Bitmap,
    val base64Jpeg: String
)

data class MediaMetadataSummary(
    val displayName: String,
    val isVideo: Boolean,
    val durationMs: Long
)

class VideoFrameExtractor(private val context: Context) {

    suspend fun inspectMedia(uri: Uri): MediaMetadataSummary = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video/")
        var name = "Media"
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx) ?: name
                    }
                }
        }
        var duration = 0L
        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
            runCatching { retriever.release() }
        }
        MediaMetadataSummary(displayName = name, isVideo = isVideo, durationMs = duration)
    }

    suspend fun extractVideoFrames(
        uri: Uri,
        frameCount: Int = 5
    ): List<ExtractedFrame> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<ExtractedFrame>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val safeCount = frameCount.coerceIn(2, 6)

            for (i in 0 until safeCount) {
                val timeUs = if (durationMs <= 0L) {
                    i * 500_000L
                } else {
                    val ratio = (i + 0.5) / safeCount.toDouble()
                    (durationMs * 1000.0 * ratio).toLong().coerceAtLeast(0L)
                }
                val rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (rawBitmap != null) {
                    val scaled = scaleBitmap(rawBitmap, maxDimension = 720)
                    if (scaled !== rawBitmap) rawBitmap.recycle()
                    val base64 = bitmapToBase64(scaled, quality = 78)
                    frames.add(ExtractedFrame(scaled, base64))
                }
            }
        } catch (_: Exception) {
            // Return whatever frames were decoded cleanly
        } finally {
            runCatching { retriever.release() }
        }
        frames
    }

    suspend fun loadVideoThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let { scaleBitmap(it, maxDimension = 420) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun encodePhotoToBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val decoded = BitmapFactory.decodeStream(stream) ?: return@use null
                val scaled = scaleBitmap(decoded, maxDimension = 1024)
                if (scaled !== decoded) decoded.recycle()
                val encoded = bitmapToBase64(scaled, quality = 82)
                scaled.recycle()
                encoded
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val scale = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
