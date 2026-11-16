package com.example.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.GitHubReleaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppUpdateRelease(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkAssetName: String,
    val apkSizeBytes: Long = 0L
)

sealed interface AppUpdateCheckOutcome {
    data class Available(val release: AppUpdateRelease) : AppUpdateCheckOutcome
    data class Dismissed(val release: AppUpdateRelease) : AppUpdateCheckOutcome
    data object UpToDate : AppUpdateCheckOutcome
    data object NoApkAsset : AppUpdateCheckOutcome
    data class Error(val message: String) : AppUpdateCheckOutcome
}

sealed interface DownloadQueryStatus {
    data class Successful(val apkFile: File) : DownloadQueryStatus
    data class Failed(val reason: Int) : DownloadQueryStatus
    data object Pending : DownloadQueryStatus
}

class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
    private val currentVersionName: String = BuildConfig.VERSION_NAME
) {
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isTagDismissed(tagName: String): Boolean {
        val dismissed = prefs.getString(KEY_DISMISSED_TAG, null) ?: return false
        return dismissed.equals(tagName.trim(), ignoreCase = true)
    }

    fun dismissTag(tagName: String) {
        prefs.edit().putString(KEY_DISMISSED_TAG, tagName.trim()).apply()
    }

    fun clearDismissedTag() {
        prefs.edit().remove(KEY_DISMISSED_TAG).apply()
    }

    suspend fun checkForUpdate(ignoreDismissed: Boolean = false): AppUpdateCheckOutcome =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(latestReleaseUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "SteamPulse-Android/$currentVersionName")
                .get()
                .build()

            val bodyText = try {
                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    when {
                        response.code == 404 -> return@withContext AppUpdateCheckOutcome.UpToDate
                        response.code == 403 || response.code == 429 -> {
                            return@withContext AppUpdateCheckOutcome.Error(
                                "GitHub API:s förfrågningsgräns nåddes tillfälligt. Försök igen senare."
                            )
                        }
                        !response.isSuccessful -> {
                            return@withContext AppUpdateCheckOutcome.Error(
                                "Kunde inte söka efter uppdatering (HTTP ${response.code})."
                            )
                        }
                        else -> raw
                    }
                }
            } catch (e: IOException) {
                return@withContext AppUpdateCheckOutcome.Error(
                    "Kunde inte ansluta till GitHub (${e.localizedMessage ?: "nätverksfel"})."
                )
            } catch (e: Exception) {
                return@withContext AppUpdateCheckOutcome.Error(
                    e.localizedMessage ?: "Ett oväntat fel uppstod vid uppdateringskontroll."
                )
            }

            val parsed = parseLatestReleaseJson(bodyText)
                ?: return@withContext AppUpdateCheckOutcome.NoApkAsset

            if (!isRemoteVersionNewer(parsed.tagName, currentVersionName)) {
                return@withContext AppUpdateCheckOutcome.UpToDate
            }

            if (!ignoreDismissed && isTagDismissed(parsed.tagName)) {
                return@withContext AppUpdateCheckOutcome.Dismissed(parsed)
            }

            AppUpdateCheckOutcome.Available(parsed)
        }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        return Intent(action, packageUri).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun openUnknownSourcesSettings(): Boolean {
        return try {
            context.startActivity(buildUnknownSourcesSettingsIntent())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildApkDestinationFile(release: AppUpdateRelease): File {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val safeTag = release.tagName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(downloadsDir, "SteamPulse-$safeTag.apk")
    }

    fun enqueueApkDownload(release: AppUpdateRelease): Pair<Long, File> {
        val destinationFile = buildApkDestinationFile(release)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkDownloadUrl)).apply {
            setTitle("SteamPulse ${release.tagName}")
            setDescription("Laddar ner appuppdatering...")
            setMimeType(APK_MIME_TYPE)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                destinationFile.name
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val downloadId = downloadManager.enqueue(request)
        return downloadId to destinationFile
    }

    fun queryDownloadStatus(downloadId: Long, fallbackFile: File): DownloadQueryStatus {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return DownloadQueryStatus.Failed(-1)
            }
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                    val resolvedFile = localUri
                        ?.takeIf { it.startsWith("file://") }
                        ?.let { Uri.parse(it).path }
                        ?.let { File(it) }
                        ?.takeIf { it.exists() }
                        ?: fallbackFile
                    DownloadQueryStatus.Successful(resolvedFile)
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                    DownloadQueryStatus.Failed(reason)
                }
                else -> DownloadQueryStatus.Pending
            }
        }
        return DownloadQueryStatus.Pending
    }

    fun buildInstallApkIntent(apkFile: File): Intent {
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            context.startActivity(buildInstallApkIntent(apkFile))
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/ferrelicious/steampulse/releases/latest"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val PREFS_NAME = "steampulse_app_updater"
        private const val KEY_DISMISSED_TAG = "dismissed_tag"

        fun isRemoteVersionNewer(remoteTagName: String, currentVersionName: String): Boolean {
            val cleanedTag = remoteTagName.trim().removePrefix("v").removePrefix("V")
            val cleanedLocal = currentVersionName.trim().removePrefix("v").removePrefix("V")
            if (cleanedTag.isBlank()) return false
            return GitHubReleaseRepository.compareSemanticVersions(cleanedTag, cleanedLocal) > 0
        }

        fun parseLatestReleaseJson(jsonString: String): AppUpdateRelease? {
            if (jsonString.isBlank()) return null
            val obj = try {
                JSONObject(jsonString)
            } catch (_: Exception) {
                return null
            }

            if (obj.optBoolean("draft", false)) return null

            val tagName = obj.optString("tag_name", "").trim()
            if (tagName.isBlank()) return null

            val versionName = tagName.removePrefix("v").removePrefix("V")
            val body = obj.optString("body", "").trim()

            val assets: JSONArray = obj.optJSONArray("assets") ?: JSONArray()
            var apkDownloadUrl = ""
            var apkAssetName = ""
            var apkSizeBytes = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "").trim()
                val downloadUrl = asset.optString("browser_download_url", "").trim()
                if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    apkDownloadUrl = downloadUrl
                    apkAssetName = name
                    apkSizeBytes = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkDownloadUrl.isBlank()) return null

            return AppUpdateRelease(
                tagName = tagName,
                versionName = versionName,
                releaseNotes = body,
                apkDownloadUrl = apkDownloadUrl,
                apkAssetName = apkAssetName,
                apkSizeBytes = apkSizeBytes
            )
        }
    }
}
