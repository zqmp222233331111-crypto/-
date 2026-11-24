package com.example.data.remote

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    val name: String? = null,
    val size: Long? = null,
    val browser_download_url: String? = null
)

@JsonClass(generateAdapter = true)
data class ReleaseManifestDto(
    val versionName: String? = null,
    val version: String? = null,
    val tag_name: String? = null,
    val versionCode: Int? = null,
    val apkUrl: String? = null,
    val downloadUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val releaseNotes: String? = null,
    val body: String? = null,
    val assets: List<GitHubAssetDto>? = null
)

data class BuildVersionInfo(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val latestVersionName: String? = null,
    val latestVersionCode: Int? = null,
    val resolvedApkUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val releaseNotes: String? = null,
    val isUpdateAvailable: Boolean = false
)

class AppUpdateManager(private val context: Context) {

    private val apkDownloader = ApkDownloader(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getCurrentBuildInfo(): BuildVersionInfo = BuildVersionInfo(
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE
    )

    suspend fun checkLatestBuild(sourceUrl: String): Result<BuildVersionInfo> = withContext(Dispatchers.IO) {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("অনুগ্রহ করে একটি বৈধ APK বা Release JSON URL দিন।")
            )
        }
        val parsedUri = runCatching { Uri.parse(trimmed) }.getOrNull()
        if (parsedUri == null || (parsedUri.scheme != "https" && parsedUri.scheme != "http")) {
            return@withContext Result.failure(
                IllegalArgumentException("সঠিক HTTP/HTTPS URL প্রদান করুন।")
            )
        }

        try {
            val effectiveUrl = normalizeReleaseSourceUrl(trimmed)
            val effectiveUri = runCatching { Uri.parse(effectiveUrl) }.getOrNull() ?: parsedUri
            val isDirectApk = effectiveUri.path?.endsWith(".apk", ignoreCase = true) == true ||
                effectiveUrl.endsWith(".apk", ignoreCase = true)
            if (isDirectApk) {
                val headReq = Request.Builder().url(effectiveUrl).head().build()
                client.newCall(headReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("সার্ভার থেকে তথ্য পাওয়া যায়নি (HTTP ${resp.code})")
                        )
                    }
                    val len = resp.header("Content-Length")?.toLongOrNull()
                    return@withContext Result.success(
                        BuildVersionInfo(
                            currentVersionName = BuildConfig.VERSION_NAME,
                            currentVersionCode = BuildConfig.VERSION_CODE,
                            latestVersionName = BuildConfig.VERSION_NAME,
                            latestVersionCode = BuildConfig.VERSION_CODE,
                            resolvedApkUrl = effectiveUrl,
                            fileSizeBytes = len,
                            releaseNotes = "Direct APK verified and ready for DownloadManager.",
                            isUpdateAvailable = false
                        )
                    )
                }
            } else {
                val req = Request.Builder()
                    .url(effectiveUrl)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("ভার্সন চেক ব্যর্থ হয়েছে (HTTP ${resp.code})")
                        )
                    }
                    val body = resp.body?.string().orEmpty()
                    Result.success(parseReleaseJson(body))
                }
            }
        } catch (_: UnknownHostException) {
            Result.failure(IllegalStateException("ইন্টারনেট সংযোগ নেই। নেটওয়ার্ক যাচাই করে আবার চেষ্টা করুন।"))
        } catch (_: IOException) {
            Result.failure(IllegalStateException("নেটওয়ার্ক সমস্যার কারণে বিল্ড যাচাই করা যায়নি।"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "ভার্সন তথ্য পড়া যায়নি।"))
        }
    }

    fun startApkDownload(apkUrl: String, versionLabel: String?): Result<Pair<Long, String>> =
        apkDownloader.downloadApk(apkUrl, versionLabel)

    fun observeDownloadProgress(downloadId: Long, fileName: String): Flow<DownloadProgressState> =
        apkDownloader.observeDownload(downloadId, fileName)

    fun openSystemDownloads() = apkDownloader.openSystemDownloads()

    companion object {
        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        fun parseReleaseJson(
            body: String,
            currentVersionName: String = BuildConfig.VERSION_NAME,
            currentVersionCode: Int = BuildConfig.VERSION_CODE
        ): BuildVersionInfo {
            val adapter = moshi.adapter(ReleaseManifestDto::class.java)
            val dto = adapter.fromJson(body) ?: ReleaseManifestDto()

            // Support both GitHub Releases API JSON and simple { versionName, versionCode, apkUrl } manifest
            val tagOrVersion = dto.versionName?.takeIf { it.isNotBlank() }
                ?: dto.tag_name?.removePrefix("v")?.takeIf { it.isNotBlank() }
                ?: dto.version?.takeIf { it.isNotBlank() }
                ?: ""
            val remoteCode = dto.versionCode ?: currentVersionCode
            val notes = dto.releaseNotes?.takeIf { it.isNotBlank() }
                ?: dto.body?.takeIf { it.isNotBlank() }

            var apkUrl = dto.apkUrl?.takeIf { it.isNotBlank() }
                ?: dto.downloadUrl?.takeIf { it.isNotBlank() }
                ?: ""
            var fileSize: Long? = dto.fileSizeBytes?.takeIf { it > 0L }

            if (apkUrl.isBlank()) {
                val apkAsset = dto.assets?.firstOrNull { asset ->
                    asset.name?.endsWith(".apk", ignoreCase = true) == true &&
                        !asset.browser_download_url.isNullOrBlank()
                }
                if (apkAsset != null) {
                    apkUrl = apkAsset.browser_download_url.orEmpty()
                    fileSize = apkAsset.size?.takeIf { it > 0L }
                }
            }

            val hasNewer = remoteCode > currentVersionCode ||
                (tagOrVersion.isNotBlank() && tagOrVersion != currentVersionName)

            return BuildVersionInfo(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                latestVersionName = tagOrVersion.ifBlank { currentVersionName },
                latestVersionCode = remoteCode,
                resolvedApkUrl = apkUrl.ifBlank { null },
                fileSizeBytes = fileSize,
                releaseNotes = notes,
                isUpdateAvailable = hasNewer
            )
        }

        fun formatFileSize(bytes: Long?): String? {
            if (bytes == null || bytes <= 0L) return null
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb) else "${(bytes / 1024L).coerceAtLeast(1)} KB"
        }

        /**
         * Converts human-friendly GitHub repository or releases URLs (e.g.
         * `https://github.com/owner/repo` or `https://github.com/owner/repo/releases/latest`)
         * into the GitHub Releases API endpoint, while preserving direct `.apk` download links.
         */
        fun normalizeReleaseSourceUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            val parsed = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return trimmed
            val host = parsed.host?.lowercase() ?: return trimmed
            val path = parsed.path ?: return trimmed
            if ((host == "github.com" || host == "www.github.com") &&
                !path.endsWith(".apk", ignoreCase = true)
            ) {
                val segments = path.trim('/').split('/').filter { it.isNotBlank() }
                if (segments.size >= 2) {
                    val owner = segments[0]
                    val repo = segments[1].removeSuffix(".git")
                    val sub = segments.drop(2)
                    val isRepoOrReleases = sub.isEmpty() ||
                        sub.firstOrNull() == "releases" ||
                        sub.firstOrNull() == "tree"
                    if (isRepoOrReleases) {
                        return "https://api.github.com/repos/$owner/$repo/releases/latest"
                    }
                }
            }
            return trimmed
        }
    }
}
