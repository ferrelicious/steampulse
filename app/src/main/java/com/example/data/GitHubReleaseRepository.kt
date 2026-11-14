package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.GitHubRelease
import com.example.model.GitHubReleaseAsset
import com.example.model.ReleaseCheckResult
import com.example.model.VersionComparisonStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.releasePrefsDataStore by preferencesDataStore(name = "steampulse_release_prefs")

data class ReleasePreferences(
    val repoSlug: String = "",
    val includePrereleases: Boolean = false
)

class GitHubReleaseException(
    message: String,
    val isNotFound: Boolean = false
) : Exception(message)

class GitHubReleaseRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private object Keys {
        val REPO_SLUG = stringPreferencesKey("github_repo_slug")
        val INCLUDE_PRERELEASES = booleanPreferencesKey("include_prereleases")
    }

    val preferencesFlow: Flow<ReleasePreferences> = context.releasePrefsDataStore.data.map { prefs ->
        ReleasePreferences(
            repoSlug = prefs[Keys.REPO_SLUG].orEmpty(),
            includePrereleases = prefs[Keys.INCLUDE_PRERELEASES] ?: false
        )
    }

    suspend fun savePreferences(repoSlug: String, includePrereleases: Boolean) {
        context.releasePrefsDataStore.edit { prefs ->
            prefs[Keys.REPO_SLUG] = repoSlug
            prefs[Keys.INCLUDE_PRERELEASES] = includePrereleases
        }
    }

    suspend fun fetchReleases(
        rawRepoInput: String,
        currentVersionName: String,
        includePrereleases: Boolean
    ): ReleaseCheckResult = withContext(Dispatchers.IO) {
        val normalizedSlug = normalizeRepoSlug(rawRepoInput)
            ?: throw GitHubReleaseException(
                "Ogiltigt format. Ange som ägare/repo eller klistra in din GitHub-URL."
            )

        val apiUrl = "https://api.github.com/repos/$normalizedSlug/releases?per_page=15"
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SteamPulse-Android/$currentVersionName")
            .get()
            .build()

        val bodyText = try {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> throw GitHubReleaseException(
                        "Hittade inga publika releaser för $normalizedSlug. Kontrollera att repot är publikt och har minst en GitHub Release.",
                        isNotFound = true
                    )
                    response.code == 403 || response.code == 429 -> throw GitHubReleaseException(
                        "GitHub API:s förfrågningsgräns nåddes tillfälligt. Försök igen om en liten stund."
                    )
                    !response.isSuccessful -> throw GitHubReleaseException(
                        "GitHub svarade med HTTP ${response.code}. Kontrollera repositorynamnet och försök igen."
                    )
                    else -> rawBody
                }
            }
        } catch (e: GitHubReleaseException) {
            throw e
        } catch (e: IOException) {
            throw GitHubReleaseException(
                "Kunde inte ansluta till GitHub (${e.localizedMessage ?: "nätverksfel"})."
            )
        }

        val allReleases = parseReleasesJson(bodyText)
            .filter { !it.isDraft }
            .filter { includePrereleases || !it.isPrerelease }

        val latest = allReleases.firstOrNull()
        val versionStatus = if (latest == null) {
            VersionComparisonStatus.UP_TO_DATE
        } else {
            val cmp = compareSemanticVersions(latest.tagName, currentVersionName)
            when {
                cmp > 0 -> VersionComparisonStatus.UPDATE_AVAILABLE
                cmp < 0 -> VersionComparisonStatus.AHEAD_OF_REMOTE
                else -> VersionComparisonStatus.UP_TO_DATE
            }
        }

        ReleaseCheckResult(
            repoSlug = normalizedSlug,
            releases = allReleases,
            latestRelease = latest,
            versionStatus = versionStatus,
            checkedAtMillis = System.currentTimeMillis()
        )
    }

    private fun parseReleasesJson(jsonString: String): List<GitHubRelease> {
        if (jsonString.isBlank()) return emptyList()
        val array = JSONArray(jsonString)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val assetsArray = obj.optJSONArray("assets") ?: JSONArray()
                val assets = buildList {
                    for (j in 0 until assetsArray.length()) {
                        val assetObj = assetsArray.getJSONObject(j)
                        add(
                            GitHubReleaseAsset(
                                name = assetObj.optString("name", "asset"),
                                downloadUrl = assetObj.optString("browser_download_url", ""),
                                sizeBytes = assetObj.optLong("size", 0L),
                                downloadCount = assetObj.optInt("download_count", 0),
                                contentType = assetObj.optString("content_type", "")
                            )
                        )
                    }
                }
                val tag = obj.optString("tag_name", "")
                add(
                    GitHubRelease(
                        id = obj.optLong("id", i.toLong()),
                        tagName = tag,
                        title = obj.optString("name", "").ifBlank { tag },
                        body = obj.optString("body", "").trim(),
                        htmlUrl = obj.optString("html_url", ""),
                        publishedAtIso = obj.optString("published_at", obj.optString("created_at", "")),
                        isPrerelease = obj.optBoolean("prerelease", false),
                        isDraft = obj.optBoolean("draft", false),
                        assets = assets
                    )
                )
            }
        }
    }

    companion object {
        private val REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

        fun normalizeRepoSlug(rawInput: String): String? {
            val trimmed = rawInput.trim()
                .removeSuffix("/")
                .removeSuffix(".git")
            if (trimmed.isEmpty()) return null

            val candidate = when {
                trimmed.contains("github.com/", ignoreCase = true) -> {
                    val afterHost = trimmed.substringAfter("github.com/", "")
                    val segments = afterHost.split('/').filter { it.isNotBlank() }
                    if (segments.size >= 2) "${segments[0]}/${segments[1]}" else ""
                }
                else -> trimmed
            }

            return candidate.takeIf { REPO_REGEX.matches(it) }
        }

        /**
         * Returns > 0 if [remoteTag] is newer than [localVersion],
         * 0 if equal, and < 0 if [remoteTag] is older.
         */
        fun compareSemanticVersions(remoteTag: String, localVersion: String): Int {
            val remoteParsed = parseSemver(remoteTag)
            val localParsed = parseSemver(localVersion)

            val maxLen = maxOf(remoteParsed.numbers.size, localParsed.numbers.size, 3)
            for (i in 0 until maxLen) {
                val r = remoteParsed.numbers.getOrElse(i) { 0 }
                val l = localParsed.numbers.getOrElse(i) { 0 }
                if (r != l) return r.compareTo(l)
            }

            // Pre-release versions have lower precedence than a normal version (1.1.0-beta < 1.1.0)
            val rPre = remoteParsed.preRelease
            val lPre = localParsed.preRelease
            if (rPre == null && lPre == null) return 0
            if (rPre == null) return 1
            if (lPre == null) return -1
            return rPre.compareTo(lPre, ignoreCase = true)
        }

        private data class ParsedSemver(
            val numbers: List<Int>,
            val preRelease: String?
        )

        private fun parseSemver(raw: String): ParsedSemver {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            val mainPart = cleaned.substringBefore('-').substringBefore('+')
            val prePart = cleaned.substringAfter('-', "").substringBefore('+').ifBlank { null }
            val numbers = mainPart.split('.')
                .mapNotNull { seg -> seg.filter { it.isDigit() }.toIntOrNull() }
            return ParsedSemver(numbers = numbers, preRelease = prePart)
        }
    }
}
