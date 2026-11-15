package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.FollowedWorkshopMod
import com.example.model.WorkshopFollowSource
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopModItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WorkshopFollowRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun followAndUnfollowGame_ownerSucceeds_andUpdatesRealtimeFlow() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = WorkshopFollowRepository(firestore, auth)
        val appId = 1643320

        val game = WorkshopGameSummary(
            rank = 0,
            appId = appId,
            name = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
            developer = "GSC Game World",
            publisher = "GSC Game World",
            ccu = 50000,
            ownersRange = "—",
            positiveReviews = 10000,
            negativeReviews = 1500,
            averagePlaytimeMinutes = 300,
            priceCents = 5999,
            discountPercent = 0,
            isKnownWorkshopPowerhouse = true
        )

        val followResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.followGame(game) }
        assertTrue("followGame failed: ${followResult.exceptionOrNull()}", followResult.isSuccess)

        val games = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFollowedGames(uid).first { list -> list.any { it.appId == appId } }
        }
        val saved = games.first { it.appId == appId }
        assertEquals("S.T.A.L.K.E.R. 2: Heart of Chornobyl", saved.gameName)
        assertEquals("GSC Game World", saved.developer)

        val unfollowResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.unfollowGame(appId) }
        assertTrue(unfollowResult.isSuccess)

        val afterUnfollow = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFollowedGames(uid).first { list -> list.none { it.appId == appId } }
        }
        assertTrue(afterUnfollow.none { it.appId == appId })
    }

    @Test
    fun bookmarkAndSyncMods_mergesSourcesCleanly_andPersistsSyncProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = WorkshopFollowRepository(firestore, auth)
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(6)
        val sharedModId = "9900$uniqueSuffix"
        val syncOnlyModId = "8800$uniqueSuffix"

        val mod = WorkshopModItem(
            publishedFileId = sharedModId,
            appId = 294100,
            title = "Combat Extended",
            author = "CE Team",
            previewUrl = "https://steamuserimages-a.akamaihd.net/ugc/ce.jpg",
            itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=$sharedModId",
            subscriptions = 420_000L,
            updatedEpochSec = 1_730_000_000L
        )

        // 1. Bookmark mod -> BOOKMARK
        val bookmarkedRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.toggleBookmarkMod(mod, "RimWorld")
        }
        assertTrue(bookmarkedRes.isSuccess)
        assertEquals(true, bookmarkedRes.getOrNull())

        // 2. Steam sync includes same mod + new mod -> upgrades existing to BOTH, inserts new as STEAM_SYNC
        val syncResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.syncSubscribedMods(
                steamId64 = "76561198000000001",
                profileInput = "https://steamcommunity.com/id/stalkerfan",
                syncedMods = listOf(
                    FollowedWorkshopMod(
                        publishedFileId = sharedModId,
                        appId = 294100,
                        gameName = "RimWorld",
                        title = "Combat Extended",
                        author = "CE Team",
                        previewUrl = mod.previewUrl,
                        itemUrl = mod.itemUrl,
                        source = WorkshopFollowSource.STEAM_SYNC,
                        updatedEpochSec = 1_730_005_000L,
                        subscriptions = 425_000L
                    ),
                    FollowedWorkshopMod(
                        publishedFileId = syncOnlyModId,
                        appId = 1643320,
                        gameName = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
                        title = "Mutant Loot Tweaks",
                        author = "ZoneModder",
                        previewUrl = "",
                        itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=$syncOnlyModId",
                        source = WorkshopFollowSource.STEAM_SYNC,
                        updatedEpochSec = 1_730_100_000L,
                        subscriptions = 12_000L
                    )
                )
            )
        }
        assertTrue("syncSubscribedMods failed: ${syncResult.exceptionOrNull()}", syncResult.isSuccess)
        assertEquals(2, syncResult.getOrNull())

        val modsAfterSync = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFollowedMods(uid).first { list ->
                list.any { it.publishedFileId == sharedModId && it.source == WorkshopFollowSource.BOTH } &&
                    list.any { it.publishedFileId == syncOnlyModId && it.source == WorkshopFollowSource.STEAM_SYNC }
            }
        }
        assertTrue(modsAfterSync.any { it.publishedFileId == sharedModId && it.source == WorkshopFollowSource.BOTH })

        val syncProfile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSyncProfile(uid).first { it != null && it.steamId64 == "76561198000000001" }
        }
        assertNotNull(syncProfile)
        assertEquals(2, syncProfile?.syncedCount)

        // 3. Unbookmarking a BOTH mod downgrades to STEAM_SYNC rather than deleting
        val unbookmarkRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.toggleBookmarkMod(mod, "RimWorld")
        }
        assertTrue(unbookmarkRes.isSuccess)
        assertEquals(false, unbookmarkRes.getOrNull())

        val downgraded = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFollowedMods(uid).first { list ->
                list.any { it.publishedFileId == sharedModId && it.source == WorkshopFollowSource.STEAM_SYNC }
            }
        }
        assertTrue(downgraded.any { it.publishedFileId == sharedModId })

        // Cleanup
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repo.removeFollowedMod(sharedModId) }.isSuccess)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repo.removeFollowedMod(syncOnlyModId) }.isSuccess)
    }

    @Test
    fun observeFollowedData_crossUserAndUnauthenticated_failWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WorkshopFollowRepository(firestore, auth)
        val aliceModId = "77112233"

        val bookmarkRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.toggleBookmarkMod(
                WorkshopModItem(
                    publishedFileId = aliceModId,
                    appId = 4000,
                    title = "Wiremod",
                    author = "WireTeam",
                    previewUrl = "",
                    itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=$aliceModId"
                ),
                "Garry's Mod"
            )
        }
        assertTrue(bookmarkRes.isSuccess)

        // Bob cannot read or write Alice's followed mods/games
        signInTestUser(BOB_EMAIL)
        val bobRepo = WorkshopFollowRepository(firestore, auth)

        var crossReadError: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeFollowedMods(aliceUid).first()
            }
        } catch (t: Throwable) {
            crossReadError = t
        }
        val crossFirestoreEx = crossReadError?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED, got $crossReadError", crossFirestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossFirestoreEx?.code)

        var crossGamesError: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeFollowedGames(aliceUid).first()
            }
        } catch (t: Throwable) {
            crossGamesError = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossGamesError?.unwrapFirestoreException()?.code
        )

        // Unauthenticated user denied
        auth.signOut()
        var unauthError: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                WorkshopFollowRepository(firestore, auth).observeSyncProfile(aliceUid).first()
            }
        } catch (t: Throwable) {
            unauthError = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unauthError?.unwrapFirestoreException()?.code
        )
    }

    private companion object {
        const val ALICE_EMAIL = "alice_workshop@test.com"
        const val BOB_EMAIL = "bob_workshop@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
