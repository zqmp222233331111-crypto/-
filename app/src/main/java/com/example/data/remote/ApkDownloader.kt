package com.example.data.remote

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class DownloadProgressState {
    data object Idle : DownloadProgressState()
    data class Downloading(
        val downloadId: Long,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val isPending: Boolean = false,
        val isPaused: Boolean = false
    ) : DownloadProgressState()
    data class Completed(val downloadId: Long, val fileName: String) : DownloadProgressState()
    data class Failed(val reason: String) : DownloadProgressState()
}

/**
 * Service wrapping Android's [DownloadManager] for direct APK downloads,
 * live progress tracking, system status notifications, and completion broadcasts.
 */
class ApkDownloader(private val context: Context) {

    fun downloadApk(apkUrl: String, versionLabel: String? = null): Result<Pair<Long, String>> {
        val trimmed = apkUrl.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        if (uri == null || (uri.scheme != "https" && uri.scheme != "http")) {
            return Result.failure(
                IllegalArgumentException("ডাউনলোডের জন্য একটি বৈধ HTTP/HTTPS APK লিংক প্রয়োজন।")
            )
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return Result.failure(IllegalStateException("Android DownloadManager উপলব্ধ নয়।"))

        val safeVersion = (versionLabel ?: BuildConfig.VERSION_NAME)
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "Shaila-$safeVersion.apk"

        val request = DownloadManager.Request(uri).apply {
            setTitle("Shaila ($safeVersion)")
            setDescription("Downloading Shaila APK...")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
        }

        return try {
            val downloadId = dm.enqueue(request)
            Result.success(downloadId to fileName)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "ডাউনলোড শুরু করা যায়নি।"))
        }
    }

    fun observeDownload(downloadId: Long, fileName: String): Flow<DownloadProgressState> = callbackFlow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            trySend(DownloadProgressState.Failed("DownloadManager unavailable"))
            close()
            return@callbackFlow
        }

        fun querySnapshot(): Pair<DownloadProgressState, Boolean> {
            val query = DownloadManager.Query().setFilterById(downloadId)
            dm.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return DownloadProgressState.Failed("ডাউনলোড বাতিল করা হয়েছে") to true
                }
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
                val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx) else 0L
                val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L
                val pct = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0

                return when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        DownloadProgressState.Completed(downloadId, fileName) to true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reasonCode = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                        DownloadProgressState.Failed("ডাউনলোড ব্যর্থ হয়েছে (Code $reasonCode)") to true
                    }
                    DownloadManager.STATUS_PENDING -> {
                        DownloadProgressState.Downloading(
                            downloadId = downloadId,
                            progressPercent = pct,
                            bytesDownloaded = downloaded,
                            totalBytes = total,
                            isPending = true
                        ) to false
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        DownloadProgressState.Downloading(
                            downloadId = downloadId,
                            progressPercent = pct,
                            bytesDownloaded = downloaded,
                            totalBytes = total,
                            isPaused = true
                        ) to false
                    }
                    else -> {
                        DownloadProgressState.Downloading(
                            downloadId = downloadId,
                            progressPercent = pct,
                            bytesDownloaded = downloaded,
                            totalBytes = total
                        ) to false
                    }
                }
            }
            return DownloadProgressState.Failed("ডাউনলোড বাতিল করা হয়েছে") to true
        }

        val completionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (completedId == downloadId) {
                        val (state, terminal) = querySnapshot()
                        trySend(state)
                        if (terminal) close()
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            completionReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        val pollJob = launch {
            while (isActive) {
                val (state, terminal) = querySnapshot()
                trySend(state)
                if (terminal) {
                    close()
                    break
                }
                delay(400)
            }
        }

        awaitClose {
            pollJob.cancel()
            runCatching { context.unregisterReceiver(completionReceiver) }
        }
    }

    fun cancelDownload(downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        runCatching { dm.remove(downloadId) }
    }

    fun openSystemDownloads() {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
