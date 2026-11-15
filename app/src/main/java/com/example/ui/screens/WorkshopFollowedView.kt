package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.FollowedFeedModItem
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamSyncUiState
import com.example.model.WorkshopFollowSource
import com.example.model.WorkshopFollowedFeedUiState
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopModItem
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
import com.example.viewmodel.WorkshopScreenUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WorkshopFollowedView(
    uiState: WorkshopScreenUiState,
    onOpenGameWorkshop: (WorkshopGameSummary) -> Unit,
    onToggleFollowGame: (WorkshopGameSummary) -> Unit,
    onRefreshFollowedFeed: () -> Unit,
    onOpenModDetail: (WorkshopModItem) -> Unit,
    onToggleBookmarkMod: (WorkshopModItem, String?) -> Unit,
    onRemoveFollowedMod: (String) -> Unit,
    onOpenSteamSyncDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Steam Subscribed Mods Sync Banner
        item(key = "steam_sync_banner") {
            SteamSyncStatusCard(
                uiState = uiState,
                onOpenSteamSyncDialog = onOpenSteamSyncDialog
            )
        }

        // 2. Followed Games & New Mods Feed Header
        item(key = "followed_games_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NYA MODDAR I FÖLJDA SPEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamAccent
                    )
                    Text(
                        text = "Senaste släppen från dina favoritspel (${uiState.followedGames.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = SteamTextSecondary
                    )
                }
                IconButton(
                    onClick = onRefreshFollowedFeed,
                    enabled = uiState.followedGames.isNotEmpty() &&
                        uiState.followedNewModsFeed !is WorkshopFollowedFeedUiState.Loading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_followed_feed_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Uppdatera följda moddar",
                        tint = SteamAccent
                    )
                }
            }
        }

        // Followed games quick chips
        if (uiState.followedGames.isNotEmpty()) {
            item(key = "followed_games_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.followedGames.forEach { followedGame ->
                        val summary = followedGame.toGameSummary()
                        Surface(
                            onClick = { onOpenGameWorkshop(summary) },
                            color = SteamSurface,
                            border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("followed_game_chip_${followedGame.appId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsEsports,
                                    contentDescription = null,
                                    tint = SteamAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = followedGame.gameName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SteamTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { onToggleFollowGame(summary) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Sluta följa ${followedGame.gameName}",
                                        tint = SteamTextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // New mods feed content
        if (uiState.followedGames.isEmpty()) {
            item(key = "followed_games_empty") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteamSurface),
                    border = BorderStroke(1.dp, SteamBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Inga följda spel ännu",
                            style = MaterialTheme.typography.titleSmall,
                            color = SteamTextPrimary
                        )
                        Text(
                            text = "Öppna valfritt spel under Utforska (t.ex. S.T.A.L.K.E.R. 2 eller RimWorld) och tryck på \"Följ spel\" för att se nya moddar här direkt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SteamTextSecondary
                        )
                    }
                }
            }
        } else {
            when (val feedState = uiState.followedNewModsFeed) {
                WorkshopFollowedFeedUiState.Idle,
                WorkshopFollowedFeedUiState.Loading -> {
                    item(key = "followed_feed_loading") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteamSurface),
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = SteamAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Hämtar nyaste moddar från dina följda spel...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SteamTextSecondary
                                )
                            }
                        }
                    }
                }

                is WorkshopFollowedFeedUiState.Error -> {
                    item(key = "followed_feed_error") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteamSurface),
                            border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = feedState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SteamDanger
                                )
                                OutlinedButton(
                                    onClick = onRefreshFollowedFeed,
                                    border = BorderStroke(1.dp, SteamBorder),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Försök igen", color = SteamTextPrimary)
                                }
                            }
                        }
                    }
                }

                is WorkshopFollowedFeedUiState.Success -> {
                    if (feedState.mods.isEmpty()) {
                        item(key = "followed_feed_empty_mods") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SteamSurface),
                                border = BorderStroke(1.dp, SteamBorder),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Inga publika Workshop-moddar hittades ännu för dina följda spel.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SteamTextSecondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = feedState.mods,
                            key = { "feed_${it.mod.appId}_${it.mod.publishedFileId}" }
                        ) { feedItem ->
                            FollowedFeedModCard(
                                item = feedItem,
                                isBookmarked = uiState.bookmarkedModIds.contains(feedItem.mod.publishedFileId),
                                onClick = { onOpenModDetail(feedItem.mod) },
                                onToggleBookmark = {
                                    onToggleBookmarkMod(feedItem.mod, feedItem.gameName)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Bookmarked & Steam-Subscribed Mods Section
        item(key = "saved_mods_header") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "FÖLJDA & PRENUMERERADE MODDAR (${uiState.followedMods.size})",
                style = MaterialTheme.typography.labelSmall,
                color = SteamNeon
            )
        }

        if (uiState.followedMods.isEmpty()) {
            item(key = "saved_mods_empty") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SteamSurface),
                    border = BorderStroke(1.dp, SteamBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Inga bokmärkta eller synkade moddar än",
                            style = MaterialTheme.typography.titleSmall,
                            color = SteamTextPrimary
                        )
                        Text(
                            text = "Tryck på hjärtat på valfri modd eller synka dina Steam-prenumerationer ovan för att samla alla dina moddar här.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SteamTextSecondary
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.followedMods,
                key = { "saved_${it.publishedFileId}" }
            ) { savedMod ->
                FollowedSavedModCard(
                    savedMod = savedMod,
                    onClick = { onOpenModDetail(savedMod.toModItem()) },
                    onToggleBookmark = {
                        onToggleBookmarkMod(savedMod.toModItem(), savedMod.gameName)
                    },
                    onRemove = { onRemoveFollowedMod(savedMod.publishedFileId) }
                )
            }
        }
    }
}

