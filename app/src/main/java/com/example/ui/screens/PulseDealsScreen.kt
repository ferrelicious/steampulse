package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
import com.example.model.GameCategory
import com.example.model.GameEdition
import com.example.model.SortOrder
import com.example.model.SteamGame
import com.example.model.WatchlistItem
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
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.util.SteamDeepLinkHelper
import com.example.viewmodel.SteamPulseUiState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseDealsScreen(
    uiState: SteamPulseUiState,
    watchlistItems: List<WatchlistItem>,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (GameCategory) -> Unit,
    onSortSelect: (SortOrder) -> Unit,
    onResetFilters: () -> Unit,
    onRefresh: () -> Unit = {},
    onOpenGameDetail: (String) -> Unit,
    onCloseGameDetail: () -> Unit,
    onSelectEdition: (GameEdition) -> Unit,
    onToggleWatchlist: (SteamGame, Boolean) -> Unit,
    onSavePriceAlert: (SteamGame, Double) -> Unit,
    onAskAiAboutGame: (SteamGame) -> Unit,
    onOpenWorkshopMods: (SteamGame) -> Unit = {}
) {
    val context = LocalContext.current
    val watchedIds = remember(watchlistItems) { watchlistItems.map { it.id }.toSet() }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val isLoading = (uiState.isSearchActive && uiState.isSearching) ||
        (!uiState.isSearchActive && uiState.isLoadingCategory)
    val activeError = if (uiState.isSearchActive) uiState.searchError else uiState.categoryError

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search, Category & Sort Controls
        item(key = "search_and_filters") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            text = "Sök i Steam Store (minst 2 tecken)...",
                            color = SteamTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = SteamAccent
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.isSearching) {
                                CircularProgressIndicator(
                                    color = SteamAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 4.dp)
                                )
                            }
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchChange("") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Rensa sökning",
                                        tint = SteamTextSecondary
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SteamAccent,
                        unfocusedBorderColor = SteamBorder,
                        focusedContainerColor = SteamSurface,
                        unfocusedContainerColor = SteamSurface,
                        focusedTextColor = SteamTextPrimary,
                        unfocusedTextColor = SteamTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_games_input")
                )

                // Clean Steam Store Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameCategory.entries.forEach { category ->
                        val selected = !uiState.isSearchActive && uiState.selectedCategory == category
                        val isFollowed = category == GameCategory.FOLLOWED
                        val isTrending = category == GameCategory.NEW_AND_TRENDING
                        val isTopSellers = category == GameCategory.TOP_SELLERS

                        FilterChip(
                            selected = selected,
                            onClick = { onCategorySelect(category) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when {
                                        isFollowed -> Icons.Rounded.Star
                                        isTopSellers -> Icons.Rounded.LocalOffer
                                        isTrending -> Icons.Rounded.LocalFireDepartment
                                        else -> Icons.Rounded.SportsEsports
                                    },
                                    contentDescription = null,
                                    tint = if (selected) {
                                        Color(0xFF020617)
                                    } else when {
                                        isFollowed -> SteamAmber
                                        isTopSellers -> SteamSale
                                        isTrending -> SteamNeon
                                        else -> SteamAccent
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = category.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when {
                                    isFollowed -> SteamAmber
                                    isTopSellers -> SteamSale
                                    isTrending -> SteamNeon
                                    else -> SteamAccent
                                },
                                selectedLabelColor = Color(0xFF020617),
                                containerColor = SteamSurface,
                                labelColor = when {
                                    isFollowed -> SteamAmber
                                    else -> SteamTextSecondary
                                }
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = when {
                                    isFollowed -> SteamAmber.copy(alpha = 0.45f)
                                    else -> SteamBorder
                                }
                            ),
                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                        )
                    }
                }

                // Count, Refresh & Sort Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (uiState.isSearchActive) {
                                "Sökresultat: ${uiState.games.size} spel"
                            } else {
                                "${uiState.selectedCategory.label} • ${uiState.games.size} spel"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_pulse_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Uppdatera lista",
                                tint = SteamAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = uiState.sortOrder.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = SteamAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                            containerColor = SteamSurface
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.label,
                                            color = if (order == uiState.sortOrder) SteamAccent else SteamTextPrimary,
                                            fontWeight = if (order == uiState.sortOrder) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSortSelect(order)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loading / Error / Empty / Live Results
        when {
            isLoading && uiState.games.isEmpty() -> {
                item(key = "loading_state") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = SteamAccent)
                            Text(
                                text = if (uiState.isSearchActive) {
                                    "Söker i Steam Store..."
                                } else {
                                    "Hämtar livedata från Steam..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteamTextSecondary
                            )
                        }
                    }
                }
            }

            activeError != null && uiState.games.isEmpty() -> {
                item(key = "error_state") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface),
                        border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = SteamDanger,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Kunde inte ladda Steam-data",
                                style = MaterialTheme.typography.titleMedium,
                                color = SteamTextPrimary
                            )
                            Text(
                                text = activeError,
                                style = MaterialTheme.typography.bodySmall,
                                color = SteamTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SteamAccent,
                                    contentColor = Color(0xFF020617)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Försök igen", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            uiState.games.isEmpty() -> {
                item(key = "empty_state") {
                    val isFollowedEmpty = !uiState.isSearchActive && uiState.selectedCategory == GameCategory.FOLLOWED
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isFollowedEmpty) Icons.Rounded.Star else Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = if (isFollowedEmpty) SteamAmber else SteamTextSecondary,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = if (isFollowedEmpty) {
                                    "Inga följda eller bevakade spel ännu"
                                } else {
                                    "Inga spel matchade din sökning"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = SteamTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isFollowedEmpty) {
                                    "Tryck på Bevaka på valfritt spel i Puls eller följ spel i Workshop-fliken."
                                } else {
                                    "Prova att söka på en annan speltitel i Steam Store."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteamTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onResetFilters,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SteamAccent,
                                    contentColor = Color(0xFF020617)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = if (isFollowedEmpty) "Visa mest spelade" else "Återställ filter",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                items(uiState.games, key = { it.id }) { game ->
                    val isWatched = watchedIds.contains(game.id)
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LivePulseGameCard(
                            game = game,
                            currency = uiState.currency,
                            isWatched = isWatched,
                            isSearchResult = uiState.isSearchActive,
                            onCardClick = { onOpenGameDetail(game.id) },
                            onWatchClick = { onToggleWatchlist(game, isWatched) },
                            onWorkshopClick = { onOpenWorkshopMods(game) },
                            onOpenInSteamClick = {
                                SteamDeepLinkHelper.openStoreGameInSteam(
                                    context = context,
                                    appId = game.appId
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail & Price Alert Bottom Sheet
    val selectedGame = uiState.selectedGame
    if (selectedGame != null) {
        val existingWatch = watchlistItems.firstOrNull { it.id == selectedGame.id }
        GameDetailModalSheet(
            game = selectedGame,
            currency = uiState.currency,
            selectedEdition = uiState.selectedEdition,
            existingWatch = existingWatch,
            onDismiss = onCloseGameDetail,
            onSelectEdition = onSelectEdition,
            onToggleWatchlist = { onToggleWatchlist(selectedGame, existingWatch != null) },
            onSavePriceAlert = { target -> onSavePriceAlert(selectedGame, target) },
            onAskAi = {
                onCloseGameDetail()
                onAskAiAboutGame(selectedGame)
            },
            onOpenWorkshopMods = {
                onCloseGameDetail()
                onOpenWorkshopMods(selectedGame)
            }
        )
    }
}

@Composable
private fun LivePulseGameCard(
    game: SteamGame,
    currency: CurrencyMode,
    isWatched: Boolean,
    isSearchResult: Boolean,
    onCardClick: () -> Unit,
    onWatchClick: () -> Unit,
    onWorkshopClick: () -> Unit,
    onOpenInSteamClick: () -> Unit
) {
    val context = LocalContext.current
    val (_, bestPriceEur) = remember(game) { SteamGamesCatalog.getBestOffer(game) }
    val discountPercent = remember(game) { SteamGamesCatalog.getDiscountPercent(game) }

    var headerAttempt by remember(game.appId, game.headerImageUrl) { mutableIntStateOf(0) }
    val headerUrls = remember(game.appId, game.headerImageUrl) {
        buildList {
            if (game.headerImageUrl.isNotBlank()) add(game.headerImageUrl)
            if (game.appId > 0) {
                add("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${game.appId}/header.jpg")
                add("https://cdn.akamai.steamstatic.com/steam/apps/${game.appId}/header.jpg")
            }
        }.distinct()
    }
    val activeHeaderUrl = headerUrls.getOrNull(headerAttempt)

    Card(
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(
            1.dp,
            if (isWatched) SteamAmber.copy(alpha = 0.55f) else SteamBorder
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick)
            .testTag("game_card_${game.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Steam Header Artwork Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .background(Brush.linearGradient(listOf(game.gradientStart, game.gradientEnd)))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = game.shortCode,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }

                if (activeHeaderUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(activeHeaderUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        onError = { headerAttempt += 1 },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x33020617), Color(0xCC090D16))
                            )
                        )
                )

                // Top Row: Rank Badge & Discount Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (game.rank > 0 && !isSearchResult) {
                        Surface(
                            color = Color(0xCC020617),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SteamBorder)
                        ) {
                            Text(
                                text = "#${game.rank}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = if (game.rank <= 3) SteamAccent else SteamTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (discountPercent > 0) {
                        Surface(
                            color = SteamSale,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("game_discount_badge_${game.id}")
                        ) {
                            Text(
                                text = "-$discountPercent%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = Color(0xFF020617),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Bottom Live Player Count Badge
                if (game.playersNow > 0) {
                    Surface(
                        color = Color(0xD9020617),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .testTag("live_players_badge_${game.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SteamNeon)
                            )
                            Text(
                                text = "${SteamGamesCatalog.formatNumber(game.playersNow)} spelar nu",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = SteamNeon
                            )
                        }
                    }
                }
            }

            // Card Content: Title, Price & Action Buttons
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SteamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metaSubtitle = buildList {
                            if (game.developer.isNotBlank() && game.developer != "Steam") add(game.developer)
                            if (game.genres.isNotEmpty()) add(game.genres.take(2).joinToString(" • "))
                        }.joinToString(" • ").ifBlank { "Steam AppID ${game.appId}" }
                        Text(
                            text = metaSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Current Steam Price Column
                    Column(horizontalAlignment = Alignment.End) {
                        if (game.isFreeToPlay || bestPriceEur <= 0.0) {
                            Surface(
                                color = SteamNeon.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "Gratis",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = SteamNeon,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            if (discountPercent > 0 && game.steamPriceEur > bestPriceEur) {
                                Text(
                                    text = SteamGamesCatalog.formatPrice(game.steamPriceEur, currency),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = SteamTextSecondary,
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                            Text(
                                text = SteamGamesCatalog.formatPrice(bestPriceEur, currency),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = if (discountPercent > 0) SteamSale else SteamTextPrimary
                            )
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWatched) SteamAmber else SteamAccent,
                            contentColor = Color(0xFF020617)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("card_watch_button_${game.id}")
                    ) {
                        Icon(
                            imageVector = if (isWatched) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isWatched -> "Bevakad"
                                isSearchResult -> "Bevaka pris"
                                else -> "Bevaka"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isSearchResult) {
                        OutlinedButton(
                            onClick = onOpenInSteamClick,
                            border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("card_steam_button_${game.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = SteamNeon,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Öppna i Steam",
                                color = SteamNeon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onWorkshopClick,
                            border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("card_workshop_button_${game.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = SteamAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Workshop",
                                color = SteamAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GameDetailModalSheet(
    game: SteamGame,
    currency: CurrencyMode,
    selectedEdition: GameEdition,
    existingWatch: WatchlistItem?,
    onDismiss: () -> Unit,
    onSelectEdition: (GameEdition) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSavePriceAlert: (Double) -> Unit,
    onAskAi: () -> Unit,
    onOpenWorkshopMods: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (_, currentBestEur) = remember(game, selectedEdition) {
        SteamGamesCatalog.getBestOffer(game, selectedEdition)
    }
    val discountPercent = remember(game) { SteamGamesCatalog.getDiscountPercent(game) }
    val steamBaseInCurrency = remember(game, currency, selectedEdition) {
        val mult = if (selectedEdition == GameEdition.DELUXE) game.deluxeMultiplier else 1.0
        val base = if (game.steamPriceEur > 0.0) game.steamPriceEur else game.finalPriceEur
        SteamGamesCatalog.getNumericPrice(base * mult, currency)
    }
    val sliderMax = remember(steamBaseInCurrency, currency) {
        val minTop = if (currency == CurrencyMode.SEK) 150f else 15f
        max(minTop, ceil(steamBaseInCurrency * 1.15).toFloat())
    }

    val defaultSuggested = remember(game.id, currency, existingWatch) {
        if (existingWatch != null) {
            val num = SteamGamesCatalog.getNumericPrice(existingWatch.targetPriceEur, currency)
            if (currency == CurrencyMode.SEK) num.roundToInt().toString() else num.toString()
        } else {
            val (_, bestEur) = SteamGamesCatalog.getBestOffer(game, GameEdition.STANDARD)
            val suggested = max(1.0, floor(SteamGamesCatalog.getNumericPrice(bestEur, currency) * 0.85))
            suggested.roundToInt().toString()
        }
    }
    var targetPriceInput by remember(game.id, currency) { mutableStateOf(defaultSuggested) }
    val sliderValue = (targetPriceInput.replace(",", ".").toFloatOrNull() ?: 0f)
        .coerceIn(0f, sliderMax)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SteamSurface,
        contentColor = SteamTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            var headerImageAttempt by remember(game.appId, game.headerImageUrl) { mutableIntStateOf(0) }
            val headerUrls = remember(game.appId, game.headerImageUrl) {
                buildList {
                    if (game.headerImageUrl.isNotBlank()) add(game.headerImageUrl)
                    if (game.appId > 0) {
                        add("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${game.appId}/header.jpg")
                        add("https://cdn.akamai.steamstatic.com/steam/apps/${game.appId}/header.jpg")
                    }
                }.distinct()
            }
            val activeHeaderUrl = headerUrls.getOrNull(headerImageAttempt)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(game.gradientStart, game.gradientEnd)))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = game.shortCode,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )
                }
                if (activeHeaderUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(activeHeaderUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${game.title} Steam-omslag",
                        contentScale = ContentScale.Crop,
                        onError = { headerImageAttempt += 1 },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC020617))
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xCC020617),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (game.rank > 0) "#${game.rank} • AppID ${game.appId}" else "AppID ${game.appId}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = SteamAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = Color(0xCC020617),
                        shape = CircleShape
                    ) {
                        IconButton(
                            onClick = onToggleWatchlist,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("modal_watchlist_toggle")
                        ) {
                            Icon(
                                imageVector = if (existingWatch != null) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                                contentDescription = if (existingWatch != null) "Ta bort från bevakning" else "Lägg till i bevakning",
                                tint = if (existingWatch != null) SteamAmber else Color.White
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildList {
                            if (game.developer.isNotBlank()) add(game.developer)
                            if (game.releaseDate.isNotBlank()) add(game.releaseDate)
                        }.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (game.genres.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    game.genres.take(5).forEach { genre ->
                        Surface(
                            color = SteamBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SteamBorder)
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (game.shortDescription.isNotBlank()) {
                Text(
                    text = game.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Live Player Count Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatBox(
                    label = "Spelare Nu",
                    value = if (game.playersNow > 0) SteamGamesCatalog.formatNumber(game.playersNow) else "—",
                    valueColor = SteamNeon,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatBox(
                    label = "24h Peak",
                    value = if (game.peak24h > 0) SteamGamesCatalog.formatNumber(game.peak24h) else "—",
                    valueColor = SteamTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Steam Store Price Summary
            Surface(
                color = SteamBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SteamBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "PRIS PÅ STEAM STORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamTextSecondary
                    )
                    if (game.isFreeToPlay || currentBestEur <= 0.0) {
                        Text(
                            text = "Gratis att spela på Steam",
                            style = MaterialTheme.typography.titleMedium,
                            color = SteamNeon
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = SteamGamesCatalog.formatPrice(currentBestEur, currency),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = SteamNeon
                            )
                            if (discountPercent > 0) {
                                Surface(
                                    color = SteamSale,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "-$discountPercent%",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF020617),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = SteamGamesCatalog.formatPrice(game.steamPriceEur, currency),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = SteamTextSecondary,
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                        }
                    }
                }
            }

            // Quick Cross-Tab & Steam Deep-Link Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenWorkshopMods,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamAccent,
                        contentColor = Color(0xFF020617)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_workshop_mods_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Visa Workshop-moddar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        SteamDeepLinkHelper.openStoreGameInSteam(
                            context = context,
                            appId = game.appId
                        )
                    },
                    border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_in_steam_button")
                ) {
                    Text(
                        text = "Öppna i Steam",
                        color = SteamNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = SteamNeon,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Target Price Slider & Input ("Bevaka pris")
            if (!game.isFreeToPlay && currentBestEur > 0.0) {
                Surface(
                    color = SteamBg.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SteamBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Välj målpris med reglaget",
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamTextSecondary
                            )
                            Text(
                                text = "${sliderValue.roundToInt()} ${currency.symbol}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = SteamNeon
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { newVal ->
                                targetPriceInput = newVal.roundToInt().toString()
                            },
                            valueRange = 0f..sliderMax,
                            colors = SliderDefaults.colors(
                                thumbColor = SteamNeon,
                                activeTrackColor = SteamNeon,
                                inactiveTrackColor = SteamBorder
                            ),
                            modifier = Modifier.testTag("target_price_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = targetPriceInput,
                                onValueChange = { targetPriceInput = it },
                                label = { Text("Målpris (${currency.code})") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SteamAccent,
                                    unfocusedBorderColor = SteamBorder,
                                    focusedContainerColor = SteamSurface,
                                    unfocusedContainerColor = SteamSurface,
                                    focusedTextColor = SteamTextPrimary,
                                    unfocusedTextColor = SteamTextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("target_price_input")
                            )
                            Button(
                                onClick = {
                                    val parsed = targetPriceInput.replace(",", ".").toDoubleOrNull()
                                    if (parsed != null && parsed >= 0.0) {
                                        onSavePriceAlert(parsed)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SteamNeon,
                                    contentColor = Color(0xFF020617)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_price_alert_button")
                            ) {
                                Text("Bevaka pris", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = { onSavePriceAlert(0.0) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamNeon,
                        contentColor = Color(0xFF020617)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("save_price_alert_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bevaka spel i Bevaka-fliken", fontWeight = FontWeight.ExtraBold)
                }
            }

            // Ask AI CTA
            OutlinedButton(
                onClick = onAskAi,
                border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = SteamAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fråga AI-rådgivaren om ${game.title}",
                    color = SteamAccent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteamBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SteamBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SteamTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
