package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SteamGamesCatalog
import com.example.model.CurrencyMode
import com.example.model.SteamGame
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamAmber
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSale
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary

@Composable
fun PlayerSparklineChart(
    dataPoints: List<Int>,
    modifier: Modifier = Modifier
) {
    val isUp = remember(dataPoints) {
        dataPoints.isNotEmpty() && dataPoints.last() >= dataPoints.first()
    }
    val strokeColor = if (isUp) SteamNeon else SteamAccent

    Canvas(modifier = modifier) {
        if (dataPoints.size < 2) return@Canvas
        val minVal = dataPoints.minOrNull()?.toFloat() ?: 0f
        val maxVal = dataPoints.maxOrNull()?.toFloat() ?: 1f
        val range = (maxVal - minVal).takeIf { it > 0f } ?: 1f
        val pad = 4.dp.toPx()
        val usableW = size.width - pad * 2
        val usableH = size.height - pad * 2

        val points = dataPoints.mapIndexed { idx, value ->
            val x = pad + (idx.toFloat() / (dataPoints.size - 1)) * usableW
            val y = size.height - pad - ((value - minVal) / range) * usableH
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        val areaPath = Path().apply {
            moveTo(points.first().x, size.height - pad)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height - pad)
            close()
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(strokeColor.copy(alpha = 0.34f), Color.Transparent),
                startY = pad,
                endY = size.height - pad
            )
        )

        drawPath(
            path = linePath,
            color = strokeColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawCircle(
            color = strokeColor,
            radius = 3.dp.toPx(),
            center = points.last()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameCardRow(
    game: SteamGame,
    currency: CurrencyMode,
    isWatched: Boolean,
    onClick: () -> Unit
) {
    val (bestOffer, bestPriceEur) = remember(game) { SteamGamesCatalog.getBestOffer(game) }
    val discount = remember(game) { SteamGamesCatalog.getDiscountPercent(game) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(1.dp, SteamBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("game_card_${game.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "#${game.rank}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (game.rank <= 3) SteamAccent else SteamTextSecondary
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Brush.linearGradient(listOf(game.gradientStart, game.gradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = game.shortCode,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = SteamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isWatched) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Bevakad",
                                tint = SteamAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        game.genres.forEach { genre ->
                            Surface(
                                color = SteamBg,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, SteamBorder)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SteamTextSecondary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                PlayerSparklineChart(
                    dataPoints = game.trend24h,
                    modifier = Modifier
                        .width(72.dp)
                        .height(34.dp)
                )
            }

            Surface(
                color = SteamBg.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SteamBorder.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Spelare Nu",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SteamNeon)
                            )
                            Text(
                                text = SteamGamesCatalog.formatNumber(game.playersNow),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = SteamNeon
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "24h Peak",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary
                        )
                        Text(
                            text = SteamGamesCatalog.formatNumber(game.peak24h),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = SteamTextPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "All-Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary
                        )
                        Text(
                            text = SteamGamesCatalog.formatNumber(game.allTimePeak),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            color = SteamTextSecondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (game.steamPriceEur <= 0.0) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            color = SteamNeon.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "Gratis att spela",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = SteamNeon,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Officiell Steam-titel",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (discount > 0) {
                                Surface(
                                    color = SteamSale,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "-$discount%",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = Color(0xFF020617),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = SteamGamesCatalog.formatPrice(game.steamPriceEur, currency),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = SteamTextSecondary,
                                textDecoration = TextDecoration.LineThrough
                            )
                            Text(
                                text = SteamGamesCatalog.formatPrice(bestPriceEur, currency),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = SteamTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Bäst hos ${bestOffer.store.replace(" (Officiell)", "")}" +
                                (game.editionNote?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = SteamAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Jämför",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            color = SteamAccent
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SteamAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
