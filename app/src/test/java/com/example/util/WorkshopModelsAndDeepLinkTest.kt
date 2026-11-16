package com.example.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.example.model.FollowedWorkshopMod
import com.example.model.WorkshopModItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkshopModelsAndDeepLinkTest {

    @Test
    fun isNewMod_evaluates48HourWindowAndPreviousVisitCorrectly() {
        val now = 1_765_000_000L
        val recentWithin48h = WorkshopModItem(
            publishedFileId = "1",
            appId = 1643320,
            title = "Recent Mod",
            author = "Author",
            previewUrl = "",
            itemUrl = "",
            createdEpochSec = now - (12L * 3600L)
        )
        val olderThan48hAfterLastVisit = WorkshopModItem(
            publishedFileId = "2",
            appId = 1643320,
            title = "Post-Visit Mod",
            author = "Author",
            previewUrl = "",
            itemUrl = "",
            createdEpochSec = now - (72L * 3600L)
        )
        val olderThan48hBeforeLastVisit = WorkshopModItem(
            publishedFileId = "3",
            appId = 1643320,
            title = "Old Mod",
            author = "Author",
            previewUrl = "",
            itemUrl = "",
            createdEpochSec = now - (120L * 3600L)
        )
        val missingTimestamp = WorkshopModItem(
            publishedFileId = "4",
            appId = 1643320,
            title = "No Timestamp",
            author = "Author",
            previewUrl = "",
            itemUrl = "",
            createdEpochSec = null
        )

        val previousVisit = now - (96L * 3600L)

        // First-ever visit (previousVisit = 0L): only mods within 48h are NY
        assertTrue(recentWithin48h.isNewMod(previousVisitEpochSec = 0L, nowEpochSec = now))
        assertFalse(olderThan48hAfterLastVisit.isNewMod(previousVisitEpochSec = 0L, nowEpochSec = now))
        assertFalse(olderThan48hBeforeLastVisit.isNewMod(previousVisitEpochSec = 0L, nowEpochSec = now))

        // Subsequent visit: within 48h OR created after previous visit
        assertTrue(recentWithin48h.isNewMod(previousVisitEpochSec = previousVisit, nowEpochSec = now))
        assertTrue(olderThan48hAfterLastVisit.isNewMod(previousVisitEpochSec = previousVisit, nowEpochSec = now))
        assertFalse(olderThan48hBeforeLastVisit.isNewMod(previousVisitEpochSec = previousVisit, nowEpochSec = now))
        assertFalse(missingTimestamp.isNewMod(previousVisitEpochSec = previousVisit, nowEpochSec = now))
    }

    @Test
    fun followedWorkshopMod_toModItemPreservesCreatedEpochSec() {
        val followed = FollowedWorkshopMod(
            publishedFileId = "555666",
            appId = 294100,
            title = "RimHud",
            createdEpochSec = 1_764_000_000L,
            updatedEpochSec = 1_764_100_000L
        )
        val modItem = followed.toModItem()
        assertEquals(1_764_000_000L, modItem.createdEpochSec)
        assertEquals(1_764_100_000L, modItem.updatedEpochSec)
    }

    @Test
    fun isUpdatedMod_evaluatesSeenTimestampAnd48HourWindowCorrectly() {
        val now = 1_765_000_000L
        val recentlyUpdatedMod = WorkshopModItem(
            publishedFileId = "101",
            appId = 1643320,
            title = "Recent Update Mod",
            author = "Author",
            previewUrl = "",
            itemUrl = "",
            updatedEpochSec = now - (6L * 3600L)
        )
        val olderUpdatedMod = WorkshopModItem(
            publishedFileId = "102",
            appId = 1643320,
            title = "Older Update Mod",
            author = "Author",
            previewUrl = "",
            itemUrl = "",
            updatedEpochSec = now - (72L * 3600L)
        )
        val noUpdatedTimestamp = WorkshopModItem(
            publishedFileId = "103",
            appId = 1643320,
            title = "Missing Update Timestamp",
            author = "Author",
            previewUrl = "",
            itemUrl = "",
            updatedEpochSec = null
        )

        // Never seen yet (0L): only mods updated within last 48 hours count as updated
        assertTrue(recentlyUpdatedMod.isUpdatedMod(lastSeenUpdatedEpochSec = 0L, nowEpochSec = now))
        assertFalse(olderUpdatedMod.isUpdatedMod(lastSeenUpdatedEpochSec = 0L, nowEpochSec = now))
        assertFalse(noUpdatedTimestamp.isUpdatedMod(lastSeenUpdatedEpochSec = 0L, nowEpochSec = now))

        // After marking as seen at its current updatedEpochSec -> false
        assertFalse(
            recentlyUpdatedMod.isUpdatedMod(
                lastSeenUpdatedEpochSec = recentlyUpdatedMod.updatedEpochSec!!,
                nowEpochSec = now
            )
        )

        // Even if updated > 48h ago, if remote updatedEpochSec > lastSeenUpdatedEpochSec (> 0L) -> true
        assertTrue(
            olderUpdatedMod.isUpdatedMod(
                lastSeenUpdatedEpochSec = olderUpdatedMod.updatedEpochSec!! - 3600L,
                nowEpochSec = now
            )
        )
    }

    @Test
    fun steamDeepLinkHelper_buildsSteamUriAndFallsBackToBrowserOnActivityNotFound() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(
            "steam://url/CommunityFilePage/33991122",
            SteamDeepLinkHelper.buildSteamAppUri(" 33991122 ").toString()
        )
        assertEquals(
            "https://steamcommunity.com/sharedfiles/filedetails/?id=33991122",
            SteamDeepLinkHelper.buildWebFallbackUri("33991122", "").toString()
        )

        // When Steam package is NOT installed
        assertFalse(SteamDeepLinkHelper.isSteamAppInstalled(app.packageManager))
        val intentWithoutSteam = SteamDeepLinkHelper.buildSteamDeepLinkIntent(app, "33991122")
        assertNull(intentWithoutSteam.`package`)

        // When Steam package IS installed
        val pkgInfo = PackageInfo().apply {
            packageName = SteamDeepLinkHelper.STEAM_PACKAGE_NAME
        }
        shadowOf(app.packageManager).installPackage(pkgInfo)
        assertTrue(SteamDeepLinkHelper.isSteamAppInstalled(app.packageManager))
        val intentWithSteam = SteamDeepLinkHelper.buildSteamDeepLinkIntent(app, "33991122")
        assertEquals(SteamDeepLinkHelper.STEAM_PACKAGE_NAME, intentWithSteam.`package`)

        // Verify fallback to browser URL when Steam deep link throws ActivityNotFoundException
        val startedIntents = mutableListOf<Intent>()
        val recordingContext = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                startedIntents.add(intent)
                if (intent.data?.scheme == "steam") {
                    throw ActivityNotFoundException("Steam app cannot handle intent")
                }
            }
        }

        val opened = SteamDeepLinkHelper.openWorkshopModInSteam(
            context = recordingContext,
            publishedFileId = "33991122",
            itemUrl = ""
        )
        assertTrue(opened)
        assertEquals(2, startedIntents.size)
        assertEquals("steam://url/CommunityFilePage/33991122", startedIntents[0].dataString)
        assertEquals(
            "https://steamcommunity.com/sharedfiles/filedetails/?id=33991122",
            startedIntents[1].dataString
        )
    }

    @Test
    fun steamDeepLinkHelper_buildsStoreUriAndFallsBackToBrowserOnActivityNotFound() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(
            "steam://store/730",
            SteamDeepLinkHelper.buildSteamStoreAppUri(730).toString()
        )
        assertEquals(
            "https://store.steampowered.com/app/730/",
            SteamDeepLinkHelper.buildStoreWebFallbackUri(730).toString()
        )

        assertFalse(SteamDeepLinkHelper.openStoreGameInSteam(app, 0))
        assertFalse(SteamDeepLinkHelper.openStoreGameInSteam(app, -5))

        val pkgInfo = PackageInfo().apply {
            packageName = SteamDeepLinkHelper.STEAM_PACKAGE_NAME
        }
        shadowOf(app.packageManager).installPackage(pkgInfo)
        val storeIntent = SteamDeepLinkHelper.buildSteamStoreDeepLinkIntent(app, 730)
        assertEquals(SteamDeepLinkHelper.STEAM_PACKAGE_NAME, storeIntent.`package`)

        val startedIntents = mutableListOf<Intent>()
        val recordingContext = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                startedIntents.add(intent)
                if (intent.data?.scheme == "steam") {
                    throw ActivityNotFoundException("Steam app cannot handle store intent")
                }
            }
        }

        val opened = SteamDeepLinkHelper.openStoreGameInSteam(
            context = recordingContext,
            appId = 730
        )
        assertTrue(opened)
        assertEquals(2, startedIntents.size)
        assertEquals("steam://store/730", startedIntents[0].dataString)
        assertEquals("https://store.steampowered.com/app/730/", startedIntents[1].dataString)
    }

    @Test
    fun steamDeepLinkHelper_preferSteamAppFalse_opensBrowserDirectly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val startedIntents = mutableListOf<Intent>()
        val recordingContext = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                startedIntents.add(intent)
            }
        }

        val opened = SteamDeepLinkHelper.openWorkshopModInSteam(
            context = recordingContext,
            publishedFileId = "33991122",
            itemUrl = "",
            preferSteamApp = false
        )
        assertTrue(opened)
        assertEquals(1, startedIntents.size)
        assertEquals(
            "https://steamcommunity.com/sharedfiles/filedetails/?id=33991122",
            startedIntents[0].dataString
        )
    }
}
