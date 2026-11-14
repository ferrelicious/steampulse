package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
import com.example.model.GameCategory
import com.example.model.GameEdition
import com.example.model.SortOrder
import com.example.model.SteamGame
import com.example.model.WatchlistItem
import com.example.ui.components.GameCardRow
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamAmber
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamCard
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSale
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.viewmodel.SteamPulseUiState
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
    onOpenGameDetail: (String) -> Unit,
    onCloseGameDetail: () -> Unit,
    onSelectEdition: (GameEdition) -> Unit,
    onToggleWatchlist: (SteamGame, Boolean) -> Unit,
    onSavePriceAlert: (SteamGame, Double) -> Unit,
    onAskAiAboutGame: (SteamGame) -> Unit
) {
    val watchedIds = remember(watchlistItems) { watchlistItems.map { it.id }.toSet() }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Platform Telemetry Banner
        item(key = "telemetry_banner") {
            Card(
                colors = CardDefaults.cardColors(containerColor = SteamSurface),
                border = BorderStroke(1.dp, SteamBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(SteamNeon)
                        )
                        Column {
                            Text(
                                text = "STEAM ONLINE NU",
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamTextSecondary
                            )
                            Text(
                                text = SteamGamesCatalog.formatNumber(uiState.globalOnline),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SteamTextPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(SteamBorder)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "I SPEL JUST NU",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary
                        )
                        Text(
                            text = SteamGamesCatalog.formatNumber(uiState.globalInGame),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SteamNeon
                        )
                    }
                }
            }
        }

        // Highlight Cards Strip
        item(key = "hero_highlights") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val cs2 = uiState.games.firstOrNull { it.id == "cs2" }
                HeroHighlightCard(
                    eyebrow = "MEST SPELADE IDAG",
                    title = "Counter-Strike 2",
                    subtitle = "${SteamGamesCatalog.formatNumber(cs2?.playersNow ?: 1482910)} aktiva spelare",
                    accent = SteamAccent,
                    icon = Icons.Rounded.NotificationsActive,
                    onClick = { onOpenGameDetail("cs2") }
                )
                HeroHighlightCard(
                    eyebrow = "HETASTE RELEASES 2026",
                    title = "Phantom Blade Zero",
                    subtitle = "Jämför nycklar upp till -36%",
                    accent = SteamNeon,
                    icon = Icons.Rounded.LocalFireDepartment,
                    onClick = { onOpenGameDetail("phantom-blade-zero") }
                )
                HeroHighlightCard(
                    eyebrow = "PRISGARANTI & TRANSPARENS",
                    title = "Officiella & Keyshops",
                    subtitle = "Steam, CDKeys, Eneba, G2A & Fanatical",
                    accent = SteamSale,
                    icon = Icons.Rounded.Shield,
                    onClick = { onCategorySelect(GameCategory.DEALS) }
                )
            }
        }

        // Search, Sort & Category Filter Controls
        item(key = "search_and_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            "Sök speltitel (t.ex. CS2, Monster Hunter, Rust)...",
                            color = SteamTextSecondary,
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
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchChange("") },
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

                // Sort Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.games.size} spel visas",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamTextSecondary
                    )

                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = "Sortera: ${uiState.sortOrder.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamTextPrimary
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

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameCategory.entries.forEach { category ->
                        val selected = uiState.selectedCategory == category
                        val isDeals = category == GameCategory.DEALS
                        FilterChip(
                            selected = selected,
                            onClick = { onCategorySelect(category) },
                            leadingIcon = if (isDeals) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalOffer,
                                        contentDescription = null,
                                        tint = if (selected) Color(0xFF020617) else SteamSale,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else null,
                            label = {
                                Text(
                                    text = category.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isDeals) SteamSale else SteamAccent,
                                selectedLabelColor = Color(0xFF020617),
                                containerColor = SteamSurface,
                                labelColor = if (isDeals) SteamSale else SteamTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = if (isDeals) SteamSale.copy(alpha = 0.4f) else SteamBorder
                            )
                        )
                    }
                }
            }
        }

        // Empty State or Games List
        if (uiState.games.isEmpty()) {
            item(key = "empty_state") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteamSurface),
                    border = BorderStroke(1.dp, SteamBorder),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = SteamTextSecondary,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = "Inga spel matchade din sökning",
                            style = MaterialTheme.typography.titleMedium,
                            color = SteamTextPrimary
                        )
                        Text(
                            text = "Prova att ändra dina filter eller söka på en annan speltitel.",
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
                            Text("Återställ filter", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(uiState.games, key = { it.id }) { game ->
                GameCardRow(
                    game = game,
                    currency = uiState.currency,
                    isWatched = watchedIds.contains(game.id),
                    onClick = { onOpenGameDetail(game.id) }
                )
            }
        }
    }

    // Detail & Price Comparison Bottom Sheet
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
            }
        )
    }
}

