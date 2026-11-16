package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.SteamPulseTopBar
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
import com.example.model.GameCategory
import com.example.model.SteamGame
import com.example.model.WatchlistItem
import com.example.ui.screens.PulseDealsScreen
import com.example.ui.screens.WatchlistScreen
import com.example.ui.theme.SteamPulseTheme
import com.example.viewmodel.SteamPulseUiState
import com.example.viewmodel.WatchlistUiState
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
        var checkedUpdate = false

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
                    onCheckForUpdate = { checkedUpdate = true },
                    onSignOut = { signedOut = true }
                )
            }
        }

        composeTestRule.onNodeWithText("SteamPulse").assertIsDisplayed()
        composeTestRule.onNodeWithTag("guest_login_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("account_menu_button").assertIsDisplayed().performClick()

        composeTestRule.onNodeWithText("Alex Gamer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("check_update_menu_item").assertIsDisplayed().performClick()
        assertTrue(checkedUpdate)

        composeTestRule.onNodeWithTag("account_menu_button").performClick()
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

    @Test
    fun pulseDealsScreen_followedChipAndModalSheetQuickActionsWork() {
        val paidGame = SteamGamesCatalog.initialGames.first {
            !it.isFreeToPlay && it.steamPriceEur > 0.0 && SteamGamesCatalog.getDiscountPercent(it) > 0
        }
        var selectedCategory by mutableStateOf(GameCategory.FOLLOWED)
        var selectedGameId by mutableStateOf<String?>(null)
        var workshopOpenedFor: SteamGame? = null
        var savedAlertGame: SteamGame? = null
        var savedAlertTarget: Double? = null

        composeTestRule.setContent {
            SteamPulseTheme {
                PulseDealsScreen(
                    uiState = SteamPulseUiState(
                        games = if (selectedCategory == GameCategory.FOLLOWED) emptyList() else listOf(paidGame),
                        selectedCategory = selectedCategory,
                        selectedGameId = selectedGameId
                    ),
                    watchlistItems = emptyList(),
                    onSearchChange = {},
                    onCategorySelect = { selectedCategory = it },
                    onSortSelect = {},
                    onResetFilters = { selectedCategory = GameCategory.ALL },
                    onOpenGameDetail = { selectedGameId = it },
                    onCloseGameDetail = { selectedGameId = null },
                    onSelectEdition = {},
                    onToggleWatchlist = { _, _ -> },
                    onSavePriceAlert = { game, target ->
                        savedAlertGame = game
                        savedAlertTarget = target
                    },
                    onAskAiAboutGame = {},
                    onOpenWorkshopMods = { workshopOpenedFor = it }
                )
            }
        }

        // "Mina följda" filter chip and empty state
        composeTestRule.onNodeWithTag("category_chip_followed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mina följda").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inga följda eller bevakade spel ännu")
            .performScrollTo()
            .assertIsDisplayed()

        // Open detail modal sheet on a paid discounted game
        composeTestRule.runOnUiThread {
            selectedCategory = GameCategory.ALL
            selectedGameId = paidGame.id
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("Spelare Nu").onLast().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("24h Peak").onLast().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("All-Time").onLast().assertIsDisplayed()
        composeTestRule.onNodeWithText("PRISSAMMANFATTNING").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("target_price_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("target_price_input").performScrollTo().performTextReplacement("120")
        composeTestRule.onNodeWithTag("save_price_alert_button").performScrollTo().performClick()
        assertEquals(paidGame.id, savedAlertGame?.id)
        assertEquals(120.0, savedAlertTarget ?: -1.0, 0.001)

        composeTestRule.onNodeWithTag("open_in_steam_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_workshop_mods_button").performScrollTo().performClick()
        assertEquals(paidGame.id, workshopOpenedFor?.id)
    }

    @Test
    fun watchlistScreen_displaysTargetReachedBadgeDiscountAndSavesInlineTargetPrice() {
        val discountedGame = SteamGamesCatalog.initialGames.first {
            !it.isFreeToPlay && it.steamPriceEur > 0.0 && SteamGamesCatalog.getDiscountPercent(it) > 0
        }
        val (_, bestPriceEur) = SteamGamesCatalog.getBestOffer(discountedGame)
        val watchItem = WatchlistItem(
            id = discountedGame.id,
            userId = "guest",
            gameTitle = discountedGame.title,
            targetPriceEur = bestPriceEur + 2.0,
            preferredCurrency = "SEK"
        )
        var updatedGameId: String? = null
        var updatedTargetValue: Double? = null

        composeTestRule.setContent {
            SteamPulseTheme {
                WatchlistScreen(
                    watchlistState = WatchlistUiState.Success(listOf(watchItem)),
                    games = SteamGamesCatalog.initialGames,
                    currency = CurrencyMode.SEK,
                    onOpenGame = {},
                    onRemoveItem = {},
                    onUpdateTargetPrice = { game, target ->
                        updatedGameId = game.id
                        updatedTargetValue = target
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("watchlist_item_${discountedGame.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("target_reached_badge_${discountedGame.id}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Målpris uppnått!").assertIsDisplayed()
        composeTestRule.onNodeWithTag("watchlist_discount_badge_${discountedGame.id}").assertIsDisplayed()

        composeTestRule.onNodeWithTag("watchlist_target_input_${discountedGame.id}")
            .performTextReplacement("185")
        composeTestRule.onNodeWithTag("watchlist_save_target_${discountedGame.id}")
            .performClick()

        assertEquals(discountedGame.id, updatedGameId)
        assertEquals(185.0, updatedTargetValue ?: -1.0, 0.001)
    }
}
