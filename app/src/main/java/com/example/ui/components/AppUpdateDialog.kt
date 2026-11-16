package com.example.ui.components

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamAmber
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.update.AppUpdateCheckOutcome
import com.example.update.AppUpdateManager
import com.example.update.AppUpdateRelease
import com.example.update.DownloadQueryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Stable
class AppUpdateCoordinator(
    private val context: Context,
    private val manager: AppUpdateManager,
    private val scope: CoroutineScope,
    private val onStatusMessage: (String) -> Unit
) {
    var pendingRelease by mutableStateOf<AppUpdateRelease?>(null)
        private set
    var hasAvailableUpdate by mutableStateOf(false)
        private set
    var isChecking by mutableStateOf(false)
        private set
    var lastManualCheckMessage by mutableStateOf<String?>(null)
        private set
    var activeDownloadId by mutableStateOf<Long?>(null)
        private set
    var expectedApkFile by mutableStateOf<File?>(null)
        private set
    var awaitingInstallPermission by mutableStateOf(false)
        private set

    fun checkForUpdates(manual: Boolean) {
        if (isChecking) return
        scope.launch {
            isChecking = true
            if (manual) {
                lastManualCheckMessage = "Söker..."
            }
            try {
                when (val outcome = manager.checkForUpdate(ignoreDismissed = manual)) {
                    is AppUpdateCheckOutcome.Available -> {
                        hasAvailableUpdate = true
                        pendingRelease = outcome.release
                        if (manual) {
                            lastManualCheckMessage = "Ny version hittades: ${outcome.release.tagName}"
                        }
                    }
                    is AppUpdateCheckOutcome.Dismissed -> {
                        hasAvailableUpdate = true
                        if (manual) {
                            pendingRelease = outcome.release
                            lastManualCheckMessage = "Ny version hittades: ${outcome.release.tagName}"
                        }
                    }
                    is AppUpdateCheckOutcome.UpToDate -> {
                        hasAvailableUpdate = false
                        if (manual) {
                            val msg = "Du har redan den senaste versionen (v${BuildConfig.VERSION_NAME})"
                            lastManualCheckMessage = msg
                            onStatusMessage(msg)
                        }
                    }
                    is AppUpdateCheckOutcome.NoApkAsset -> {
                        if (manual) {
                            val msg = "Ingen APK hittades i den senaste GitHub-releasen."
                            lastManualCheckMessage = msg
                            onStatusMessage(msg)
                        }
                    }
                    is AppUpdateCheckOutcome.Error -> {
                        if (manual) {
                            lastManualCheckMessage = outcome.message
                            onStatusMessage(outcome.message)
                        }
                    }
                }
            } finally {
                isChecking = false
            }
        }
    }

    fun startUpdateNow() {
        val release = pendingRelease ?: return
        if (!manager.canRequestPackageInstalls()) {
            awaitingInstallPermission = true
            val opened = manager.openUnknownSourcesSettings()
            if (!opened) {
                onStatusMessage("Öppna appinställningarna och tillåt installation från denna källa.")
            } else {
                onStatusMessage("Tillåt installation från SteamPulse och återgå sedan till appen.")
            }
            return
        }
        awaitingInstallPermission = false
        beginDownload(release)
    }

    fun onAppResumed() {
        if (awaitingInstallPermission && manager.canRequestPackageInstalls()) {
            awaitingInstallPermission = false
            pendingRelease?.let { beginDownload(it) }
        }
    }

    private fun beginDownload(release: AppUpdateRelease) {
        try {
            val (downloadId, destinationFile) = manager.enqueueApkDownload(release)
            expectedApkFile = destinationFile
            activeDownloadId = downloadId
            onStatusMessage("Laddar ner SteamPulse ${release.tagName}...")
        } catch (e: Exception) {
            onStatusMessage("Kunde inte starta nedladdning: ${e.localizedMessage ?: "okänt fel"}")
        }
    }

    fun handleDownloadComplete(completedDownloadId: Long) {
        val currentId = activeDownloadId ?: return
        if (completedDownloadId != currentId) return

        val fallback = expectedApkFile ?: pendingRelease?.let { manager.buildApkDestinationFile(it) }
        activeDownloadId = null
        if (fallback == null) {
            onStatusMessage("Nedladdad APK hittades inte.")
            return
        }

        when (val status = manager.queryDownloadStatus(currentId, fallback)) {
            is DownloadQueryStatus.Successful -> {
                pendingRelease = null
                val launched = manager.installApk(status.apkFile)
                if (!launched) {
                    onStatusMessage("Kunde inte öppna paketinstalleraren för ${status.apkFile.name}.")
                }
            }
            is DownloadQueryStatus.Failed -> {
                onStatusMessage("Nedladdningen misslyckades (felkod ${status.reason}).")
            }
            is DownloadQueryStatus.Pending -> Unit
        }
    }

    fun dismissForLater() {
        pendingRelease?.let { manager.dismissTag(it.tagName) }
        pendingRelease = null
        awaitingInstallPermission = false
    }
}

@Composable
fun rememberAppUpdateCoordinator(
    onStatusMessage: (String) -> Unit
): AppUpdateCoordinator {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { AppUpdateManager(context) }
    val coordinator = remember(context, manager, scope) {
        AppUpdateCoordinator(
            context = context,
            manager = manager,
            scope = scope,
            onStatusMessage = onStatusMessage
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, coordinator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coordinator.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activeDownloadId = coordinator.activeDownloadId
    DisposableEffect(activeDownloadId, context) {
        if (activeDownloadId == null) {
            onDispose {}
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                        coordinator.handleDownloadComplete(id)
                    }
                }
            }
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
            onDispose {
                runCatching {
                    context.applicationContext.unregisterReceiver(receiver)
                }
            }
        }
    }

    return coordinator
}

@Composable
fun AppUpdateDialog(
    release: AppUpdateRelease,
    currentVersionName: String = BuildConfig.VERSION_NAME,
    isDownloading: Boolean = false,
    awaitingPermission: Boolean = false,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onLater() },
        containerColor = SteamSurface,
        titleContentColor = SteamTextPrimary,
        textContentColor = SteamTextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = SteamNeon,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Ny uppdatering tillgänglig",
                    style = MaterialTheme.typography.titleLarge,
                    color = SteamTextPrimary
                )
                Surface(
                    color = SteamNeon.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = SteamNeon,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "v$currentVersionName → ${release.tagName}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = SteamNeon
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = SteamBg,
                    border = BorderStroke(1.dp, SteamBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = release.releaseNotes.ifBlank {
                            "Förbättringar och buggfixar i ${release.tagName}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SteamTextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                            .testTag("app_update_changelog")
                    )
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            color = SteamNeon,
                            trackColor = SteamBorder,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Laddar ner ${release.apkAssetName}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = SteamAccent
                        )
                    }
                } else if (awaitingPermission) {
                    Text(
                        text = "Aktivera \"Tillåt från denna källa\" i Android-inställningarna för att installera APK-filen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamAmber
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateNow,
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SteamNeon,
                    contentColor = Color(0xFF020617)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("update_now_button")
            ) {
                Text(
                    text = if (isDownloading) "Laddar ner..." else "Uppdatera nu",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onLater,
                enabled = !isDownloading,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("update_later_button")
            ) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "Senare",
                    color = SteamTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        modifier = Modifier.testTag("app_update_dialog")
    )
}