@Composable
private fun HeroHighlightCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SteamCard),
        border = BorderStroke(1.dp, SteamBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(248.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SteamTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteamTextSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onAskAi: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(game.gradientStart, game.gradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = game.shortCode,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${game.rank} på Steam • ${game.genres.joinToString(" • ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamAccent
                    )
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = SteamTextPrimary
                    )
                    Text(
                        text = "Utvecklare: ${game.developer} • AppID: ${game.appId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamTextSecondary
                    )
                }

                IconButton(
                    onClick = onToggleWatchlist,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("modal_watchlist_toggle")
                ) {
                    Icon(
                        imageVector = if (existingWatch != null) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                        contentDescription = if (existingWatch != null) "Ta bort från bevakning" else "Lägg till i bevakning",
                        tint = if (existingWatch != null) SteamAmber else SteamTextSecondary
                    )
                }
            }

            // 4-Cell Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatBox(
                    label = "Spelare Nu",
                    value = SteamGamesCatalog.formatNumber(game.playersNow),
                    valueColor = SteamNeon,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatBox(
                    label = "24h Peak",
                    value = SteamGamesCatalog.formatNumber(game.peak24h),
                    valueColor = SteamTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatBox(
                    label = "All-Time Peak",
                    value = SteamGamesCatalog.formatNumber(game.allTimePeak),
                    valueColor = SteamAccent,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatBox(
                    label = "Lägsta någonsin",
                    value = if (game.historicalLowEur <= 0.0) "Gratis" else SteamGamesCatalog.formatPrice(game.historicalLowEur, currency),
                    valueColor = SteamSale,
                    modifier = Modifier.weight(1f)
                )
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
                    text = "Fråga Gemini AI om bästa deal för ${game.shortCode}",
                    color = SteamAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            // Edition Switcher + Target Price Alert
            if (game.steamPriceEur > 0.0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GameEdition.entries.forEach { edition ->
                        val selected = selectedEdition == edition
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectEdition(edition) },
                            label = {
                                Text(edition.label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SteamAccent,
                                selectedLabelColor = Color(0xFF020617),
                                containerColor = SteamBg,
                                labelColor = SteamTextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

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
                            focusedContainerColor = SteamBg,
                            unfocusedContainerColor = SteamBg,
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

            // Retailers List
            Text(
                text = "PRISJÄMFÖRELSE MELLAN BUTIKER",
                style = MaterialTheme.typography.labelSmall,
                color = SteamTextSecondary
            )

            val mult = if (selectedEdition == GameEdition.DELUXE) game.deluxeMultiplier else 1.0
            val steamBaseEur = game.steamPriceEur * mult
            val sortedOffers = game.offers
                .map { it to (it.priceEur * mult) }
                .sortedBy { it.second }

            sortedOffers.forEachIndexed { index, (offer, finalPriceEur) ->
                val isBest = index == 0 && finalPriceEur > 0.0
                val discount = if (steamBaseEur > 0.0) {
                    (((steamBaseEur - finalPriceEur) / steamBaseEur) * 100.0).roundToInt().coerceAtLeast(0)
                } else 0

                Surface(
                    color = if (isBest) SteamNeon.copy(alpha = 0.09f) else SteamBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isBest) SteamNeon.copy(alpha = 0.45f) else SteamBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = offer.store,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SteamTextPrimary
                                )
                                if (isBest) {
                                    Surface(
                                        color = SteamNeon,
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(
                                            text = "BÄSTA",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF020617),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = offer.typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (offer.isOfficial) SteamAccent else SteamAmber
                            )
                            Text(
                                text = "${offer.region} • ${offer.platform}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamTextSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (discount > 0) {
                                    Surface(
                                        color = SteamSale,
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(
                                            text = "-$discount%",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            color = Color(0xFF020617),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = SteamGamesCatalog.formatPrice(finalPriceEur, currency),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = SteamTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    val url = SteamGamesCatalog.buildStoreUrl(offer.urlType, game)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBest) SteamNeon else SteamAccent,
                                    contentColor = Color(0xFF020617)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Butik", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // External Trackers
            Text(
                text = "PRISHISTORIK & STATISTIK",
                style = MaterialTheme.typography.labelSmall,
                color = SteamTextSecondary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                val trackers = listOf(
                    "SteamDB" to SteamGamesCatalog.buildSteamDbUrl(game),
                    "GG.deals" to SteamGamesCatalog.buildGgDealsUrl(game),
                    "IsThereAnyDeal" to SteamGamesCatalog.buildItadUrl(game)
                )
                trackers.forEach { (name, url) ->
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(name, color = SteamAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = SteamAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
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
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SteamTextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = valueColor
            )
        }
    }
}
