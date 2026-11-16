package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.SteamGamesCatalog
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopGamesUiState
import com.example.model.WorkshopLeaderboardMode
import com.example.model.WorkshopModItem
import com.example.model.WorkshopModsUiState
import com.example.model.WorkshopSectionTab
import com.example.model.WorkshopSortOrder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamAmber
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamCard
import com.example.ui.theme.SteamDanger
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSale
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextMuted
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.util.SteamDeepLinkHelper
import com.example.viewmodel.WorkshopScreenUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopScreen(
    uiState: WorkshopScreenUiState,
    onSelectSectionTab: (WorkshopSectionTab) -> Unit = {},
    onSelectLeaderboardMode: (WorkshopLeaderboardMode) -> Unit,
    onGameSearchChange: (String) -> Unit,
    onSubmitGameSearch: () -> Unit = {},
    onToggleOnlyWorkshopPowerhouses: () -> Unit,
    onRefreshGames: () -> Unit,
    onOpenGameWorkshop: (WorkshopGameSummary) -> Unit,
    onOpenCustomAppId: (Int) -> Unit,
    onCloseGameWorkshop: () -> Unit,
    onSelectSortOrder: (WorkshopSortOrder) -> Unit,
    onModSearchChange: (String) -> Unit,
    onSubmitModSearch: () -> Unit,
    onRefreshMods: () -> Unit,
    onLoadMoreMods: () -> Unit,
    onOpenModDetail: (WorkshopModItem) -> Unit,
    onCloseModDetail: () -> Unit,
    onToggleFollowGame: (WorkshopGameSummary) -> Unit = {},
    onToggleBookmarkMod: (WorkshopModItem, String?) -> Unit = { _, _ -> },
    onRemoveFollowedMod: (String) -> Unit = {},
    onRefreshFollowedFeed: () -> Unit = {},
    onOpenSteamSyncDialog: () -> Unit = {},
    onDismissSteamSyncDialog: () -> Unit = {},
    onSteamProfileInputChange: (String) -> Unit = {},
    onSubmitSteamSync: () -> Unit = {},
    onOpenSteamWebViewLogin: () -> Unit = {},
    onDismissSteamWebViewLogin: () -> Unit = {},
    onSteamWebViewAuthenticated: (String, String) -> Unit = { _, _ -> },
    onLogoutSteamSession: () -> Unit = {},
    onWorkshopVisited: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onWorkshopVisited()
    }

    val openUrl: (String) -> Unit = { url ->
        if (url.isNotBlank()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }
    val openModInSteam: (WorkshopModItem) -> Unit = { mod ->
        SteamDeepLinkHelper.openWorkshopModInSteam(
            context = context,
            publishedFileId = mod.publishedFileId,
            itemUrl = mod.itemUrl
        )
    }

    val selectedGame = uiState.selectedGame
    if (selectedGame == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            WorkshopSectionSwitcher(
                activeTab = uiState.activeSectionTab,
                followedBadgeCount = uiState.followedMods.size + uiState.followedGames.size,
                onSelectTab = onSelectSectionTab
            )

            when (uiState.activeSectionTab) {
                WorkshopSectionTab.DISCOVER -> Top100GamesDirectory(
                    uiState = uiState,
                    onSelectLeaderboardMode = onSelectLeaderboardMode,
                    onGameSearchChange = onGameSearchChange,
                    onSubmitGameSearch = onSubmitGameSearch,
                    onToggleOnlyWorkshopPowerhouses = onToggleOnlyWorkshopPowerhouses,
                    onRefreshGames = onRefreshGames,
                    onOpenGameWorkshop = onOpenGameWorkshop,
                    onOpenCustomAppId = onOpenCustomAppId
                )

                WorkshopSectionTab.FOLLOWED -> WorkshopFollowedView(
                    uiState = uiState,
                    onOpenGameWorkshop = onOpenGameWorkshop,
                    onToggleFollowGame = onToggleFollowGame,
                    onRefreshFollowedFeed = onRefreshFollowedFeed,
                    onOpenModDetail = onOpenModDetail,
                    onToggleBookmarkMod = onToggleBookmarkMod,
                    onRemoveFollowedMod = onRemoveFollowedMod,
                    onOpenModInSteam = openModInSteam,
                    onOpenSteamSyncDialog = onOpenSteamSyncDialog,
                    onLogoutSteamSession = onLogoutSteamSession
                )
            }
        }
    } else {
        GameWorkshopDetailPage(
            game = selectedGame,
            uiState = uiState,
            isGameFollowed = uiState.followedGameIds.contains(selectedGame.appId),
            onBack = onCloseGameWorkshop,
            onToggleFollowGame = { onToggleFollowGame(selectedGame) },
            onSelectSortOrder = onSelectSortOrder,
            onModSearchChange = onModSearchChange,
            onSubmitModSearch = onSubmitModSearch,
            onRefreshMods = onRefreshMods,
            onLoadMoreMods = onLoadMoreMods,
            onOpenModDetail = onOpenModDetail,
            onToggleBookmarkMod = { mod -> onToggleBookmarkMod(mod, selectedGame.name) },
            onOpenModInSteam = openModInSteam
        )
    }

    val selectedMod = uiState.selectedModDetail
    if (selectedMod != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isBookmarked = uiState.bookmarkedModIds.contains(selectedMod.publishedFileId)
        val detailPrevVisit = if (uiState.selectedGame != null) {
            uiState.activeGamePreviousVisitEpochSec
        } else {
            uiState.followedGamesPreviousVisitEpochSec[selectedMod.appId]
                ?: uiState.previousWorkshopVisitEpochSec
        }
        ModalBottomSheet(
            onDismissRequest = onCloseModDetail,
            sheetState = sheetState,
            containerColor = SteamSurface,
            contentColor = SteamTextPrimary
        ) {
            WorkshopModDetailSheetContent(
                mod = selectedMod,
                isLoadingDetails = uiState.isModDetailLoading,
                isBookmarked = isBookmarked,
                isNew = selectedMod.isNewMod(detailPrevVisit),
                onToggleBookmark = {
                    onToggleBookmarkMod(selectedMod, uiState.selectedGame?.name)
                },
                onOpenOnSteam = { openModInSteam(selectedMod) }
            )
        }
    }

    if (uiState.showSteamSyncDialog) {
        SteamSyncSubscriptionsDialog(
            input = uiState.steamProfileInput,
            syncState = uiState.steamSyncState,
            hasSteamSession = uiState.hasSteamSession,
            steamSessionSteamId64 = uiState.steamSessionSteamId64,
            onInputChange = onSteamProfileInputChange,
            onDismiss = onDismissSteamSyncDialog,
            onConfirmSync = onSubmitSteamSync,
            onOpenSteamLogin = onOpenSteamWebViewLogin,
            onLogoutSteam = onLogoutSteamSession
        )
    }

    if (uiState.showSteamWebViewLoginDialog) {
        SteamWebViewLoginDialog(
            onDismiss = onDismissSteamWebViewLogin,
            onSteamLoginCaptured = onSteamWebViewAuthenticated
        )
    }
}

