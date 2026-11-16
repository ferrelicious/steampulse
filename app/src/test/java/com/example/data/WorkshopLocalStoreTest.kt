package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamWorkshopSyncProfile
import com.example.model.WatchlistItem
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

    @Test
    fun visitTimestampsAndCreatedEpochSec_persistAndMigrateCleanly() {
        assertEquals(0L, store.getWorkshopLastVisitEpochSec("u1"))
        store.saveWorkshopLastVisitEpochSec("u1", 1_760_000_000L)
        assertEquals(1_760_000_000L, store.getWorkshopLastVisitEpochSec("u1"))

        store.saveGameLastVisitEpochSec("u1", 1643320, 1_760_100_000L)
        assertEquals(1_760_100_000L, store.getGameLastVisitEpochSec("u1", 1643320))

        val visitMap = store.loadGameLastVisitMap("u1", listOf(1643320, 294100))
        assertEquals(1_760_100_000L, visitMap[1643320])
        assertEquals(1_760_000_000L, visitMap[294100])

        val savedMod = FollowedWorkshopMod(
            id = "33880011",
            userId = "u1",
            publishedFileId = "33880011",
            appId = 1643320,
            title = "Artifact Detector Glow",
            createdEpochSec = 1_760_200_000L,
            updatedEpochSec = 1_760_210_000L,
            source = WorkshopFollowSource.BOOKMARK
        )
        store.saveFollowedMods("u1", listOf(savedMod))
        val loadedMod = store.loadSnapshot("u1").followedMods.first()
        assertEquals(1_760_200_000L, loadedMod.createdEpochSec)
        assertEquals(1_760_200_000L, loadedMod.toModItem().createdEpochSec)

        store.saveWorkshopLastVisitEpochSec("guest", 1_760_500_000L)
        store.migrateGuestDataIfEmpty("u2")
        assertEquals(1_760_500_000L, store.getWorkshopLastVisitEpochSec("u2"))
    }

    @Test
    fun notificationPreferencesAndLastNotifiedState_persistAndMigrate() {
        assertEquals("guest", store.getActiveUserId())
        store.setActiveUserId("user_999")
        assertEquals("user_999", store.getActiveUserId())

        // Enabled by default
        assertTrue(store.isNewModNotificationsEnabled("user_999"))
        store.setNewModNotificationsEnabled("user_999", false)
        assertFalse(store.isNewModNotificationsEnabled("user_999"))

        // Last notified baseline per game
        assertEquals(0L, store.getLastNotifiedEpochSec("user_999", 1643320))
        assertTrue(store.getLastNotifiedModIds("user_999", 1643320).isEmpty())

        store.saveLastNotifiedEpochSec("user_999", 1643320, 1_765_000_000L)
        store.saveLastNotifiedModIds("user_999", 1643320, setOf("1001", "1002"))
        assertEquals(1_765_000_000L, store.getLastNotifiedEpochSec("user_999", 1643320))
        assertEquals(setOf("1001", "1002"), store.getLastNotifiedModIds("user_999", 1643320))

        store.clearLastNotifiedForGame("user_999", 1643320)
        assertEquals(0L, store.getLastNotifiedEpochSec("user_999", 1643320))
        assertTrue(store.getLastNotifiedModIds("user_999", 1643320).isEmpty())

        // Guest preference migration
        store.setNewModNotificationsEnabled("guest", false)
        store.migrateGuestDataIfEmpty("user_fresh")
        assertFalse(store.isNewModNotificationsEnabled("user_fresh"))
    }

    @Test
    fun checkIntervalHours_andSeenModUpdates_persistSanitizeAndMigrate() {
        // Default interval is 2 hours; invalid values sanitize back to default
        assertEquals(2L, store.getCheckIntervalHours("u_interval"))
        store.setCheckIntervalHours("u_interval", 6L)
        assertEquals(6L, store.getCheckIntervalHours("u_interval"))
        store.setCheckIntervalHours("u_interval", 99L)
        assertEquals(2L, store.getCheckIntervalHours("u_interval"))

        val now = 1_765_000_000L
        val oldMod = FollowedWorkshopMod(
            id = "old_mod",
            userId = "u_interval",
            publishedFileId = "old_mod",
            appId = 1643320,
            title = "Old Stable Mod",
            updatedEpochSec = now - (100L * 3600L)
        )
        val recentMod = FollowedWorkshopMod(
            id = "recent_mod",
            userId = "u_interval",
            publishedFileId = "recent_mod",
            appId = 1643320,
            title = "Recently Patched Mod",
            updatedEpochSec = now - (5L * 3600L)
        )

        // Old mod (>48h) gets silently baselined; recent mod (<48h) stays un-seen so UPPDATERAD shows
        store.initializeModUpdateBaselinesIfMissing(
            userId = "u_interval",
            mods = listOf(oldMod, recentMod),
            nowEpochSec = now
        )
        val seenMapAfterInit = store.loadSeenModUpdateMap("u_interval")
        assertEquals(oldMod.updatedEpochSec, seenMapAfterInit["old_mod"])
        assertFalse(seenMapAfterInit.containsKey("recent_mod"))
        assertFalse(oldMod.toModItem().isUpdatedMod(seenMapAfterInit["old_mod"] ?: 0L, now))
        assertTrue(recentMod.toModItem().isUpdatedMod(seenMapAfterInit["recent_mod"] ?: 0L, now))

        // Mark recent mod as seen
        store.saveModUpdateSeen("u_interval", "recent_mod", recentMod.updatedEpochSec!!)
        val seenAfterMark = store.loadSeenModUpdateMap("u_interval")
        assertEquals(recentMod.updatedEpochSec, seenAfterMark["recent_mod"])
        assertFalse(recentMod.toModItem().isUpdatedMod(seenAfterMark["recent_mod"] ?: 0L, now))

        // Last notified mod updates map round-trip
        store.saveLastNotifiedModUpdatedMap("u_interval", mapOf("old_mod" to 1_764_000_000L))
        assertEquals(1_764_000_000L, store.loadLastNotifiedModUpdatedMap("u_interval")["old_mod"])

        // Guest interval and seen updates migrate to new user
        store.setCheckIntervalHours("guest", 12L)
        store.saveModUpdateSeen("guest", "guest_mod_1", 1_765_123_456L)
        store.migrateGuestDataIfEmpty("u_migrated_interval")
        assertEquals(12L, store.getCheckIntervalHours("u_migrated_interval"))
        assertEquals(1_765_123_456L, store.loadSeenModUpdateMap("u_migrated_interval")["guest_mod_1"])
    }

    @Test
    fun watchlist_persistsUpsertsRemovesAndMigratesFromGuest() {
        assertTrue(store.loadWatchlist("guest").isEmpty())

        val item1 = WatchlistItem(
            id = "rust",
            userId = "guest",
            gameTitle = "Rust",
            targetPriceEur = 19.99,
            preferredCurrency = "SEK"
        )
        val item2 = WatchlistItem(
            id = "cyberpunk",
            userId = "guest",
            gameTitle = "Cyberpunk 2077",
            targetPriceEur = 24.50,
            preferredCurrency = "EUR"
        )

        store.upsertWatchlistItemLocal("guest", item1)
        store.upsertWatchlistItemLocal("guest", item2)
        val loaded = store.loadWatchlist("guest")
        assertEquals(2, loaded.size)
        assertEquals("cyberpunk", loaded[0].id)
        assertEquals("rust", loaded[1].id)

        // Updating existing item replaces without duplicating
        store.upsertWatchlistItemLocal("guest", item1.copy(targetPriceEur = 14.99))
        val updated = store.loadWatchlist("guest")
        assertEquals(2, updated.size)
        assertEquals(14.99, updated.first { it.id == "rust" }.targetPriceEur, 0.001)

        // Migrate guest watchlist to a new user
        store.migrateGuestDataIfEmpty("user_wl_1")
        val migrated = store.loadWatchlist("user_wl_1")
        assertEquals(2, migrated.size)
        assertTrue(migrated.all { it.userId == "user_wl_1" })

        // Removing an item only removes from that user's store
        store.removeWatchlistItemLocal("guest", "cyberpunk")
        val afterRemove = store.loadWatchlist("guest")
        assertEquals(1, afterRemove.size)
        assertEquals("rust", afterRemove.first().id)

        // Existing user watchlist is not overwritten by guest migration
        store.migrateGuestDataIfEmpty("user_wl_1")
        assertEquals(2, store.loadWatchlist("user_wl_1").size)
    }

    @Test
    fun settingsPreferencesAndQuietHoursWindow_persistEvaluateAndMigrate() {
        // Overnight window 22:00..08:00
        assertTrue(WorkshopLocalStore.isHourInQuietWindow(22, 22, 8))
        assertTrue(WorkshopLocalStore.isHourInQuietWindow(23, 22, 8))
        assertTrue(WorkshopLocalStore.isHourInQuietWindow(0, 22, 8))
        assertTrue(WorkshopLocalStore.isHourInQuietWindow(7, 22, 8))
        assertFalse(WorkshopLocalStore.isHourInQuietWindow(8, 22, 8))
        assertFalse(WorkshopLocalStore.isHourInQuietWindow(15, 22, 8))
        assertFalse(WorkshopLocalStore.isHourInQuietWindow(21, 22, 8))

        // Same-day window 01:00..06:00
        assertTrue(WorkshopLocalStore.isHourInQuietWindow(1, 1, 6))
        assertTrue(WorkshopLocalStore.isHourInQuietWindow(5, 1, 6))
        assertFalse(WorkshopLocalStore.isHourInQuietWindow(6, 1, 6))
        assertFalse(WorkshopLocalStore.isHourInQuietWindow(0, 1, 6))

        // Equal start and end -> empty window
        assertFalse(WorkshopLocalStore.isHourInQuietWindow(10, 10, 10))

        // Defaults & setters on guest
        assertTrue(store.isNotifyNewModsEnabled("guest"))
        assertTrue(store.isNotifyModUpdatesEnabled("guest"))
        assertFalse(store.isQuietHoursEnabled("guest"))
        assertEquals(22, store.getQuietHoursStart("guest"))
        assertEquals(8, store.getQuietHoursEnd("guest"))
        assertEquals(com.example.model.CurrencyMode.SEK, store.getPreferredCurrency("guest"))
        assertTrue(store.isAlwaysOpenModsInSteamApp("guest"))

        store.setNotifyNewModsEnabled("guest", false)
        store.setNotifyModUpdatesEnabled("guest", false)
        store.setQuietHoursEnabled("guest", true)
        store.setQuietHoursStart("guest", 23)
        store.setQuietHoursEnd("guest", 7)
        store.setPreferredCurrency("guest", com.example.model.CurrencyMode.EUR)
        store.setAlwaysOpenModsInSteamApp("guest", false)

        store.migrateGuestDataIfEmpty("user_settings_migrated")
        assertFalse(store.isNotifyNewModsEnabled("user_settings_migrated"))
        assertFalse(store.isNotifyModUpdatesEnabled("user_settings_migrated"))
        assertTrue(store.isQuietHoursEnabled("user_settings_migrated"))
        assertEquals(23, store.getQuietHoursStart("user_settings_migrated"))
        assertEquals(7, store.getQuietHoursEnd("user_settings_migrated"))
        assertEquals(com.example.model.CurrencyMode.EUR, store.getPreferredCurrency("user_settings_migrated"))
        assertFalse(store.isAlwaysOpenModsInSteamApp("user_settings_migrated"))
    }

    @Test
    fun exportBackupJson_andImportBackupJson_roundTripAndValidateErrors() {
        val game = FollowedWorkshopGame(
            id = "294100",
            userId = "backup_src",
            appId = 294100,
            gameName = "RimWorld",
            developer = "Ludeon Studios",
            headerImageUrl = "https://example.com/rimworld.jpg"
        )
        val watch = WatchlistItem(
            id = "cyberpunk",
            userId = "backup_src",
            gameTitle = "Cyberpunk 2077",
            targetPriceEur = 21.50,
            preferredCurrency = "EUR"
        )
        store.saveFollowedGames("backup_src", listOf(game))
        store.saveWatchlist("backup_src", listOf(watch))
        store.setNewModNotificationsEnabled("backup_src", false)
        store.setCheckIntervalHours("backup_src", 6L)
        store.setNotifyNewModsEnabled("backup_src", false)
        store.setNotifyModUpdatesEnabled("backup_src", true)
        store.setQuietHoursEnabled("backup_src", true)
        store.setQuietHoursStart("backup_src", 21)
        store.setQuietHoursEnd("backup_src", 9)
        store.setPreferredCurrency("backup_src", com.example.model.CurrencyMode.EUR)
        store.setAlwaysOpenModsInSteamApp("backup_src", false)

        val backupJson = store.exportBackupJson("backup_src")
        assertTrue(backupJson.contains("RimWorld"))
        assertTrue(backupJson.contains("Cyberpunk 2077"))

        // Invalid inputs fail safely without crashing
        val emptyResult = store.importBackupJson("backup_dst", "   ")
        assertFalse(emptyResult.success)
        assertNotNull(emptyResult.errorMessage)

        val malformedResult = store.importBackupJson("backup_dst", "{not-json")
        assertFalse(malformedResult.success)

        val unrelatedJsonResult = store.importBackupJson("backup_dst", """{"hello":"world"}""")
        assertFalse(unrelatedJsonResult.success)

        // Valid import restores games, watchlist, and all notification/display preferences
        val okResult = store.importBackupJson("backup_dst", backupJson)
        assertTrue(okResult.success)
        assertEquals(1, okResult.restoredGamesCount)
        assertEquals(1, okResult.restoredWatchlistCount)
        assertTrue(okResult.restoredPreferences)

        val dstSnapshot = store.loadSnapshot("backup_dst")
        assertEquals(1, dstSnapshot.followedGames.size)
        assertEquals(294100, dstSnapshot.followedGames.first().appId)

        val dstWatchlist = store.loadWatchlist("backup_dst")
        assertEquals(1, dstWatchlist.size)
        assertEquals(21.50, dstWatchlist.first().targetPriceEur, 0.001)

        assertFalse(store.isNewModNotificationsEnabled("backup_dst"))
        assertEquals(6L, store.getCheckIntervalHours("backup_dst"))
        assertFalse(store.isNotifyNewModsEnabled("backup_dst"))
        assertTrue(store.isNotifyModUpdatesEnabled("backup_dst"))
        assertTrue(store.isQuietHoursEnabled("backup_dst"))
        assertEquals(21, store.getQuietHoursStart("backup_dst"))
        assertEquals(9, store.getQuietHoursEnd("backup_dst"))
        assertEquals(com.example.model.CurrencyMode.EUR, store.getPreferredCurrency("backup_dst"))
        assertFalse(store.isAlwaysOpenModsInSteamApp("backup_dst"))
    }
}
