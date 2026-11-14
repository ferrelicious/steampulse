package com.example.model

data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val downloadCount: Int,
    val contentType: String
) {
    val isApkOrBundle: Boolean
        get() = name.endsWith(".apk", ignoreCase = true) ||
            name.endsWith(".aab", ignoreCase = true)
}

data class GitHubRelease(
    val id: Long,
    val tagName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val publishedAtIso: String,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<GitHubReleaseAsset>
)

enum class VersionComparisonStatus {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    AHEAD_OF_REMOTE
}

data class ReleaseCheckResult(
    val repoSlug: String,
    val releases: List<GitHubRelease>,
    val latestRelease: GitHubRelease?,
    val versionStatus: VersionComparisonStatus,
    val checkedAtMillis: Long
)

sealed interface ReleaseUiState {
    data object Idle : ReleaseUiState
    data object Loading : ReleaseUiState
    data class Success(val result: ReleaseCheckResult) : ReleaseUiState
    data class Error(val message: String, val isNotFound: Boolean = false) : ReleaseUiState
}