@Composable
private fun SteamSyncStatusCard(
    uiState: WorkshopScreenUiState,
    onOpenSteamSyncDialog: () -> Unit
) {
    val profile = uiState.syncProfile
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
                            SteamNeon.copy(alpha = 0.12f),
                            SteamAccent.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = SteamNeon
                )
                Text(
                    text = "STEAM-PRENUMERATIONER",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = SteamNeon
                )
            }

            if (profile != null) {
                Text(
                    text = "Kopplad till SteamID ${profile.steamId64} • ${profile.syncedCount} synkade moddar",
                    style = MaterialTheme.typography.titleSmall,
                    color = SteamTextPrimary
                )
                Text(
                    text = "Uppdateras från din publika Steam-prenumerationslista och sparas i ditt SteamPulse-konto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary
                )
            } else {
                Text(
                    text = "Importera moddar du prenumererar på i Steam",
                    style = MaterialTheme.typography.titleSmall,
                    color = SteamTextPrimary
                )
                Text(
                    text = "Ange ditt SteamID64 eller din Steam-profil-URL så hämtar vi alla dina prenumererade moddar automatiskt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary
                )
            }

            if (uiState.steamSyncState is SteamSyncUiState.EmptyDiagnostic) {
                val diag = uiState.steamSyncState
                Surface(
                    color = SteamAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SteamAmber.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = diag.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = SteamAmber
                        )
                        Text(
                            text = "HTTP ${diag.httpStatusCode} • ${diag.queriedUrl}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = SteamTextMuted
                        )
                    }
                }
            }

            Button(
                onClick = onOpenSteamSyncDialog,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SteamNeon,
                    contentColor = Color(0xFF020617)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("open_steam_sync_dialog_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Synka Steam-prenumerationer",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowedFeedModCard(
    item: FollowedFeedModItem,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val mod = item.mod
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(1.dp, SteamBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("followed_feed_mod_${mod.publishedFileId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModThumbnailBox(previewUrl = mod.previewUrl, contentDescription = mod.title)

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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = SteamAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.gameName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SteamAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    mod.updatedEpochSec?.takeIf { it > 0L }?.let { epoch ->
                        Text(
                            text = formatWorkshopEpochDate(epoch),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = SteamTextMuted
                        )
                    }
                }
                Text(
                    text = "av ${mod.author.ifBlank { "Steam-skapare" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isBookmarked) "Ta bort bokmärke" else "Bokmärk modd",
                    tint = if (isBookmarked) SteamNeon else SteamTextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowedSavedModCard(
    savedMod: FollowedWorkshopMod,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRemove: () -> Unit
) {
    val isBookmarked = savedMod.source == WorkshopFollowSource.BOOKMARK ||
        savedMod.source == WorkshopFollowSource.BOTH
    val sourceLabel = when (savedMod.source) {
        WorkshopFollowSource.BOTH -> "Bokmärkt • Steam"
        WorkshopFollowSource.STEAM_SYNC -> "Steam-prenumeration"
        else -> "Bokmärkt"
    }
    val badgeColor = when (savedMod.source) {
        WorkshopFollowSource.BOTH, WorkshopFollowSource.STEAM_SYNC -> SteamNeon
        else -> SteamAccent
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(1.dp, SteamBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_followed_mod_${savedMod.publishedFileId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModThumbnailBox(previewUrl = savedMod.previewUrl, contentDescription = savedMod.title)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = savedMod.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SteamTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (savedMod.gameName.isNotBlank()) {
                        Surface(
                            color = SteamCard,
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = savedMod.gameName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = SteamTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = sourceLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val updatedText = when {
                    savedMod.updatedEpochSec > 0L -> "Uppdaterad ${formatWorkshopEpochDate(savedMod.updatedEpochSec)}"
                    savedMod.updatedAt != null -> "Sparad ${formatWorkshopEpochDate(savedMod.updatedAt.seconds)}"
                    else -> null
                }
                if (updatedText != null) {
                    Text(
                        text = updatedText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = SteamSale
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isBookmarked) "Ta bort bokmärke" else "Bokmärk modd",
                        tint = if (isBookmarked) SteamNeon else SteamTextSecondary
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Ta bort modd",
                        tint = SteamTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ModThumbnailBox(
    previewUrl: String,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SteamCard)
    ) {
        if (previewUrl.isNotBlank()) {
            AsyncImage(
                model = previewUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Extension,
                contentDescription = contentDescription,
                tint = SteamAccent,
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
internal fun SteamSyncSubscriptionsDialog(
    input: String,
    syncState: SteamSyncUiState,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmSync: () -> Unit
) {
    val isSyncing = syncState is SteamSyncUiState.Syncing

    AlertDialog(
        onDismissRequest = { if (!isSyncing) onDismiss() },
        containerColor = SteamSurface,
        titleContentColor = SteamTextPrimary,
        textContentColor = SteamTextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = SteamNeon
                )
                Text(
                    text = "Synka Steam-prenumerationer",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ange ditt 17-siffriga SteamID64 eller din Steam-profil-URL (t.ex. steamcommunity.com/id/dittnamn eller .../profiles/7656119...).",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !isSyncing,
                    label = { Text("SteamID64 eller Profil-URL") },
                    placeholder = {
                        Text(
                            text = "7656119... eller steamcommunity.com/id/...",
                            color = SteamTextMuted,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SteamNeon,
                        unfocusedBorderColor = SteamBorder,
                        focusedTextColor = SteamTextPrimary,
                        unfocusedTextColor = SteamTextPrimary,
                        focusedLabelColor = SteamNeon,
                        unfocusedLabelColor = SteamTextSecondary,
                        cursorColor = SteamNeon
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("steam_sync_profile_input")
                )
                Text(
                    text = "Obs: Din Steam-profil och \"Speldetaljer\" (Game Details) måste vara inställda på Offentlig i Steams sekretessinställningar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamTextMuted
                )

                when (syncState) {
                    is SteamSyncUiState.Error -> {
                        Surface(
                            color = SteamDanger.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = SteamDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = syncState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SteamDanger,
                                    modifier = Modifier.testTag("steam_sync_error_message")
                                )
                            }
                        }
                    }

                    is SteamSyncUiState.EmptyDiagnostic -> {
                        Surface(
                            color = SteamAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, SteamAmber.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .testTag("steam_sync_diagnostic_message"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = SteamAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = syncState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteamAmber
                                    )
                                }
                                Text(
                                    text = "URL: ${syncState.queriedUrl}\nStatus: HTTP ${syncState.httpStatusCode} • 0 träffar",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = SteamTextSecondary
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSync,
                enabled = !isSyncing && input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SteamNeon,
                    contentColor = Color(0xFF020617)
                ),
                modifier = Modifier.testTag("confirm_steam_sync_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = Color(0xFF020617),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Synkar...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Synka nu", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSyncing
            ) {
                Text("Avbryt", color = SteamTextSecondary)
            }
        }
    )
}

internal fun formatWorkshopEpochDate(epochSec: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.forLanguageTag("sv-SE"))
    return sdf.format(Date(epochSec * 1000L))
}
