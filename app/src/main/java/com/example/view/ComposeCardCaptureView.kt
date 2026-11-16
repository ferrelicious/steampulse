package com.example.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
import com.example.model.SteamGame
import com.example.model.WorkshopModItem
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSale
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextMuted
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary

class ComposeCardCaptureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val composeView = ComposeView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(this)
    }

    private var currentGame by mutableStateOf<SteamGame?>(null)
    private var currentMods by mutableStateOf<List<WorkshopModItem>>(emptyList())
    private var showPlayers by mutableStateOf(true)
    private var showDeal by mutableStateOf(true)
    private var showMods by mutableStateOf(false)

    init {
        composeView.setContent {
            SteamPulseCardContent(
                game = currentGame,
                mods = currentMods,
                showPlayers = showPlayers,
                showDeal = showDeal,
                showMods = showMods
            )
        }
    }

    fun setCardData(
        game: SteamGame?,
        mods: List<WorkshopModItem>,
        showPlayers: Boolean,
        showDeal: Boolean,
        showMods: Boolean
    ) {
        this.currentGame = game
        this.currentMods = mods
        this.showPlayers = showPlayers
        this.showDeal = showDeal
        this.showMods = showMods
    }
}

@Composable
internal fun SteamPulseCardContent(
    game: SteamGame?,
    mods: List<WorkshopModItem>,
    showPlayers: Boolean,
    showDeal: Boolean,
    showMods: Boolean
) {
    val bestOfferPair = game?.let { SteamGamesCatalog.getBestOffer(it) }
    val bestOffer = bestOfferPair?.first
    val bestEur = bestOfferPair?.second ?: 0.0
    val formattedPrice = SteamGamesCatalog.formatPrice(bestEur, CurrencyMode.SEK)
    val discountPercent = game?.let { SteamGamesCatalog.getDiscountPercent(it) } ?: 0
    val headerUrl = game?.let { "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${it.appId}/header.jpg" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        if (!headerUrl.isNullOrBlank()) {
            AsyncImage(
                model = headerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF090D16)),
                            startY = 200f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = SteamNeon.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "STEAM-PULSE INTEL",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = SteamNeon,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (discountPercent > 0 && showDeal) {
                        Surface(
                            color = SteamSale,
                            shape = RoundedCornerShape(8.dp)
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

                Spacer(Modifier.height(8.dp))
                Text(
                    text = game?.title ?: "Steam Game",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = game?.developer ?: "Valve",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteamTextSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showPlayers && game != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface.copy(alpha = 0.9f)),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = SteamAccent, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Spelare nu", style = MaterialTheme.typography.labelSmall, color = SteamTextSecondary)
                                    Text(SteamGamesCatalog.formatNumber(game.playersNow), fontWeight = FontWeight.Bold, color = SteamTextPrimary, fontSize = 15.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("24h Peak", style = MaterialTheme.typography.labelSmall, color = SteamTextSecondary)
                                Text(SteamGamesCatalog.formatNumber(game.peak24h), fontWeight = FontWeight.Bold, color = SteamTextSecondary, fontSize = 14.sp)
                            }
                        }
                    }
                }

                if (showDeal && bestOffer != null) {
                    val isHistLow = bestEur <= (game?.historicalLowEur ?: 0.0) && bestEur > 0.0
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface.copy(alpha = 0.9f)),
                        border = BorderStroke(1.dp, SteamSale.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.LocalOffer, contentDescription = null, tint = SteamSale, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Bästa pris hos ${bestOffer.store}", style = MaterialTheme.typography.labelSmall, color = SteamTextSecondary)
                                    Text(formattedPrice, fontWeight = FontWeight.Black, color = SteamSale, fontSize = 16.sp)
                                }
                            }
                            Text(
                                text = if (isHistLow) "HISTORISKT LÄGSTA" else "REA",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = SteamSale
                            )
                        }
                    }
                }

                if (showMods && mods.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface.copy(alpha = 0.9f)),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Extension, contentDescription = null, tint = SteamNeon, modifier = Modifier.size(16.dp))
                                Text("Toppprenumererade Workshop-moddar", style = MaterialTheme.typography.labelMedium, color = SteamNeon, fontWeight = FontWeight.Bold)
                            }
                            mods.take(3).forEach { mod ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mod.title, style = MaterialTheme.typography.bodySmall, color = SteamTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${mod.subscriptionsCountFormatted} prenum.", fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = SteamTextMuted)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Genererad i SteamPulse",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = SteamTextMuted
                )
                Text(
                    text = "steampulse.app",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = SteamAccent
                )
            }
        }
    }
}
