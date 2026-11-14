package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamCard
import com.example.ui.theme.SteamDanger
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSale
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.viewmodel.ImageGenUiState
import com.example.viewmodel.ImageStudioViewModel
import com.example.viewmodel.StudioAspectRatio
import com.example.viewmodel.StudioImageModel
import com.example.viewmodel.StudioImageSize

@Composable
fun ImageStudioScreen(
    viewModel: ImageStudioViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var remixInput by rememberSaveable { mutableStateOf("") }
    val isLoading = state.genState is ImageGenUiState.Loading

    val presets = listOf(
        "Counter-Strike 2" to "Futuristic cyber-esports key art for Counter-Strike 2, dramatic cyan and orange rim lighting, tactical silhouettes, ultra detailed",
        "Monster Hunter Wilds" to "Epic storm dragon perched on obsidian cliffs in Monster Hunter Wilds, glowing lightning atmosphere, cinematic game cover art",
        "Forza Horizon 6" to "Hypercar drifting through neon-lit Tokyo mountain pass at night, rain reflections, Forza Horizon 6 key art style",
        "S.T.A.L.K.E.R. 2" to "Atmospheric stalker by a glowing campfire in misty Chernobyl exclusion zone ruins, eerie emerald anomaly light"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(SteamAccent, SteamSale))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = Color(0xFF020617)
                )
            }
            Column {
                Text(
                    text = "Spelkonst & Bildstudio",
                    style = MaterialTheme.typography.titleLarge,
                    color = SteamTextPrimary
                )
                Text(
                    text = "Skapa omslag & bakgrunder i 1K, 2K eller 4K med Gemini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteamTextSecondary
                )
            }
        }

        // Controls Card
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
                // Model selector
                Text(
                    text = "AI-Bildmodell",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamTextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    StudioImageModel.entries.forEach { model ->
                        val selected = state.selectedModel == model
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectModel(model) },
                            enabled = !isLoading,
                            label = { Text(model.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SteamAccent,
                                selectedLabelColor = Color(0xFF020617),
                                containerColor = SteamBg,
                                labelColor = SteamTextSecondary
                            )
                        )
                    }
                }

                // Required 1K / 2K / 4K Image Size Selector
                Text(
                    text = "Upplösning (Bildstorlek)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamTextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StudioImageSize.entries.forEach { sizeOption ->
                        val selected = state.selectedSize == sizeOption
                        Surface(
                            onClick = { viewModel.selectSize(sizeOption) },
                            enabled = !isLoading,
                            color = if (selected) SteamAccent else SteamBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) SteamAccent else SteamBorder),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("size_${sizeOption.label.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = sizeOption.label,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = if (selected) Color(0xFF020617) else SteamTextPrimary
                                )
                                Text(
                                    text = sizeOption.resolutionHint,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = if (selected) Color(0xFF0F172A) else SteamTextSecondary
                                )
                            }
                        }
                    }
                }

                // Aspect Ratio Selector
                Text(
                    text = "Bildformat",
                    style = MaterialTheme.typography.labelSmall,
                    color = SteamTextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    StudioAspectRatio.entries.forEach { ratio ->
                        val selected = state.selectedAspectRatio == ratio
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectAspectRatio(ratio) },
                            enabled = !isLoading,
                            label = {
                                Text(
                                    ratio.label,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SteamNeon.copy(alpha = 0.2f),
                                selectedLabelColor = SteamNeon,
                                containerColor = SteamBg,
                                labelColor = SteamTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = SteamBorder,
                                selectedBorderColor = SteamNeon
                            )
                        )
                    }
                }

                // Prompt Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (label, fullPrompt) ->
                        Surface(
                            onClick = { viewModel.updatePrompt(fullPrompt) },
                            enabled = !isLoading,
                            color = SteamCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SteamBorder)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    label = { Text("Beskriv din spelbild eller poster") },
                    placeholder = {
                        Text("T.ex. Cyberpunk sniper overlooking rainy neon city, 4K concept art")
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SteamAccent,
                        unfocusedBorderColor = SteamBorder,
                        focusedContainerColor = SteamBg,
                        unfocusedContainerColor = SteamBg,
                        focusedTextColor = SteamTextPrimary,
                        unfocusedTextColor = SteamTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input")
                )

                Button(
                    onClick = { viewModel.generateArtwork() },
                    enabled = state.prompt.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamAccent,
                        contentColor = Color(0xFF020617)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("generate_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generera ${state.selectedSize.label}-bild",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Result Canvas
        Card(
            colors = CardDefaults.cardColors(containerColor = SteamSurface),
            border = BorderStroke(1.dp, SteamBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val gen = state.genState) {
                    is ImageGenUiState.Idle -> {
                        Column(
                            modifier = Modifier.padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = SteamTextSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Välj 1K, 2K eller 4K och ange en prompt för att skapa spelkonst.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteamTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is ImageGenUiState.Loading -> {
                        Column(
                            modifier = Modifier.padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = SteamAccent)
                            Text(
                                text = "Renderar ${gen.size.label}-konstverk (${gen.size.resolutionHint})...",
                                style = MaterialTheme.typography.titleMedium,
                                color = SteamTextPrimary
                            )
                            Text(
                                text = "Högupplöst bildgenerering tar vanligtvis 30–60 sekunder.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteamTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is ImageGenUiState.Error -> {
                        Surface(
                            color = SteamDanger.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Fel vid bildgenerering: ${gen.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SteamDanger,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    is ImageGenUiState.Success -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${gen.artwork.model.label} • ${gen.artwork.size.label} (${gen.artwork.aspectRatio.label})",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = SteamNeon
                                )
                            }
                            Image(
                                bitmap = gen.artwork.bitmap.asImageBitmap(),
                                contentDescription = "Genererad spelkonst: ${gen.artwork.prompt}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                            )

                            // Image-to-Image Remix Box
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = remixInput,
                                    onValueChange = { remixInput = it },
                                    placeholder = { Text("Remixa bilden (t.ex. lägg till neonsnö)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SteamNeon,
                                        unfocusedBorderColor = SteamBorder,
                                        focusedContainerColor = SteamBg,
                                        unfocusedContainerColor = SteamBg,
                                        focusedTextColor = SteamTextPrimary,
                                        unfocusedTextColor = SteamTextPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.remixCurrentArtwork(remixInput)
                                        remixInput = ""
                                    },
                                    enabled = remixInput.isNotBlank() && !isLoading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SteamNeon,
                                        contentColor = Color(0xFF020617)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Remixa", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gallery strip
        if (state.gallery.isNotEmpty()) {
            Text(
                text = "Genererade bilder (${state.gallery.size})",
                style = MaterialTheme.typography.titleMedium,
                color = SteamTextPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp)
            ) {
                state.gallery.forEach { record ->
                    Image(
                        bitmap = record.bitmap.asImageBitmap(),
                        contentDescription = record.prompt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectFromGallery(record) }
                    )
                }
            }
        }
    }
}
