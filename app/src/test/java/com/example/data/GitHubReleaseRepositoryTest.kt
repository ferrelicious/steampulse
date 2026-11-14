package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseRepositoryTest {

    @Test
    fun normalizeRepoSlug_acceptsOwnerRepoAndUrls() {
        assertEquals(
            "octocat/steampulse-android",
            GitHubReleaseRepository.normalizeRepoSlug("octocat/steampulse-android")
        )
        assertEquals(
            "octocat/steampulse-android",
            GitHubReleaseRepository.normalizeRepoSlug("https://github.com/octocat/steampulse-android")
        )
        assertEquals(
            "octocat/steampulse-android",
            GitHubReleaseRepository.normalizeRepoSlug("https://github.com/octocat/steampulse-android/releases/latest")
        )
        assertEquals(
            "octocat/steampulse-android",
            GitHubReleaseRepository.normalizeRepoSlug("https://github.com/octocat/steampulse-android.git/")
        )
        assertNull(GitHubReleaseRepository.normalizeRepoSlug("invalid-repo-name"))
        assertNull(GitHubReleaseRepository.normalizeRepoSlug("   "))
    }

    @Test
    fun compareSemanticVersions_handlesPrefixesAndPrereleases() {
        assertTrue(GitHubReleaseRepository.compareSemanticVersions("v1.1.0", "1.0.0") > 0)
        assertTrue(GitHubReleaseRepository.compareSemanticVersions("v2.0.0", "1.9.9") > 0)
        assertEquals(0, GitHubReleaseRepository.compareSemanticVersions("v1.0.0", "1.0.0"))
        assertEquals(0, GitHubReleaseRepository.compareSemanticVersions("1.0", "1.0.0"))
        assertTrue(GitHubReleaseRepository.compareSemanticVersions("v0.9.5", "1.0.0") < 0)
        assertTrue(GitHubReleaseRepository.compareSemanticVersions("v1.1.0-beta.1", "1.1.0") < 0)
        assertTrue(GitHubReleaseRepository.compareSemanticVersions("v1.1.0", "1.1.0-beta.1") > 0)
    }
}
