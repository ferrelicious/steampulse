package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
import com.example.model.SteamGame
import com.example.model.WatchlistItem
import com.example.ui.components.SteamGameArtBadge
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
import com.example.viewmodel.SteamPulseViewModel
import com.example.viewmodel.WatchlistUiState
import kotlin.math.roundToInt

@Composable
fun WatchlistScreen(
    watchlistState: WatchlistUiState,
    games: List<SteamGame>,
    currency: CurrencyMode,
    onOpenGame: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onUpdateTargetPrice: (SteamGame, Double) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SteamAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = SteamAmber
                )
            }
            Column {
                Text(
                    text = "Min Bevakningslista",
                    style = MaterialTheme.typography.titleLarge,
                    color = SteamTextPrimary
                )
                Text(
                    text = "Sparade målpriser & prisvarningar (${currency.code})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteamTextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        when (watchlistState) {
            is WatchlistUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SteamAccent)
                }
            }
            is WatchlistUiState.Error -> {
                Surface(
                    color = SteamDanger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = watchlistState.message,
                        color = SteamDanger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is WatchlistUiState.Success -> {
                if (watchlistState.items.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(20.dp),
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
                                imageVector = Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = SteamTextSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Din bevakningslista är tom",
                                style = MaterialTheme.typography.titleMedium,
                                color = SteamTextPrimary
                            )
                            Text(
                                text = "Tryck på valfritt spel i Puls för att sätta ett målpris och bevaka rabatter hos Steam, CDKeys, Eneba och G2A.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteamTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(watchlistState.items, key = { it.id }) { item ->
                            val itemAppId = SteamPulseViewModel.extractAppIdFromWatchlistId(item.id)
                            val game = games.firstOrNull { it.id == item.id }
                                ?: itemAppId?.let { appId -> games.firstOrNull { it.appId == appId } }
                            WatchlistItemCard(
                                item = item,
                                game = game,
                                currency = currency,
                                onOpenGame = { onOpenGame(item.id) },
                                onRemove = { onRemoveItem(item.id) },
                                onSaveTargetPrice = { parsed ->
                                    if (game != null) {
                                        onUpdateTargetPrice(game, parsed)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistItemCard(
    item: WatchlistItem,
    game: SteamGame?,
    currency: CurrencyMode,
    onOpenGame: () -> Unit,
    onRemove: () -> Unit,
    onSaveTargetPrice: (Double) -> Unit
) {
    val bestOffer = game?.let { SteamGamesCatalog.getBestOffer(it) }
    val bestPriceEur = bestOffer?.second ?: Double.MAX_VALUE
    val discount = game?.let { SteamGamesCatalog.getDiscountPercent(it) } ?: 0

    val bestInDisplay = game?.let {
        val raw = SteamGamesCatalog.getNumericPrice(bestPriceEur, currency)
        if (currency == CurrencyMode.SEK) raw.roundToInt().toDouble() else raw
    } ?: Double.MAX_VALUE
    val targetInDisplay = SteamGamesCatalog.getNumericPrice(item.targetPriceEur, currency).let {
        if (currency == CurrencyMode.SEK) it.roundToInt().toDouble() else it
    }
    val targetReached = game != null && (
        bestPriceEur <= item.targetPriceEur + 0.005 ||
            bestInDisplay <= targetInDisplay + 0.005
        )

    val formattedTargetNumeric = remember(item.targetPriceEur, currency) {
        val num = SteamGamesCatalog.getNumericPrice(item.targetPriceEur, currency)
        if (currency == CurrencyMode.SEK) num.roundToInt().toString() else num.toString()
    }
    var targetInput by remember(item.id, item.targetPriceEur, currency) {
        mutableStateOf(formattedTargetNumeric)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(
            1.dp,
            if (targetReached) SteamNeon.copy(alpha = 0.6f) else SteamBorder
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("watchlist_item_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SteamGameArtBadge(
                    appId = game?.appId ?: SteamPulseViewModel.extractAppIdFromWatchlistId(item.id) ?: 0,
                    title = item.gameTitle,
                    shortCode = game?.shortCode ?: item.id.take(4).uppercase(),
                    gradientStart = game?.gradientStart ?: SteamCard,
                    gradientEnd = game?.gradientEnd ?: SteamBorder,
                    width = 64.dp,
                    height = 40.dp,
                    cornerRadius = 10.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.gameTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = SteamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (targetReached) {
                            Surface(
                                color = SteamNeon,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("target_reached_badge_${item.id}")
                            ) {
                                Text(
                                    text = "Målpris uppnått!",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF020617),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (discount > 0) {
                            Surface(
                                color = SteamSale,
                                shape = RoundedCornerShape(5.dp),
                                modifier = Modifier.testTag("watchlist_discount_badge_${item.id}")
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
                        if (game != null && game.steamPriceEur > 0.0 && discount > 0) {
                            Text(
                                text = SteamGamesCatalog.formatPrice(game.steamPriceEur, currency),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = SteamTextSecondary,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        val bestLabel = if (bestOffer != null) {
                            if (bestOffer.second <= 0.0) "Gratis"
                            else "${SteamGamesCatalog.formatPrice(bestOffer.second, currency)} (${bestOffer.first.store.replace(" (Officiell)", "")})"
                        } else {
                            "—"
                        }
                        Text(
                            text = "Lägst nu: $bestLabel",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (targetReached) SteamNeon else SteamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (game != null && game.steamPriceEur > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
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
                            .testTag("watchlist_target_input_${item.id}")
                    )
                    Button(
                        onClick = {
                            val parsed = targetInput.replace(",", ".").toDoubleOrNull()
                            if (parsed != null && parsed >= 0.0) {
                                onSaveTargetPrice(parsed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SteamNeon,
                            contentColor = Color(0xFF020617)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("watchlist_save_target_${item.id}")
                    ) {
                        Text("Spara", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenGame,
                    enabled = game != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamAccent.copy(alpha = 0.16f),
                        contentColor = SteamAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Visa priser", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("remove_watchlist_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Ta bort från bevakningslista",
                        tint = SteamDanger
                    )
                }
            }
        }
    }
}