@Composable
private fun WorkshopSectionSwitcher(
    activeTab: WorkshopSectionTab,
    followedBadgeCount: Int,
    onSelectTab: (WorkshopSectionTab) -> Unit
) {
    Surface(
        color = SteamSurface,
        border = BorderStroke(1.dp, SteamBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WorkshopSectionTab.entries.forEach { tab ->
                val selected = activeTab == tab
                val labelText = if (tab == WorkshopSectionTab.FOLLOWED && followedBadgeCount > 0) {
                    "${tab.label} ($followedBadgeCount)"
                } else {
                    tab.label
                }
                Surface(
                    onClick = { onSelectTab(tab) },
                    color = if (selected) SteamAccent else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("workshop_section_${tab.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) Color(0xFF020617) else SteamTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Top100GamesDirectory(
    uiState: WorkshopScreenUiState,
    onSelectLeaderboardMode: (WorkshopLeaderboardMode) -> Unit,
    onGameSearchChange: (String) -> Unit,
    onSubmitGameSearch: () -> Unit,
    onToggleOnlyWorkshopPowerhouses: () -> Unit,
    onRefreshGames: () -> Unit,
    onOpenGameWorkshop: (WorkshopGameSummary) -> Unit,
    onOpenCustomAppId: (Int) -> Unit
) {
    val customAppIdInput = uiState.gameSearchQuery.trim().toIntOrNull()
    val filteredGames = uiState.filteredGames

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Telemetry Header
        item(key = "workshop_hero") {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteamSurface),
                border = BorderStroke(1.dp, SteamBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    SteamAccent.copy(alpha = 0.12f),
                                    SteamNeon.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = SteamAccent
                            )
                            Text(
                                text = "STEAM WORKSHOP HUB",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = SteamAccent
                            )
                        }
                        IconButton(
                            onClick = onRefreshGames,
                            enabled = uiState.gamesState !is WorkshopGamesUiState.Loading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("workshop_refresh_games_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Uppdatera Topp 100",
                                tint = SteamAccent
                            )
                        }
                    }

                    Text(
                        text = "Topp 100 mest populära Steam-spel",
                        style = MaterialTheme.typography.titleMedium,
                        color = SteamTextPrimary
                    )
                    Text(
                        text = "Välj ett spel för att öppna dess Steam Workshop-sida och utforska mest prenumererade, nyaste eller trendande moddar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SteamTextSecondary
                    )
                }
            }
        }

        // Leaderboard Mode + Workshop Filter Chips
        item(key = "workshop_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WorkshopLeaderboardMode.entries.forEach { mode ->
                        val selected = uiState.leaderboardMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectLeaderboardMode(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SteamAccent,
                                selectedLabelColor = Color(0xFF020617),
                                containerColor = SteamSurface,
                                labelColor = SteamTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = SteamBorder,
                                selectedBorderColor = SteamAccent
                            ),
                            modifier = Modifier.testTag("workshop_mode_${mode.name.lowercase()}")
                        )
                    }

                    FilterChip(
                        selected = uiState.onlyWorkshopPowerhouses,
                        onClick = onToggleOnlyWorkshopPowerhouses,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Workshop-spel",
                                fontSize = 12.sp,
                                fontWeight = if (uiState.onlyWorkshopPowerhouses) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SteamNeon,
                            selectedLabelColor = Color(0xFF020617),
                            selectedLeadingIconColor = Color(0xFF020617),
                            containerColor = SteamSurface,
                            labelColor = SteamTextSecondary,
                            iconColor = SteamNeon
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.onlyWorkshopPowerhouses,
                            borderColor = SteamBorder,
                            selectedBorderColor = SteamNeon
                        ),
                        modifier = Modifier.testTag("workshop_only_powerhouses_chip")
                    )
                }

                OutlinedTextField(
                    value = uiState.gameSearchQuery,
                    onValueChange = onGameSearchChange,
                    placeholder = {
                        Text(
                            text = "Sök alla Steam-spel eller ange AppID (t.ex. 1643320)...",
                            color = SteamTextMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = SteamTextSecondary
                        )
                    },
                    trailingIcon = {
                        if (uiState.gameSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onGameSearchChange("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Rensa sökning",
                                    tint = SteamTextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (customAppIdInput != null && customAppIdInput > 0) {
                                onOpenCustomAppId(customAppIdInput)
                            } else {
                                onSubmitGameSearch()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SteamAccent,
                        unfocusedBorderColor = SteamBorder,
                        focusedTextColor = SteamTextPrimary,
                        unfocusedTextColor = SteamTextPrimary,
                        cursorColor = SteamAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workshop_game_search_input")
                )

                if (customAppIdInput != null && customAppIdInput > 0) {
                    OutlinedButton(
                        onClick = { onOpenCustomAppId(customAppIdInput) },
                        border = BorderStroke(1.dp, SteamNeon),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_custom_appid_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = SteamNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Öppna Steam Workshop direkt för AppID $customAppIdInput",
                            color = SteamNeon,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (uiState.isSearchingStore) {
            item(key = "store_search_loading") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteamSurface),
                    border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workshop_store_search_loading")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SteamAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Söker i hela Steam Store efter \"${uiState.gameSearchQuery.trim()}\"...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SteamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (uiState.storeSearchError != null) {
            item(key = "store_search_error") {
                WorkshopErrorCard(
                    message = uiState.storeSearchError,
                    onRetry = onSubmitGameSearch
                )
            }
        }

        if (uiState.isShowingStoreSearch) {
            item(key = "store_search_count") {
                Text(
                    text = "STEAM STORE-SÖKNING • ${filteredGames.size} TRÄFFAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamAccent
                )
            }

            if (filteredGames.isEmpty() && !uiState.isSearchingStore) {
                item(key = "store_search_empty") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Inga spel hittades i Steam Store",
                                style = MaterialTheme.typography.titleSmall,
                                color = SteamTextPrimary
                            )
                            Text(
                                text = "Prova ett annat sökord eller ange ett numeriskt Steam AppID (t.ex. 1643320).",
                                style = MaterialTheme.typography.bodySmall,
                                color = SteamTextSecondary
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredGames,
                    key = { "store_${it.appId}" }
                ) { game ->
                    WorkshopGameCard(
                        game = game,
                        isFollowed = uiState.followedGameIds.contains(game.appId),
                        onClick = { onOpenGameWorkshop(game) }
                    )
                }
            }
        } else {
            when (val state = uiState.gamesState) {
                WorkshopGamesUiState.Loading -> {
                    item(key = "games_loading") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteamSurface),
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = SteamAccent,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column {
                                    Text(
                                        text = "Hämtar Topp 100 Steam-spel...",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SteamTextPrimary
                                    )
                                    Text(
                                        text = "Synkar spelarantal och metadata live från SteamSpy",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteamTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                is WorkshopGamesUiState.Error -> {
                    item(key = "games_error") {
                        WorkshopErrorCard(
                            message = state.message,
                            onRetry = onRefreshGames
                        )
                    }
                }

                is WorkshopGamesUiState.Success -> {
                    item(key = "games_count") {
                        Text(
                            text = "VISAR ${filteredGames.size} AV ${state.games.size} SPEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary
                        )
                    }

                    if (filteredGames.isEmpty()) {
                        item(key = "games_empty") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SteamSurface),
                                border = BorderStroke(1.dp, SteamBorder),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Inga matchande spel i Topp 100",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SteamTextPrimary
                                    )
                                    Text(
                                        text = "Skriv minst 2 tecken för att söka i hela Steam Store eller ange ett Steam AppID.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteamTextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredGames,
                            key = { it.appId }
                        ) { game ->
                            WorkshopGameCard(
                                game = game,
                                isFollowed = uiState.followedGameIds.contains(game.appId),
                                onClick = { onOpenGameWorkshop(game) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkshopGameCard(
    game: WorkshopGameSummary,
    isFollowed: Boolean = false,
    onClick: () -> Unit
) {
    var imageAttempt by remember(game.appId, game.customImageUrl) { mutableIntStateOf(0) }
    val imageUrls = remember(game.appId, game.customImageUrl) {
        listOf(
            game.customImageUrl,
            game.headerImageUrl,
            game.fallbackHeaderImageUrl,
            game.capsuleImageUrl
        ).filter { it.isNotBlank() }.distinct()
    }
    val imageUrl = imageUrls.getOrNull(imageAttempt)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(
            1.dp,
            if (game.isKnownWorkshopPowerhouse) SteamAccent.copy(alpha = 0.45f) else SteamBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workshop_game_${game.appId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SteamCard)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = game.name,
                        contentScale = ContentScale.Crop,
                        onError = { imageAttempt += 1 },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(SteamCard, SteamSurface)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = game.name,
                            tint = SteamAccent.copy(alpha = 0.75f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (game.rank > 0) {
                    Surface(
                        color = Color(0xCC020617),
                        shape = RoundedCornerShape(bottomEnd = 8.dp)
                    ) {
                        Text(
                            text = "#${game.rank}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = SteamAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = SteamTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = game.developer.ifBlank { "AppID ${game.appId}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (game.ccu > 0) {
                        Text(
                            text = "${SteamGamesCatalog.formatNumber(game.ccu)} aktiva",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = SteamNeon
                        )
                    }
                    game.positiveRatioPercent?.let { pct ->
                        Text(
                            text = "$pct% pos",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = SteamSale
                        )
                    }
                    if (isFollowed) {
                        Surface(
                            color = SteamNeon.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Följer",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = SteamNeon,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (game.isKnownWorkshopPowerhouse) {
                        Surface(
                            color = SteamAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Workshop",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = SteamAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.Rounded.Extension,
                contentDescription = "Öppna Workshop för ${game.name}",
                tint = if (game.isKnownWorkshopPowerhouse) SteamAccent else SteamTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun GameWorkshopDetailPage(
    game: WorkshopGameSummary,
    uiState: WorkshopScreenUiState,
    isGameFollowed: Boolean,
    onBack: () -> Unit,
    onToggleFollowGame: () -> Unit,
    onSelectSortOrder: (WorkshopSortOrder) -> Unit,
    onModSearchChange: (String) -> Unit,
    onSubmitModSearch: () -> Unit,
    onRefreshMods: () -> Unit,
    onLoadMoreMods: () -> Unit,
    onOpenModDetail: (WorkshopModItem) -> Unit,
    onToggleBookmarkMod: (WorkshopModItem) -> Unit,
    onOpenModInSteam: (WorkshopModItem) -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val openUrl: (String) -> Unit = { url ->
        if (url.isNotBlank()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SteamBg)
    ) {
        // Sub-page Top Navigation Row
        Surface(
            color = SteamSurface,
            border = BorderStroke(1.dp, SteamBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workshop_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Tillbaka till Topp 100",
                            tint = SteamTextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = SteamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Steam Workshop • AppID ${game.appId}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = SteamAccent
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshMods,
                    enabled = uiState.modsState !is WorkshopModsUiState.Loading,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Ladda om moddar",
                        tint = SteamAccent
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val twoColumn = maxWidth >= 660.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Game Hero Banner
                item(key = "workshop_game_hero") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            var heroAttempt by remember(game.appId, game.customImageUrl) { mutableIntStateOf(0) }
                            val heroUrls = remember(game.appId, game.customImageUrl) {
                                listOf(
                                    game.headerImageUrl,
                                    game.fallbackHeaderImageUrl,
                                    game.capsuleImageUrl,
                                    game.customImageUrl
                                ).filter { it.isNotBlank() }.distinct()
                            }
                            val heroUrl = heroUrls.getOrNull(heroAttempt)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .background(SteamCard)
                            ) {
                                if (heroUrl != null) {
                                    AsyncImage(
                                        model = heroUrl,
                                        contentDescription = game.name,
                                        contentScale = ContentScale.Crop,
                                        onError = { heroAttempt += 1 },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SportsEsports,
                                            contentDescription = game.name,
                                            tint = SteamAccent.copy(alpha = 0.5f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color(0xDD0B101B))
                                            )
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = game.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = SteamTextPrimary
                                        )
                                        Text(
                                            text = buildString {
                                                if (game.developer.isNotBlank()) append(game.developer)
                                                if (game.ccu > 0) {
                                                    if (isNotEmpty()) append(" • ")
                                                    append("${SteamGamesCatalog.formatNumber(game.ccu)} aktiva spelare")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SteamTextSecondary
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = onToggleFollowGame,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isGameFollowed) SteamNeon else SteamCard,
                                                contentColor = if (isGameFollowed) Color(0xFF020617) else SteamTextPrimary
                                            ),
                                            border = BorderStroke(1.dp, if (isGameFollowed) SteamNeon else SteamBorder),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("follow_game_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isGameFollowed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = if (isGameFollowed) "Följer" else "Följ spel",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { openUrl(game.workshopHomeUrl) },
                                            border = BorderStroke(1.dp, SteamAccent),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = null,
                                                tint = SteamAccent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "Steam",
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = SteamTextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sort Chips & Mod Search
                item(key = "workshop_mod_controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WorkshopSortOrder.entries.forEach { sort ->
                                val selected = uiState.sortOrder == sort
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectSortOrder(sort) },
                                    label = {
                                        Text(
                                            text = sort.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SteamAccent,
                                        selectedLabelColor = Color(0xFF020617),
                                        containerColor = SteamSurface,
                                        labelColor = SteamTextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = SteamBorder,
                                        selectedBorderColor = SteamAccent
                                    ),
                                    modifier = Modifier.testTag("workshop_sort_${sort.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.modSearchQuery,
                                onValueChange = onModSearchChange,
                                placeholder = {
                                    Text(
                                        text = "Sök moddar i ${game.name}...",
                                        color = SteamTextMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = SteamTextSecondary
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.modSearchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                onModSearchChange("")
                                                onSubmitModSearch()
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Rensa modsökning",
                                                tint = SteamTextSecondary
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSubmitModSearch() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SteamAccent,
                                    unfocusedBorderColor = SteamBorder,
                                    focusedTextColor = SteamTextPrimary,
                                    unfocusedTextColor = SteamTextPrimary,
                                    cursorColor = SteamAccent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("workshop_mod_search_input")
                            )

                            Button(
                                onClick = onSubmitModSearch,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SteamAccent,
                                    contentColor = Color(0xFF020617)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("workshop_mod_search_button")
                            ) {
                                Text("Sök", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                when (val modsState = uiState.modsState) {
                    WorkshopModsUiState.Loading -> {
                        item(key = "mods_loading") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SteamSurface),
                                border = BorderStroke(1.dp, SteamBorder),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = SteamAccent,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Hämtar Workshop-moddar...",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = SteamTextPrimary
                                        )
                                        Text(
                                            text = "Sortering: ${uiState.sortOrder.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SteamTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is WorkshopModsUiState.Error -> {
                        item(key = "mods_error") {
                            WorkshopErrorCard(
                                message = modsState.message,
                                onRetry = onRefreshMods
                            )
                        }
                    }

                    is WorkshopModsUiState.Success -> {
                        if (modsState.mods.isEmpty()) {
                            item(key = "mods_empty") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SteamSurface),
                                    border = BorderStroke(1.dp, SteamBorder),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Extension,
                                            contentDescription = null,
                                            tint = SteamTextSecondary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                        Text(
                                            text = "Inga publika Workshop-moddar hittades",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = SteamTextPrimary
                                        )
                                        Text(
                                            text = "Det här spelet kanske inte har en aktiv Steam Workshop eller så gav sökningen inga träffar.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SteamTextSecondary
                                        )
                                        val diagText = modsState.diagnosticInfo.ifBlank {
                                            "AppID: ${game.appId} • HTTP 200 • 0 träffar"
                                        }
                                        Surface(
                                            color = SteamBg,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, SteamBorder)
                                        ) {
                                            Text(
                                                text = diagText,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 11.sp,
                                                color = SteamTextMuted,
                                                modifier = Modifier
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                                    .testTag("workshop_empty_diagnostic")
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { openUrl(game.workshopHomeUrl) },
                                            border = BorderStroke(1.dp, SteamBorder),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = null,
                                                tint = SteamAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Kontrollera på Steam Community", color = SteamTextPrimary)
                                        }
                                    }
                                }
                            }
                        } else {
                            item(key = "mods_summary") {
                                Text(
                                    text = "${modsState.mods.size} MODDAR • ${uiState.sortOrder.label.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SteamTextSecondary
                                )
                            }

                            if (twoColumn) {
                                val rows = modsState.mods.chunked(2)
                                items(
                                    items = rows,
                                    key = { pair -> pair.joinToString("_") { it.publishedFileId } }
                                ) { rowMods ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowMods.forEach { mod ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                WorkshopModCard(
                                                    mod = mod,
                                                    isBookmarked = uiState.bookmarkedModIds.contains(mod.publishedFileId),
                                                    isNew = mod.isNewMod(uiState.activeGamePreviousVisitEpochSec),
                                                    onClick = { onOpenModDetail(mod) },
                                                    onToggleBookmark = { onToggleBookmarkMod(mod) },
                                                    onOpenInSteam = { onOpenModInSteam(mod) }
                                                )
                                            }
                                        }
                                        if (rowMods.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = modsState.mods,
                                    key = { it.publishedFileId }
                                ) { mod ->
                                    WorkshopModCard(
                                        mod = mod,
                                        isBookmarked = uiState.bookmarkedModIds.contains(mod.publishedFileId),
                                        isNew = mod.isNewMod(uiState.activeGamePreviousVisitEpochSec),
                                        onClick = { onOpenModDetail(mod) },
                                        onToggleBookmark = { onToggleBookmarkMod(mod) },
                                        onOpenInSteam = { onOpenModInSteam(mod) }
                                    )
                                }
                            }

                            if (modsState.hasMore) {
                                item(key = "load_more_mods") {
                                    OutlinedButton(
                                        onClick = onLoadMoreMods,
                                        enabled = !uiState.isLoadingMoreMods,
                                        border = BorderStroke(1.dp, SteamAccent),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("workshop_load_more_button")
                                    ) {
                                        if (uiState.isLoadingMoreMods) {
                                            CircularProgressIndicator(
                                                color = SteamAccent,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Laddar fler moddar...", color = SteamTextPrimary)
                                        } else {
                                            Text(
                                                text = "Ladda fler moddar (sida ${modsState.currentPage + 1})",
                                                color = SteamAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkshopModCard(
    mod: WorkshopModItem,
    isBookmarked: Boolean,
    isNew: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenInSteam: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(
            1.dp,
            if (isNew) SteamSale.copy(alpha = 0.45f) else SteamBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workshop_mod_${mod.publishedFileId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SteamCard)
                ) {
                    if (mod.previewUrl.isNotBlank()) {
                        AsyncImage(
                            model = mod.previewUrl,
                            contentDescription = mod.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = SteamAccent,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = mod.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = SteamTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "av ${mod.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SteamTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isNew) {
                            ModNewBadgePill(
                                modifier = Modifier.testTag("mod_new_badge_${mod.publishedFileId}")
                            )
                        }
                        mod.subscriptions?.let { subs ->
                            Surface(
                                color = SteamNeon.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Group,
                                        contentDescription = null,
                                        tint = SteamNeon,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = formatCompactCount(subs),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SteamNeon
                                    )
                                }
                            }
                        }

                        mod.fileSizeBytes?.let { size ->
                            if (size > 0L) {
                                Text(
                                    text = formatSizeMb(size),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = SteamTextSecondary
                                )
                            }
                        }

                        val displayEpoch = mod.createdEpochSec?.takeIf { it > 0L }
                            ?: mod.updatedEpochSec?.takeIf { it > 0L }
                        displayEpoch?.let { epoch ->
                            Text(
                                text = formatWorkshopEpochDate(epoch),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = SteamTextMuted
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bookmark_mod_${mod.publishedFileId}")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isBookmarked) "Ta bort bokmärke för ${mod.title}" else "Bokmärk ${mod.title}",
                        tint = if (isBookmarked) SteamNeon else SteamTextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OpenInSteamModButton(
                    publishedFileId = mod.publishedFileId,
                    onClick = onOpenInSteam
                )
            }
        }
    }
}

@Composable
internal fun ModNewBadgePill(
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteamSale,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = "NY",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            color = Color(0xFF020617),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun OpenInSteamModButton(
    publishedFileId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("open_in_steam_$publishedFileId")
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = SteamAccent,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Öppna i Steam",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = SteamAccent,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkshopModDetailSheetContent(
    mod: WorkshopModItem,
    isLoadingDetails: Boolean,
    isBookmarked: Boolean,
    isNew: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenOnSteam: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (mod.previewUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SteamCard)
            ) {
                AsyncImage(
                    model = mod.previewUrl,
                    contentDescription = mod.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isNew) {
                    ModNewBadgePill(
                        modifier = Modifier.testTag("sheet_mod_new_badge_${mod.publishedFileId}")
                    )
                }
                Text(
                    text = mod.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = SteamTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Skapare: ${mod.author} • Workshop ID ${mod.publishedFileId}",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = SteamAccent
            )
        }

        // Metrics Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mod.subscriptions?.let { subs ->
                ModStatTile(
                    icon = Icons.Rounded.Group,
                    label = "Prenumeranter",
                    value = formatCompactCount(subs),
                    tint = SteamNeon,
                    modifier = Modifier.weight(1f)
                )
            }
            mod.favorites?.let { favs ->
                ModStatTile(
                    icon = Icons.Rounded.Favorite,
                    label = "Favoriter",
                    value = formatCompactCount(favs),
                    tint = SteamAmber,
                    modifier = Modifier.weight(1f)
                )
            }
            mod.views?.let { views ->
                ModStatTile(
                    icon = Icons.Rounded.Visibility,
                    label = "Visningar",
                    value = formatCompactCount(views),
                    tint = SteamAccent,
                    modifier = Modifier.weight(1f)
                )
            }
            mod.fileSizeBytes?.takeIf { it > 0L }?.let { bytes ->
                ModStatTile(
                    icon = Icons.Rounded.Download,
                    label = "Storlek",
                    value = formatSizeMb(bytes),
                    tint = SteamSale,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (mod.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mod.tags.take(10).forEach { tag ->
                    Surface(
                        color = SteamCard,
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 11.sp,
                            color = SteamTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = SteamBorder)

        if (isLoadingDetails && mod.description.isBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = SteamAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Hämtar mod-beskrivning...",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary
                )
            }
        } else {
            Text(
                text = mod.description.ifBlank {
                    "Öppna modden på Steam Workshop för att läsa full beskrivning, se bilder, kommentarer och prenumerera."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SteamTextSecondary
            )
        }

        OutlinedButton(
            onClick = onToggleBookmark,
            border = BorderStroke(1.dp, if (isBookmarked) SteamNeon else SteamBorder),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .minimumInteractiveComponentSize()
                .testTag("sheet_bookmark_mod_button")
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (isBookmarked) SteamNeon else SteamTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isBookmarked) "Sparad i Mina & Följda" else "Bokmärk modd",
                color = if (isBookmarked) SteamNeon else SteamTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Button(
            onClick = onOpenOnSteam,
            colors = ButtonDefaults.buttonColors(
                containerColor = SteamNeon,
                contentColor = Color(0xFF020617)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .minimumInteractiveComponentSize()
                .testTag("open_mod_on_steam_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Öppna i Steam",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ModStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteamCard,
        border = BorderStroke(1.dp, SteamBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = SteamTextPrimary,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = SteamTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkshopErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = SteamDanger
                )
                Text(
                    text = "Kunde inte ladda data",
                    style = MaterialTheme.typography.titleSmall,
                    color = SteamDanger
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = SteamTextSecondary
            )
            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, SteamBorder),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = SteamAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Försök igen", color = SteamTextPrimary)
            }
        }
    }
}

private fun formatCompactCount(count: Long): String {
    return when {
        count >= 1_000_000L -> String.format(Locale.forLanguageTag("sv-SE"), "%.1fM", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.forLanguageTag("sv-SE"), "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatSizeMb(bytes: Long): String {
    return when {
        bytes < 1024L * 1024L -> String.format(Locale.forLanguageTag("sv-SE"), "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.forLanguageTag("sv-SE"), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
