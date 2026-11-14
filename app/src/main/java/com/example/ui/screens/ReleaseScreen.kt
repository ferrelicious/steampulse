package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GitHubRelease
import com.example.model.GitHubReleaseAsset
import com.example.model.ReleaseUiState
import com.example.model.VersionComparisonStatus
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
import com.example.viewmodel.ReleaseScreenState
import java.util.Locale

@Composable
fun ReleaseScreen(
    uiState: ReleaseScreenState,
    onRepoInputChange: (String) -> Unit,
    onTogglePrereleases: (Boolean) -> Unit,
    onSaveAndCheck: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showWorkflowGuide by remember(uiState.savedRepoSlug) {
        mutableStateOf(uiState.savedRepoSlug.isBlank())
    }

    BackHandler(onBack = onClose)

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
        // Sub-screen Header Row
        Surface(
            color = SteamSurface,
            border = BorderStroke(1.dp, SteamBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("release_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Tillbaka",
                            tint = SteamTextPrimary
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.release_page_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = SteamTextPrimary
                        )
                        Text(
                            text = "GitHub Releases & App-uppdatering",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamAccent
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = uiState.releaseState !is ReleaseUiState.Loading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("release_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Uppdatera releaser",
                        tint = if (uiState.releaseState is ReleaseUiState.Loading) SteamTextMuted else SteamAccent
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Installed Version Telemetry Card
            item(key = "installed_version_card") {
                InstalledBuildCard(
                    uiState = uiState,
                    onRefresh = onRefresh
                )
            }

            // 2. Highlighted Update Banner when a newer GitHub Release exists
            val successState = uiState.releaseState as? ReleaseUiState.Success
            if (successState != null &&
                successState.result.versionStatus == VersionComparisonStatus.UPDATE_AVAILABLE &&
                successState.result.latestRelease != null
            ) {
                item(key = "update_available_banner") {
                    UpdateAvailableBanner(
                        latestRelease = successState.result.latestRelease,
                        onOpenReleaseUrl = openUrl
                    )
                }
            }

            // 3. GitHub Repository Configuration Card
            item(key = "repo_config_card") {
                GitHubRepoConfigCard(
                    uiState = uiState,
                    onRepoInputChange = onRepoInputChange,
                    onTogglePrereleases = onTogglePrereleases,
                    onSaveAndCheck = onSaveAndCheck,
                    showWorkflowGuide = showWorkflowGuide,
                    onToggleGuide = { showWorkflowGuide = !showWorkflowGuide }
                )
            }

            // 4. Workflow / Publish Guide (collapsible)
            if (showWorkflowGuide) {
                item(key = "workflow_guide_card") {
                    GitHubWorkflowGuideCard(
                        savedRepoSlug = uiState.savedRepoSlug,
                        onOpenGitHubHome = {
                            val target = if (uiState.savedRepoSlug.isNotBlank()) {
                                "https://github.com/${uiState.savedRepoSlug}/releases"
                            } else {
                                "https://github.com"
                            }
                            openUrl(target)
                        }
                    )
                }
            }

            // 5. Status-dependent Release History Section
            when (val state = uiState.releaseState) {
                ReleaseUiState.Idle -> {
                    item(key = "idle_state") {
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
                                Icon(
                                    imageVector = Icons.Rounded.RocketLaunch,
                                    contentDescription = null,
                                    tint = SteamAccent,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "Koppla ditt GitHub-repository",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SteamTextPrimary
                                )
                                Text(
                                    text = "När du har pushat SteamPulse till GitHub och skapat en release (t.ex. tagg v1.1.0) anger du ägare/repo ovan för att hämta ändringslogg och uppdateringar live.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SteamTextSecondary
                                )
                            }
                        }
                    }
                }

                ReleaseUiState.Loading -> {
                    item(key = "loading_state") {
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
                                        text = "Hämtar GitHub Releases...",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SteamTextPrimary
                                    )
                                    Text(
                                        text = "Kontrollerar senaste tagg mot v${uiState.currentVersionName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteamTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                is ReleaseUiState.Error -> {
                    item(key = "error_state") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteamSurface),
                            border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = SteamDanger
                                    )
                                    Text(
                                        text = if (state.isNotFound) "Inga releaser hittades" else "Kunde inte hämta releaser",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SteamDanger
                                    )
                                }
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SteamTextSecondary
                                )
                                OutlinedButton(
                                    onClick = onRefresh,
                                    border = BorderStroke(1.dp, SteamBorder),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = SteamAccent
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Försök igen", color = SteamTextPrimary)
                                }
                            }
                        }
                    }
                }

                is ReleaseUiState.Success -> {
                    val releases = state.result.releases
                    item(key = "releases_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GITHUB RELEASES (${releases.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = SteamTextSecondary
                            )
                            Text(
                                text = state.result.repoSlug,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = SteamAccent
                            )
                        }
                    }

                    if (releases.isEmpty()) {
                        item(key = "no_releases_found") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SteamSurface),
                                border = BorderStroke(1.dp, SteamBorder),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Inga publicerade releaser ännu",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SteamTextPrimary
                                    )
                                    Text(
                                        text = "Det finns inga publicerade GitHub Releases i ${state.result.repoSlug}. Skapa en tagg (t.ex. v1.0.0) för att publicera din första release page.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SteamTextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = releases,
                            key = { _, release -> release.id }
                        ) { index, release ->
                            GitHubReleaseCard(
                                release = release,
                                isLatest = index == 0,
                                onOpenUrl = openUrl
                            )
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun InstalledBuildCard(
    uiState: ReleaseScreenState,
    onRefresh: () -> Unit
) {
    val (statusLabel, statusColor) = when (val state = uiState.releaseState) {
        is ReleaseUiState.Loading -> "Kontrollerar..." to SteamAccent
        is ReleaseUiState.Success -> when (state.result.versionStatus) {
            VersionComparisonStatus.UPDATE_AVAILABLE -> "Ny uppdatering finns" to SteamNeon
            VersionComparisonStatus.UP_TO_DATE -> "Senaste versionen" to SteamSale
            VersionComparisonStatus.AHEAD_OF_REMOTE -> "Lokal utvecklingsversion" to SteamAmber
        }
        is ReleaseUiState.Error -> "Kontroll misslyckades" to SteamDanger
        ReleaseUiState.Idle -> "Ej ansluten till repo" to SteamTextSecondary
    }

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
                            SteamAccent.copy(alpha = 0.08f),
                            SteamNeon.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SteamCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = SteamAccent
                        )
                    }
                    Column {
                        Text(
                            text = "INSTALLERAD APPVERSION",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamTextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "v${uiState.currentVersionName}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = SteamTextPrimary
                            )
                            Text(
                                text = "Build ${uiState.currentVersionCode}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = SteamTextSecondary
                            )
                        }
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            HorizontalDivider(color = SteamBorder.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.applicationId,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = SteamTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.savedRepoSlug.isNotBlank()) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = uiState.releaseState !is ReleaseUiState.Loading,
                        border = BorderStroke(1.dp, SteamBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = SteamAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Sök nu",
                            fontSize = 12.sp,
                            color = SteamTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(
    latestRelease: GitHubRelease,
    onOpenReleaseUrl: (String) -> Unit
) {
    val apkAsset = latestRelease.assets.firstOrNull { it.isApkOrBundle }
    val primaryTargetUrl = apkAsset?.downloadUrl?.takeIf { it.isNotBlank() } ?: latestRelease.htmlUrl

    Card(
        colors = CardDefaults.cardColors(containerColor = SteamCard),
        border = BorderStroke(1.5.dp, SteamNeon),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("update_available_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(SteamNeon.copy(alpha = 0.14f), Color.Transparent)
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
                    imageVector = Icons.Rounded.NewReleases,
                    contentDescription = null,
                    tint = SteamNeon
                )
                Text(
                    text = "NY UPPDATERING TILLGÄNGLIG: ${latestRelease.tagName}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = SteamNeon
                )
            }

            Text(
                text = latestRelease.title,
                style = MaterialTheme.typography.titleMedium,
                color = SteamTextPrimary
            )

            if (latestRelease.body.isNotBlank()) {
                Text(
                    text = latestRelease.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onOpenReleaseUrl(primaryTargetUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamNeon,
                        contentColor = Color(0xFF020617)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("download_update_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (apkAsset != null) "Ladda ner APK" else "Hämta uppdatering",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { onOpenReleaseUrl(latestRelease.htmlUrl) },
                    border = BorderStroke(1.dp, SteamBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = SteamAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("GitHub", color = SteamTextPrimary)
                }
            }
        }
    }
}

@Composable
private fun GitHubRepoConfigCard(
    uiState: ReleaseScreenState,
    onRepoInputChange: (String) -> Unit,
    onTogglePrereleases: (Boolean) -> Unit,
    onSaveAndCheck: () -> Unit,
    showWorkflowGuide: Boolean,
    onToggleGuide: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(1.dp, SteamBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GITHUB RELEASE-KÄLLA",
                    style = MaterialTheme.typography.labelMedium,
                    color = SteamAccent
                )
                OutlinedButton(
                    onClick = onToggleGuide,
                    border = BorderStroke(1.dp, SteamBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = SteamAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showWorkflowGuide) "Dölj guide" else "Publiceringsguide",
                        fontSize = 11.sp,
                        color = SteamTextPrimary
                    )
                }
            }

            OutlinedTextField(
                value = uiState.repoInput,
                onValueChange = onRepoInputChange,
                label = {
                    Text(
                        text = stringResource(R.string.release_repo_label),
                        fontSize = 12.sp
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.release_repo_placeholder),
                        color = SteamTextMuted,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                trailingIcon = {
                    if (uiState.repoInput.isNotEmpty()) {
                        IconButton(
                            onClick = { onRepoInputChange("") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Rensa",
                                tint = SteamTextSecondary
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSaveAndCheck() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SteamAccent,
                    unfocusedBorderColor = SteamBorder,
                    focusedTextColor = SteamTextPrimary,
                    unfocusedTextColor = SteamTextPrimary,
                    focusedLabelColor = SteamAccent,
                    unfocusedLabelColor = SteamTextSecondary,
                    cursorColor = SteamAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_repo_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inkludera förhandsversioner (Pre-release)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteamTextPrimary
                    )
                    Text(
                        text = "Visa beta- och RC-taggar från GitHub Releases",
                        style = MaterialTheme.typography.bodySmall,
                        color = SteamTextSecondary
                    )
                }
                Switch(
                    checked = uiState.includePrereleases,
                    onCheckedChange = onTogglePrereleases,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF020617),
                        checkedTrackColor = SteamAccent,
                        uncheckedThumbColor = SteamTextSecondary,
                        uncheckedTrackColor = SteamCard
                    ),
                    modifier = Modifier.testTag("prerelease_switch")
                )
            }

            Button(
                onClick = onSaveAndCheck,
                enabled = uiState.releaseState !is ReleaseUiState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SteamAccent,
                    contentColor = Color(0xFF020617)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("check_updates_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Spara & Hämta Releases",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GitHubWorkflowGuideCard(
    savedRepoSlug: String,
    onOpenGitHubHome: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SteamCard),
        border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SÅ PUBLICERAR DU EN RELEASE PAGE PÅ GITHUB",
                style = MaterialTheme.typography.labelMedium,
                color = SteamAccent
            )
            Text(
                text = "Projektet innehåller redan ett färdigt GitHub Actions-arbetsflöde (.github/workflows/release.yml) och CHANGELOG.md:",
                style = MaterialTheme.typography.bodySmall,
                color = SteamTextSecondary
            )

            val steps = listOf(
                "1. Öppna inställningsmenyn i AI Studio och välj att pusha projektet till GitHub.",
                "2. Höj versionName/versionCode i app/build.gradle.kts och uppdatera CHANGELOG.md.",
                "3. Skapa en versionstagg: git tag v1.1.0 && git push origin v1.1.0.",
                "4. GitHub Actions bygger APK:n och publicerar automatiskt din GitHub Release Page med nedladdningsbar appuppdatering."
            )
            steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall,
                    color = SteamTextPrimary
                )
            }

            OutlinedButton(
                onClick = onOpenGitHubHome,
                border = BorderStroke(1.dp, SteamBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = SteamAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (savedRepoSlug.isNotBlank()) "Öppna Releases på GitHub" else "Öppna GitHub",
                    color = SteamTextPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHubReleaseCard(
    release: GitHubRelease,
    isLatest: Boolean,
    onOpenUrl: (String) -> Unit
) {
    var expanded by remember(release.id) { mutableStateOf(isLatest) }

    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isLatest) SteamAccent.copy(alpha = 0.5f) else SteamBorder
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("release_card_${release.tagName}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isLatest) SteamAccent.copy(alpha = 0.16f) else SteamCard,
                        border = BorderStroke(
                            1.dp,
                            if (isLatest) SteamAccent.copy(alpha = 0.5f) else SteamBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = release.tagName.ifBlank { "release" },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isLatest) SteamAccent else SteamTextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (isLatest) {
                        Surface(
                            color = SteamNeon.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Senaste",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SteamNeon,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (release.isPrerelease) {
                        Surface(
                            color = SteamAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Pre-release",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SteamAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = formatIsoDate(release.publishedAtIso),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = SteamTextSecondary
                )
            }

            Text(
                text = release.title,
                style = MaterialTheme.typography.titleMedium,
                color = SteamTextPrimary
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (release.body.isNotBlank()) {
                        Surface(
                            color = SteamBg,
                            border = BorderStroke(1.dp, SteamBorder.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = release.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = SteamTextSecondary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (release.assets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "NEDLADDNINGSBARA FILER (${release.assets.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SteamTextSecondary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                release.assets.forEach { asset ->
                                    AssetDownloadChip(
                                        asset = asset,
                                        onClick = { onOpenUrl(asset.downloadUrl) }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onOpenUrl(release.htmlUrl) },
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = SteamAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Öppna Release Page",
                                fontSize = 12.sp,
                                color = SteamTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetDownloadChip(
    asset: GitHubReleaseAsset,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = SteamCard,
        border = BorderStroke(
            1.dp,
            if (asset.isApkOrBundle) SteamNeon.copy(alpha = 0.55f) else SteamBorder
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (asset.isApkOrBundle) Icons.Rounded.Android else Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = if (asset.isApkOrBundle) SteamNeon else SteamAccent,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = asset.name,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SteamTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatFileSize(asset.sizeBytes)} • ${asset.downloadCount} hämtningar",
                    fontSize = 10.sp,
                    color = SteamTextSecondary
                )
            }
        }
    }
}

private fun formatIsoDate(iso: String): String {
    return if (iso.length >= 10) iso.substring(0, 10) else iso.ifBlank { "—" }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0L -> "0 B"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.forLanguageTag("sv-SE"), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.forLanguageTag("sv-SE"), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
