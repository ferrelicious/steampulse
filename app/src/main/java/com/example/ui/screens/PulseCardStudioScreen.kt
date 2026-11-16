package com.example.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CurrencyMode
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamGame
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSale
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.view.ComposeCardCaptureView
import com.example.viewmodel.CardAspectRatio
import com.example.viewmodel.PulseCardStudioViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun PulseCardStudioScreen(
    viewModel: PulseCardStudioViewModel,
    pulsGames: List<SteamGame> = emptyList(),
    followedGames: List<FollowedWorkshopGame> = emptyList(),
    followedMods: List<FollowedWorkshopMod> = emptyList(),
    currency: CurrencyMode = CurrencyMode.SEK,
    onShowMessage: (String) -> Unit = {}
) {
    LaunchedEffect(pulsGames, followedGames, followedMods) {
        if (pulsGames.isNotEmpty() || followedGames.isNotEmpty()) {
            viewModel.syncGames(
                pulsGames = pulsGames,
                followedGames = followedGames,
                followedMods = followedMods
            )
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val game = state.selectedGame ?: state.games.firstOrNull()

    var cardViewInstance: ComposeCardCaptureView? by remember { mutableStateOf(null) }

    fun captureBitmap(): Bitmap? {
        val view = cardViewInstance ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            bitmap
        }.getOrNull()
    }

    fun saveToGallery(bitmap: Bitmap) {
        runCatching {
            val filename = "SteamPulse_Card_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SteamPulse")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(uri)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                onShowMessage("Bild sparad i galleri!")
            } ?: run {
                onShowMessage("Kunde inte spara bild")
            }
        }.onFailure {
            onShowMessage("Fel vid sparande: ${it.localizedMessage}")
        }
    }

    fun shareBitmap(bitmap: Bitmap) {
        runCatching {
            val cacheFile = File(context.cacheDir, "pulsecard_share_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Dela Pulse Card"))
        }.onFailure {
            onShowMessage("Kunde inte dela bild: ${it.localizedMessage}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(SteamAccent, SteamNeon))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Color(0xFF020617)
                )
            }
            Column {
                Text(
                    text = "Pulse Card Studio",
                    style = MaterialTheme.typography.titleLarge,
                    color = SteamTextPrimary
                )
                Text(
                    text = "Skapa deal- & modd-kort från Puls och Workshop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteamTextSecondary
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = SteamSurface),
            border = BorderStroke(1.dp, SteamBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Välj Spel (Puls & Följda)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamTextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    state.games.forEach { g ->
                        val selected = g.appId == game?.appId
                        val chipLabel = if (g.isFollowedWorkshop) "★ ${g.title}" else g.title
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectGame(g) },
                            label = { Text(chipLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SteamAccent,
                                selectedLabelColor = Color(0xFF020617),
                                containerColor = SteamBg,
                                labelColor = SteamTextSecondary
                            )
                        )
                    }
                }

                Text(
                    text = "Format",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamTextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CardAspectRatio.entries.forEach { ratio ->
                        val selected = state.aspectRatio == ratio
                        Surface(
                            onClick = { viewModel.setAspectRatio(ratio) },
                            color = if (selected) SteamNeon else SteamBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) SteamNeon else SteamBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("aspect_ratio_${ratio.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ratio.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (selected) Color(0xFF020617) else SteamTextPrimary
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Innehåll & Statistik",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamTextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.showPlayerCount,
                            onCheckedChange = { viewModel.togglePlayerCount(it) },
                            colors = CheckboxDefaults.colors(checkedColor = SteamAccent)
                        )
                        Text("Visa spelartal (Aktiva & 24h Peak)", color = SteamTextPrimary, fontSize = 13.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.showBestDeal,
                            onCheckedChange = { viewModel.toggleBestDeal(it) },
                            colors = CheckboxDefaults.colors(checkedColor = SteamSale)
                        )
                        Text("Visa bästa deal & rabatt", color = SteamTextPrimary, fontSize = 13.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.showModList,
                            onCheckedChange = { viewModel.toggleModList(it) },
                            colors = CheckboxDefaults.colors(checkedColor = SteamNeon)
                        )
                        Text("Visa toppprenumererade Workshop-moddar", color = SteamTextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }

        Text(
            text = "Kortförhandsvisning",
            style = MaterialTheme.typography.labelSmall,
            color = SteamTextSecondary
        )

        val aspectRatioFloat = if (state.aspectRatio == CardAspectRatio.PORTRAIT_9_16) 9f / 16f else 1f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatioFloat)
                .clip(RoundedCornerShape(20.dp))
                .background(SteamSurface)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ComposeCardCaptureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        cardViewInstance = this
                        setCardData(
                            gameOption = game,
                            mods = state.topMods,
                            currency = currency,
                            showPlayers = state.showPlayerCount,
                            showDeal = state.showBestDeal,
                            showMods = state.showModList,
                            isLoadingMods = state.isLoadingMods
                        )
                    }
                },
                update = { view ->
                    cardViewInstance = view
                    view.setCardData(
                        gameOption = game,
                        mods = state.topMods,
                        currency = currency,
                        showPlayers = state.showPlayerCount,
                        showDeal = state.showBestDeal,
                        showMods = state.showModList,
                        isLoadingMods = state.isLoadingMods
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    captureBitmap()?.let { shareBitmap(it) } ?: onShowMessage("Kunde inte generera bild")
                },
                colors = ButtonDefaults.buttonColors(containerColor = SteamAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("share_pulse_card_button")
            ) {
                Icon(imageVector = Icons.Rounded.Share, contentDescription = null, tint = Color(0xFF020617))
                Spacer(Modifier.width(8.dp))
                Text("Dela bild", color = Color(0xFF020617), fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    captureBitmap()?.let { saveToGallery(it) } ?: onShowMessage("Kunde inte generera bild")
                },
                border = BorderStroke(1.dp, SteamNeon),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_pulse_card_button")
            ) {
                Icon(imageVector = Icons.Rounded.Download, contentDescription = null, tint = SteamNeon)
                Spacer(Modifier.width(8.dp))
                Text("Spara i galleri", color = SteamNeon, fontWeight = FontWeight.Bold)
            }
        }
    }
}
