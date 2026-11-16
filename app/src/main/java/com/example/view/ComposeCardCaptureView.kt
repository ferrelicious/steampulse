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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
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
import com.example.viewmodel.PulseCardGameOption
import java.util.Locale

class ComposeCardCaptureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val composeView = ComposeView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private var currentGame by mutableStateOf<PulseCardGameOption?>(null)
    private var currentMods by mutableStateOf<List<WorkshopModItem>>(emptyList())
    private var currentCurrency by mutableStateOf(CurrencyMode.SEK)
    private var showPlayers by mutableStateOf(true)
    private var showDeal by mutableStateOf(true)
    private var showMods by mutableStateOf(false)
    private var isLoadingMods by mutableStateOf(false)

    init {
        addView(composeView)
        composeView.setContent {
            SteamPulseCardContent(
                gameOption = currentGame,
                mods = currentMods,
                currency = currentCurrency,
                showPlayers = showPlayers,
                showDeal = showDeal,
                showMods = showMods,
                isLoadingMods = isLoadingMods
            )
        }
    }

    fun setCardData(
        gameOption: PulseCardGameOption?,
        mods: List<WorkshopModItem>,
        currency: CurrencyMode,
        showPlayers: Boolean,
        showDeal: Boolean,
        showMods: Boolean,
        isLoadingMods: Boolean
    ) {
        this.currentGame = gameOption
        this.currentMods = mods
        this.currentCurrency = currency
        this.showPlayers = showPlayers
        this.showDeal = showDeal
        this.showMods = showMods
        this.isLoadingMods = isLoadingMods
    }
}

@Composable
internal fun SteamPulseCardContent(
    gameOption: PulseCardGameOption?,
    mods: List<WorkshopModItem>,
    currency: CurrencyMode,
    showPlayers: Boolean,
    showDeal: Boolean,
    showMods: Boolean,
    isLoadingMods: Boolean
) {
    val context = LocalContext.current
    val steamGame = gameOption?.steamGame
    val bestOfferPair = steamGame?.let { SteamGamesCatalog.getBestOffer(it) }
    val bestOffer = bestOfferPair?.first
    val bestEur = bestOfferPair?.second ?: 0.0
    val formattedPrice = SteamGamesCatalog.formatPrice(bestEur, currency)
    val discountPercent = steamGame?.let { SteamGamesCatalog.getDiscountPercent(it) } ?: 0
    val headerUrl = gameOption?.headerImageUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        if (!headerUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(headerUrl)
                    .allowHardware(false)
                    .crossfade(false)
                    .build(),
                contentDescription = gameOption.title,
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
                            listOf(Color.Black.copy(alpha = 0.25f), Color(0xFF090D16)),
                            startY = 120f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
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
                            text = if (gameOption?.isFollowedWorkshop == true) "WORKSHOP & PULS" else "STEAM-PULSE INTEL",
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

                Spacer(Modifier.height(6.dp))
                Text(
                    text = gameOption?.title ?: "Steam-spel",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = gameOption?.developer ?: "Steam",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showPlayers && steamGame != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface.copy(alpha = 0.92f)),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = SteamAccent, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Spelare nu", style = MaterialTheme.typography.labelSmall, color = SteamTextSecondary)
                                    Text(SteamGamesCatalog.formatNumber(steamGame.playersNow), fontWeight = FontWeight.Bold, color = SteamTextPrimary, fontSize = 14.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("24h Peak", style = MaterialTheme.typography.labelSmall, color = SteamTextSecondary)
                                Text(SteamGamesCatalog.formatNumber(steamGame.peak24h), fontWeight = FontWeight.Bold, color = SteamTextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (showDeal && steamGame != null && bestOffer != null) {
                    val isHistLow = bestEur <= steamGame.historicalLowEur && bestEur > 0.0
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface.copy(alpha = 0.92f)),
                        border = BorderStroke(1.dp, SteamSale.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.LocalOffer, contentDescription = null, tint = SteamSale, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Bästa pris hos ${bestOffer.store}", style = MaterialTheme.typography.labelSmall, color = SteamTextSecondary)
                                    Text(formattedPrice, fontWeight = FontWeight.Black, color = SteamSale, fontSize = 15.sp)
                                }
                            }
                            Text(
                                text = when {
                                    steamGame.isFreeToPlay && bestEur <= 0.0 -> "GRATIS"
                                    isHistLow -> "HISTORISKT LÄGSTA"
                                    discountPercent > 0 -> "-$discountPercent%"
                                    else -> "PRIS"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = SteamSale
                            )
                        }
                    }
                }

                if (showMods) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteamSurface.copy(alpha = 0.92f)),
                        border = BorderStroke(1.dp, SteamBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Extension, contentDescription = null, tint = SteamNeon, modifier = Modifier.size(15.dp))
                                Text("Toppprenumererade Workshop-moddar", style = MaterialTheme.typography.labelMedium, color = SteamNeon, fontWeight = FontWeight.Bold)
                            }
                            when {
                                isLoadingMods && mods.isEmpty() -> {
                                    Text(
                                        text = "Hämtar Workshop-moddar...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteamTextMuted
                                    )
                                }
                                mods.isEmpty() -> {
                                    Text(
                                        text = "Inga Workshop-moddar hittades för detta spel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteamTextMuted
                                    )
                                }
                                else -> {
                                    mods.take(3).forEach { mod ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = mod.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SteamTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            mod.subscriptions?.let { subs ->
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "${formatCompactCount(subs)} prenum.",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 10.sp,
                                                    color = SteamTextMuted
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SteamPulse",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = SteamTextMuted
                )
                Text(
                    text = "AppID ${gameOption?.appId ?: 0}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = SteamAccent
                )
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
