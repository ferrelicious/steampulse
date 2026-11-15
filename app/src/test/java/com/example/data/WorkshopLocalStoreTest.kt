package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamWorkshopSyncProfile
import com.example.model.WorkshopFollowSource
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopModItem
import com.example.viewmodel.WorkshopViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkshopLocalStoreTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: WorkshopLocalStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("workshop_local_store_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = WorkshopLocalStore(prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveAndLoadSnapshot_persistsGamesModsAndSyncProfilePerUser() {
        val now = Timestamp(1_735_000_000L, 0)
        val games = listOf(
            FollowedWorkshopGame(
                id = "1643320",
                userId = "user_a",
                appId = 1643320,
                gameName = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
                developer = "GSC Game World",
                headerImageUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1643320/header.jpg",
                createdAt = now,
                updatedAt = now
            )
        )
        val mods = listOf(
            FollowedWorkshopMod(
                id = "33991122",
                userId = "user_a",
                publishedFileId = "33991122",
                appId = 1643320,
                gameName = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
                title = "Zone Realism Overhaul",
                author = "ZoneModder",
                previewUrl = "https://steamuserimages-a.akamaihd.net/ugc/sub_thumb.jpg",
                itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=33991122",
                source = WorkshopFollowSource.STEAM_SYNC,
                updatedEpochSec = 1_735_000_123L,
                subscriptions = 4200L,
                createdAt = now,
                updatedAt = now
            )
        )
        val profile = SteamWorkshopSyncProfile(
            id = WorkshopFollowRepository.SYNC_DOC_ID,
            userId = "user_a",
            steamId64 = "76561198012345678",
            profileInput = "https://steamcommunity.com/profiles/76561198012345678",
            syncedCount = 1,
            createdAt = now,
            updatedAt = now
        )

        store.saveFollowedGames("user_a", games)
        store.saveFollowedMods("user_a", mods)
        store.saveSyncProfile("user_a", profile)

        val loadedA = store.loadSnapshot("user_a")
        assertEquals(1, loadedA.followedGames.size)
        assertEquals(1643320, loadedA.followedGames[0].appId)
        assertEquals("S.T.A.L.K.E.R. 2: Heart of Chornobyl", loadedA.followedGames[0].gameName)

        assertEquals(1, loadedA.followedMods.size)
        assertEquals("33991122", loadedA.followedMods[0].publishedFileId)
        assertEquals(WorkshopFollowSource.STEAM_SYNC, loadedA.followedMods[0].source)
        assertEquals(4200L, loadedA.followedMods[0].subscriptions)

        assertNotNull(loadedA.syncProfile)
        assertEquals("76561198012345678", loadedA.syncProfile?.steamId64)
        assertEquals(1, loadedA.syncProfile?.syncedCount)

        // Different user stays isolated
        val loadedB = store.loadSnapshot("user_b")
        assertTrue(loadedB.followedGames.isEmpty())
        assertTrue(loadedB.followedMods.isEmpty())
        assertNull(loadedB.syncProfile)
    }

    @Test
    fun workshopViewModel_hydratesFromLocalStore_andFollowsOrBookmarksOffline() = runTest {
        val initialProfile = SteamWorkshopSyncProfile(
            id = WorkshopFollowRepository.SYNC_DOC_ID,
            userId = "uid_offline",
            steamId64 = "76561198012345678",
            profileInput = "76561198012345678",
            syncedCount = 1
        )
        val initialSyncedMod = FollowedWorkshopMod(
            id = "556677",
            userId = "uid_offline",
            publishedFileId = "556677",
            appId = 294100,
            gameName = "RimWorld",
            title = "Harmony",
            author = "Brrainz",
            source = WorkshopFollowSource.STEAM_SYNC,
            updatedEpochSec = 1_735_000_000L
        )
        store.saveSyncProfile("uid_offline", initialProfile)
        store.saveFollowedMods("uid_offline", listOf(initialSyncedMod))

        val viewModel = WorkshopViewModel(
            repository = SteamWorkshopRepository(),
            followRepository = null,
            userId = "uid_offline",
            localStore = store
        )

        val hydrated = viewModel.uiState.value
        assertEquals("76561198012345678", hydrated.steamProfileInput)
        assertEquals(1, hydrated.followedMods.size)
        assertEquals("556677", hydrated.followedMods[0].publishedFileId)

        // Follow a game offline -> updates UI state and persists immediately
        val game = WorkshopGameSummary(
            rank = 0,
            appId = 1643320,
            name = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
            developer = "GSC Game World",
            publisher = "GSC Game World",
            ccu = 0,
            ownersRange = "—",
            positiveReviews = 0,
            negativeReviews = 0,
            averagePlaytimeMinutes = 0,
            priceCents = 0,
            discountPercent = 0,
            isKnownWorkshopPowerhouse = true
        )
        viewModel.toggleFollowGame(game)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.followedGameIds.contains(1643320))
        assertEquals(1, store.loadSnapshot("uid_offline").followedGames.size)

        // Bookmark the synced mod -> upgrades to BOTH in UI and local store
        val modItem = WorkshopModItem(
            publishedFileId = "556677",
            appId = 294100,
            title = "Harmony",
            author = "Brrainz",
            previewUrl = "",
            itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=556677"
        )
        viewModel.toggleBookmarkMod(modItem, "RimWorld")
        assertTrue(viewModel.uiState.value.bookmarkedModIds.contains("556677"))
        assertEquals(
            WorkshopFollowSource.BOTH,
            store.loadSnapshot("uid_offline").followedMods.first().source
        )

        // Remove mod -> clears UI and local store
        viewModel.removeFollowedMod("556677")
        assertFalse(viewModel.uiState.value.followedMods.any { it.publishedFileId == "556677" })
        assertTrue(store.loadSnapshot("uid_offline").followedMods.isEmpty())
    }
}
