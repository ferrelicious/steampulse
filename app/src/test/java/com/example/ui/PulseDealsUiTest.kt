package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.SteamPulseTopBar
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
import com.example.ui.screens.PulseDealsScreen
import com.example.ui.theme.SteamPulseTheme
import com.example.viewmodel.SteamPulseUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PulseDealsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun topBar_guestModeShowsTitleCurrencyAndSingleLoginButton_withoutVersionBadgeOrDuplicateLogout() {
        var loginClicked = false
        var selectedCurrency = CurrencyMode.SEK

        composeTestRule.setContent {
            SteamPulseTheme {
                SteamPulseTopBar(
                    currency = selectedCurrency,
                    isGuest = true,
                    userDisplayName = null,
                    userEmail = null,
                    hasUpdateAvailable = false,
                    onSelectCurrency = { selectedCurrency = it },
                    onNavigateToLogin = { loginClicked = true },
                    onOpenReleases = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithText("SteamPulse").assertIsDisplayed()
        composeTestRule.onNodeWithText("& DEALS 2026").assertIsDisplayed()
        composeTestRule.onNodeWithTag("currency_sek").assertIsDisplayed()
        composeTestRule.onNodeWithTag("currency_eur").assertIsDisplayed()
        composeTestRule.onNodeWithTag("guest_login_button").assertIsDisplayed()

        composeTestRule.onNodeWithText("v1.0.0", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("sign_out_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("account_menu_button").assertDoesNotExist()

        composeTestRule.onNodeWithTag("guest_login_button").performClick()
        assertTrue(loginClicked)
    }

    @Test
    fun topBar_signedInModeShowsAccountAvatarMenuAndSignOut() {
        var signedOut = false

        composeTestRule.setContent {
            SteamPulseTheme {
                SteamPulseTopBar(
                    currency = CurrencyMode.SEK,
                    isGuest = false,
                    userDisplayName = "Alex Gamer",
                    userEmail = "alex@example.com",
                    hasUpdateAvailable = false,
                    onSelectCurrency = {},
                    onNavigateToLogin = {},
                    onOpenReleases = {},
                    onSignOut = { signedOut = true }
                )
            }
        }

        composeTestRule.onNodeWithText("SteamPulse").assertIsDisplayed()
        composeTestRule.onNodeWithTag("guest_login_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("account_menu_button").assertIsDisplayed().performClick()

        composeTestRule.onNodeWithText("Alex Gamer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sign_out_button").assertIsDisplayed().performClick()
        assertTrue(signedOut)
    }

    @Test
    fun pulseDealsScreen_displaysCompactPlaceholderClearButtonHighlightsAndSteamGameArt() {
        var query by mutableStateOf("")
        val catalogGames = SteamGamesCatalog.initialGames.take(3)

        composeTestRule.setContent {
            SteamPulseTheme {
                PulseDealsScreen(
                    uiState = SteamPulseUiState(
                        games = catalogGames,
                        searchQuery = query
                    ),
                    watchlistItems = emptyList(),
                    onSearchChange = { query = it },
                    onCategorySelect = {},
                    onSortSelect = {},
                    onResetFilters = { query = "" },
                    onOpenGameDetail = {},
                    onCloseGameDetail = {},
                    onSelectEdition = {},
                    onToggleWatchlist = { _, _ -> },
                    onSavePriceAlert = { _, _ -> },
                    onAskAiAboutGame = {}
                )
            }
        }

        composeTestRule.onNodeWithText("MEST SPELADE IDAG").assertIsDisplayed()
        composeTestRule.onNodeWithText("HETASTE RELEASES 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sök speltitel (t.ex. CS2, Rust)...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_search_button").assertDoesNotExist()

        // Non-empty query shows trailing clear icon and clears query on click
        composeTestRule.runOnUiThread {
            query = "CS2"
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_search_button").assertIsDisplayed().performClick()
        assertEquals("", query)

        // Game cards expose real game artwork contentDescription for accessibility & Coil image loading
        val firstGame = catalogGames.first()
        composeTestRule.onNodeWithTag("game_card_${firstGame.id}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(firstGame.title, useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
