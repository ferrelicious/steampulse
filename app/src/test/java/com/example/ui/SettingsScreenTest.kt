package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.data.BackupRestoreResult
import com.example.data.WorkshopLocalStore
import com.example.model.CurrencyMode
import com.example.model.FollowedWorkshopGame
import com.example.model.WatchlistItem
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SteamPulseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun settingsScreen_rendersSectionsUpdatesQuietHoursBackupAndCacheDialog() {
        val userKey = "settings_ui_user"
        store.saveFollowedGames(
            userKey,
            listOf(
                FollowedWorkshopGame(
                    id = "1643320",
                    userId = userKey,
                    appId = 1643320,
                    gameName = "S.T.A.L.K.E.R. 2"
                )
            )
        )
        store.saveWatchlist(
            userKey,
            listOf(
                WatchlistItem(
                    id = "rust",
                    userId = userKey,
                    gameTitle = "Rust",
                    targetPriceEur = 19.99,
                    preferredCurrency = "SEK"
                )
            )
        )

        var checkUpdateCalled = false
        var clearCacheCalled = false
        var backCalled = false
        var updateMessage by mutableStateOf<String?>(null)
        var quietHoursEnabled by mutableStateOf(false)
        var quietStart by mutableStateOf(22)
        var quietEnd by mutableStateOf(8)
        var currency by mutableStateOf(CurrencyMode.SEK)
        var openInSteamApp by mutableStateOf(true)
        var lastShownToast by mutableStateOf<String?>(null)
        var importedJsonCaptured = ""

        composeTestRule.setContent {
            SteamPulseTheme {
                SettingsScreen(
                    isGuest = true,
                    userDisplayName = null,
                    userEmail = null,
                    hasSteamSession = false,
                    steamSessionSteamId64 = "",
                    isCheckingForUpdate = false,
                    updateStatusMessage = updateMessage,
                    notificationsEnabled = true,
                    checkIntervalHours = 2L,
                    notifyNewModsEnabled = true,
                    notifyModUpdatesEnabled = true,
                    quietHoursEnabled = quietHoursEnabled,
                    quietHoursStartHour = quietStart,
                    quietHoursEndHour = quietEnd,
                    currency = currency,
                    alwaysOpenModsInSteamApp = openInSteamApp,
                    onBack = { backCalled = true },
                    onCheckForUpdate = {
                        checkUpdateCalled = true
                        updateMessage = "Du har redan den senaste versionen (v${BuildConfig.VERSION_NAME})."
                    },
                    onOpenReleases = {},
                    onNavigateToLogin = {},
                    onSignOut = {},
                    onConnectSteamSession = {},
                    onClearSteamSession = {},
                    onSetNotificationsEnabled = {},
                    onSelectCheckIntervalHours = {},
                    onSetNotifyNewModsEnabled = {},
                    onSetNotifyModUpdatesEnabled = {},
                    onSetQuietHoursEnabled = { quietHoursEnabled = it },
                    onSetQuietHoursRange = { s, e ->
                        quietStart = s
                        quietEnd = e
                    },
                    onSelectCurrency = { currency = it },
                    onSetAlwaysOpenModsInSteamApp = { openInSteamApp = it },
                    onExportBackupJson = { store.exportBackupJson(userKey) },
                    onImportBackupJson = { raw ->
                        importedJsonCaptured = raw
                        store.importBackupJson("restored_target", raw)
                    },
                    onClearCachedLists = { clearCacheCalled = true },
                    onShowMessage = { lastShownToast = it }
                )
            }
        }

        // 1. Version text & manual update check
        composeTestRule.onNodeWithTag("settings_version_text")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_check_updates_button")
            .performScrollTo()
            .performClick()
        assertTrue(checkUpdateCalled)
        composeTestRule.onNodeWithTag("settings_update_status")
            .performScrollTo()
            .assertIsDisplayed()

        // 2. Quiet hours switch toggles start/end pickers
        composeTestRule.onNodeWithTag("settings_quiet_start_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_quiet_hours_switch")
            .performScrollTo()
            .performClick()
        assertTrue(quietHoursEnabled)
        composeTestRule.onNodeWithTag("settings_quiet_start_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_quiet_end_button")
            .performScrollTo()
            .assertIsDisplayed()

        // 3. Currency and Steam link toggles
        composeTestRule.onNodeWithTag("settings_currency_eur")
            .performScrollTo()
            .performClick()
        assertEquals(CurrencyMode.EUR, currency)

        composeTestRule.onNodeWithTag("settings_open_mods_in_steam_switch")
            .performScrollTo()
            .performClick()
        assertFalse(openInSteamApp)

        // 4. Copy JSON backup to ClipboardManager & Restore from ClipboardManager
        composeTestRule.onNodeWithTag("settings_copy_backup_button")
            .performScrollTo()
            .performClick()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        assertTrue(copied.contains("1643320"))
        assertTrue(copied.contains("rust"))

        // Replace clipboard content and restore
        clipboard.setPrimaryClip(ClipData.newPlainText("SteamPulse Backup", copied))
        composeTestRule.onNodeWithTag("settings_restore_backup_button")
            .performScrollTo()
            .performClick()
        assertEquals(copied, importedJsonCaptured)
        assertEquals(1, store.loadSnapshot("restored_target").followedGames.size)
        assertEquals(1, store.loadWatchlist("restored_target").size)

        // 5. Clear local cache confirmation dialog preserves followed games and watchlist
        composeTestRule.onNodeWithTag("settings_clear_cache_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("clear_cache_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_clear_cache_button").performClick()

        assertTrue(clearCacheCalled)
        assertEquals(1, store.loadSnapshot(userKey).followedGames.size)
        assertEquals(1, store.loadWatchlist(userKey).size)
        assertTrue(lastShownToast?.contains("cache", ignoreCase = true) == true)

        // 6. Back button triggers onBack
        composeTestRule.onNodeWithTag("settings_back_button")
            .performScrollTo()
            .performClick()
        assertTrue(backCalled)
    }
}
