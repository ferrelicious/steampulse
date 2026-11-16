package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamWorkshopSyncProfile
import com.example.model.WorkshopFollowSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkshopLocalStoreTest {

    private lateinit var context: Context
    private lateinit var store: WorkshopLocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("steam_pulse_workshop_local_store", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = WorkshopLocalStore(context)
    }

    @Test
    fun guestMode_togglesAndPersistsCorrectly() {
        assertFalse(store.isGuestMode())
        store.setGuestMode(true)
        assertTrue(store.isGuestMode())
        store.setGuestMode(false)
        assertFalse(store.isGuestMode())
    }

    @Test
    fun migrateGuestDataIfEmpty_copiesDataToNewUser() {
        val guestGame = FollowedWorkshopGame(
            id = "stalker2_guest",
            userId = "guest",
            appId = 1643320,
            gameName = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
            developer = "GSC Game World",
            headerImageUrl = "https://example.com/stalker2.jpg"
        )
        val guestMod = FollowedWorkshopMod(
            id = "3370000001",
            userId = "guest",
            publishedFileId = "3370000001",
            appId = 1643320,
            gameName = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
            title = "A-Life Overhaul",
            author = "ZoneModder",
            source = WorkshopFollowSource.BOOKMARK
        )
        val guestProfile = SteamWorkshopSyncProfile(
            id = WorkshopFollowRepository.SYNC_DOC_ID,
            userId = "guest",
            steamId64 = "76561198000000001",
            profileInput = "76561198000000001",
            syncedCount = 1
        )

        store.saveFollowedGames("guest", listOf(guestGame))
        store.saveFollowedMods("guest", listOf(guestMod))
        store.saveSyncProfile("guest", guestProfile)
        store.saveSteamSession("guest", "76561198000000001", "steamLoginSecure=76561198000000001%7C%7Ctoken")

        val newUserId = "user_google_12345"
        store.migrateGuestDataIfEmpty(newUserId)

        val migrated = store.loadSnapshot(newUserId)
        assertEquals(1, migrated.followedGames.size)
        assertEquals(1643320, migrated.followedGames.first().appId)
        assertEquals(1, migrated.followedMods.size)
        assertEquals("3370000001", migrated.followedMods.first().publishedFileId)
        assertNotNull(migrated.syncProfile)
        assertEquals("76561198000000001", migrated.syncProfile?.steamId64)
        assertEquals("76561198000000001", migrated.steamSessionSteamId64)
    }

    @Test
    fun migrateGuestDataIfEmpty_doesNotOverwriteExistingUserData() {
        val existingGame = FollowedWorkshopGame(
            id = "dota2_user",
            userId = "user_existing",
            appId = 570,
            gameName = "Dota 2",
            developer = "Valve",
            headerImageUrl = "https://example.com/dota2.jpg"
        )
        val guestGame = FollowedWorkshopGame(
            id = "stalker2_guest",
            userId = "guest",
            appId = 1643320,
            gameName = "S.T.A.L.K.E.R. 2",
            developer = "GSC Game World",
            headerImageUrl = "https://example.com/stalker2.jpg"
        )

        store.saveFollowedGames("user_existing", listOf(existingGame))
        store.saveFollowedGames("guest", listOf(guestGame))

        store.migrateGuestDataIfEmpty("user_existing")

        val result = store.loadSnapshot("user_existing")
        assertEquals(1, result.followedGames.size)
        assertEquals(570, result.followedGames.first().appId)
    }
}
