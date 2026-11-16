package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.BuildConfig
import com.example.data.BackupRestoreResult
import com.example.data.WorkshopLocalStore
import com.example.model.CurrencyMode
import com.example.notifications.WorkshopNotificationHelper
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamAmber
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamCard
import com.example.ui.theme.SteamDanger
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary

@OptIn(ExperimentalLayoutApi::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    isGuest: Boolean,
    userDisplayName: String?,
    userEmail: String?,
    hasSteamSession: Boolean,
    steamSessionSteamId64: String,
    isCheckingForUpdate: Boolean,
    updateStatusMessage: String?,
    latestGitHubVersion: String? = null,
    notificationsEnabled: Boolean,
    checkIntervalHours: Long,
    notifyNewModsEnabled: Boolean,
    notifyModUpdatesEnabled: Boolean,
    quietHoursEnabled: Boolean,
    quietHoursStartHour: Int,
    quietHoursEndHour: Int,
    currency: CurrencyMode,
    alwaysOpenModsInSteamApp: Boolean,
    onBack: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenReleases: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSignOut: () -> Unit,
    onConnectSteamSession: () -> Unit,
    onClearSteamSession: () -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSelectCheckIntervalHours: (Long) -> Unit,
    onSetNotifyNewModsEnabled: (Boolean) -> Unit,
    onSetNotifyModUpdatesEnabled: (Boolean) -> Unit,
    onSetQuietHoursEnabled: (Boolean) -> Unit,
    onSetQuietHoursRange: (Int, Int) -> Unit,
    onSelectCurrency: (CurrencyMode) -> Unit,
    onSetAlwaysOpenModsInSteamApp: (Boolean) -> Unit,
    onExportBackupJson: () -> String,
    onImportBackupJson: (String) -> BackupRestoreResult,
    onClearCachedLists: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var startHourMenuOpen by remember { mutableStateOf(false) }
    var endHourMenuOpen by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) {
            if (pending == "ENABLE") {
                onSetNotificationsEnabled(true)
                WorkshopNotificationHelper.schedulePeriodicCheck(
                    context = context,
                    intervalHours = checkIntervalHours,
                    existingPolicy = ExistingPeriodicWorkPolicy.UPDATE
                )
                onShowMessage("Bakgrundsnotiser aktiverade.")
            } else if (pending == "TEST") {
                val sent = WorkshopNotificationHelper.postTestNotification(context)
                onShowMessage(if (sent) "Testnotis skickad!" else "Kunde inte skicka testnotis.")
            }
        } else {
            onShowMessage("Notisbehörighet nekades i Android-inställningarna.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Screen Header with Back Arrow
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Tillbaka",
                    tint = SteamTextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SteamAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = SteamAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inställningar",
                    style = MaterialTheme.typography.titleLarge,
                    color = SteamTextPrimary
                )
                Text(
                    text = "Uppdateringar, notiser, visning & backup",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SteamTextSecondary
                )
            }
        }

        // 1. App & Uppdateringar
        SettingsSectionCard(
            title = "App & Uppdateringar",
            icon = Icons.Rounded.SystemUpdate,
            accent = SteamNeon
        ) {
            Surface(
                color = SteamBg,
                border = BorderStroke(1.dp, SteamBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Din version (installerad): v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SteamNeon,
                        modifier = Modifier.testTag("settings_version_text")
                    )
                    Text(
                        text = "Senaste på GitHub: ${
                            if (isCheckingForUpdate) "Söker..."
                            else latestGitHubVersion ?: "Tryck nedan för att kontrollera"
                        }",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = if (latestGitHubVersion != null) SteamAccent else SteamTextSecondary,
                        modifier = Modifier.testTag("settings_github_version")
                    )
                    Text(
                        text = "Paket: ${BuildConfig.APPLICATION_ID}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamTextSecondary.copy(alpha = 0.8f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCheckForUpdate,
                    enabled = !isCheckingForUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamNeon,
                        contentColor = Color(0xFF020617)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("settings_check_updates_button")
                ) {
                    if (isCheckingForUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF020617)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Söker...", fontWeight = FontWeight.ExtraBold)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sök efter uppdateringar", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            if (!updateStatusMessage.isNullOrBlank()) {
                Surface(
                    color = SteamBg,
                    border = BorderStroke(1.dp, SteamBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = updateStatusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamAccent,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("settings_update_status")
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenReleases,
                border = BorderStroke(1.dp, SteamBorder),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Text("Visa GitHub Releaser", color = SteamTextPrimary, fontSize = 12.sp)
            }
        }

        // 2. Konto & Steam-synk
        SettingsSectionCard(
            title = "Konto & Steam-synk",
            icon = Icons.Rounded.AccountCircle,
            accent = SteamAccent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isGuest) "Gästläge" else (userDisplayName?.takeIf { it.isNotBlank() } ?: "Inloggad"),
                        style = MaterialTheme.typography.titleMedium,
                        color = SteamTextPrimary
                    )
                    Text(
                        text = if (isGuest) {
                            "Data sparas lokalt på enheten"
                        } else {
                            userEmail?.takeIf { it.isNotBlank() } ?: "Molnsynk aktiv med Google-konto"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (isGuest) {
                    Button(
                        onClick = onNavigateToLogin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SteamAccent,
                            contentColor = Color(0xFF020617)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_auth_button")
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Logga in", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_auth_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = SteamDanger,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Logga ut", color = SteamDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            HorizontalDivider(color = SteamBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasSteamSession) "Ansluten till Steam" else "Ej ansluten",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (hasSteamSession) SteamNeon else SteamTextPrimary,
                        modifier = Modifier.testTag("settings_steam_status")
                    )
                    Text(
                        text = if (hasSteamSession) {
                            "SteamID64: ${steamSessionSteamId64.ifBlank { "Aktiv session" }}"
                        } else {
                            "Logga in via Steam WebView för att synka privata prenumerationer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamTextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onConnectSteamSession,
                    border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("settings_steam_connect_button")
                ) {
                    Text(
                        text = if (hasSteamSession) "Återanslut" else "Anslut till Steam",
                        color = SteamAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                if (hasSteamSession) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                            }
                            onClearSteamSession()
                        },
                        border = BorderStroke(1.dp, SteamDanger.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("settings_steam_clear_button")
                    ) {
                        Text(
                            text = "Rensa cookies",
                            color = SteamDanger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Notiser & Bakgrundskontroll
        SettingsSectionCard(
            title = "Notiser & Bakgrundskontroll",
            icon = Icons.Rounded.NotificationsActive,
            accent = SteamAmber
        ) {
            SettingsSwitchRow(
                title = "Bakgrundskontroll",
                subtitle = "Kontrollerar Workshop automatiskt var ${checkIntervalHours}:e timme",
                checked = notificationsEnabled,
                onCheckedChange = { enable ->
                    if (!enable) {
                        onSetNotificationsEnabled(false)
                        WorkshopNotificationHelper.cancelPeriodicCheck(context)
                        onShowMessage("Bakgrundskontroll pausad.")
                    } else if (WorkshopNotificationHelper.hasNotificationPermission(context)) {
                        onSetNotificationsEnabled(true)
                        WorkshopNotificationHelper.schedulePeriodicCheck(
                            context = context,
                            intervalHours = checkIntervalHours,
                            existingPolicy = ExistingPeriodicWorkPolicy.UPDATE
                        )
                        onShowMessage("Bakgrundsnotiser aktiverade.")
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pendingPermissionAction = "ENABLE"
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                switchTestTag = "settings_notifications_master_switch"
            )

            Text(
                text = "Kontrollintervall",
                style = MaterialTheme.typography.labelSmall,
                color = SteamTextSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkshopLocalStore.SUPPORTED_CHECK_INTERVAL_HOURS.forEach { hours ->
                    val selected = checkIntervalHours == hours
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSelectCheckIntervalHours(hours)
                            if (notificationsEnabled && WorkshopNotificationHelper.hasNotificationPermission(context)) {
                                WorkshopNotificationHelper.schedulePeriodicCheck(
                                    context = context,
                                    intervalHours = hours,
                                    existingPolicy = ExistingPeriodicWorkPolicy.UPDATE
                                )
                            }
                            onShowMessage("Kontrollintervall satt till $hours tim.")
                        },
                        label = {
                            Text(
                                text = "$hours tim",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SteamAccent,
                            selectedLabelColor = Color(0xFF020617),
                            containerColor = SteamBg,
                            labelColor = SteamTextSecondary
                        ),
                        modifier = Modifier.testTag("settings_interval_${hours}h")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (WorkshopNotificationHelper.hasNotificationPermission(context)) {
                            val sent = WorkshopNotificationHelper.postTestNotification(context)
                            onShowMessage(if (sent) "Testnotis skickad!" else "Kunde inte skicka testnotis.")
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pendingPermissionAction = "TEST"
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("settings_test_notification_button")
                ) {
                    Text("Testa notis", color = SteamAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { onShowMessage("Kunde inte öppna appinställningarna.") }
                    },
                    border = BorderStroke(1.dp, SteamBorder),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("settings_battery_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatterySaver,
                        contentDescription = null,
                        tint = SteamAmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Batteriinställningar", color = SteamTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            HorizontalDivider(color = SteamBorder)

            SettingsSwitchRow(
                title = "Nya moddar i följda spel",
                subtitle = "Avisera när nya Workshop-moddar släpps",
                checked = notifyNewModsEnabled,
                onCheckedChange = onSetNotifyNewModsEnabled,
                switchTestTag = "settings_notify_new_mods"
            )

            SettingsSwitchRow(
                title = "Uppdateringar av prenumererade moddar",
                subtitle = "Avisera när dina bokmärkta eller prenumererade moddar får en patch",
                checked = notifyModUpdatesEnabled,
                onCheckedChange = onSetNotifyModUpdatesEnabled,
                switchTestTag = "settings_notify_mod_updates"
            )

            HorizontalDivider(color = SteamBorder)

            SettingsSwitchRow(
                title = "Tysta timmar (${formatHourLabel(quietHoursStartHour)}–${formatHourLabel(quietHoursEndHour)})",
                subtitle = "Stör ej: skjuter upp notiser till nästa körning utanför tysta timmar",
                checked = quietHoursEnabled,
                onCheckedChange = onSetQuietHoursEnabled,
                switchTestTag = "settings_quiet_hours_switch",
                leadingIcon = Icons.Rounded.Bedtime
            )

            if (quietHoursEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { startHourMenuOpen = true },
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("settings_quiet_start_button")
                        ) {
                            Text(
                                text = "Start: ${formatHourLabel(quietHoursStartHour)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = SteamTextPrimary
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = SteamAccent, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = startHourMenuOpen,
                            onDismissRequest = { startHourMenuOpen = false },
                            containerColor = SteamSurface
                        ) {
                            (0..23).forEach { hour ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = formatHourLabel(hour),
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = if (hour == quietHoursStartHour) SteamAccent else SteamTextPrimary
                                        )
                                    },
                                    onClick = {
                                        onSetQuietHoursRange(hour, quietHoursEndHour)
                                        startHourMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { endHourMenuOpen = true },
                            border = BorderStroke(1.dp, SteamBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("settings_quiet_end_button")
                        ) {
                            Text(
                                text = "Slut: ${formatHourLabel(quietHoursEndHour)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = SteamTextPrimary
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = SteamAccent, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = endHourMenuOpen,
                            onDismissRequest = { endHourMenuOpen = false },
                            containerColor = SteamSurface
                        ) {
                            (0..23).forEach { hour ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = formatHourLabel(hour),
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = if (hour == quietHoursEndHour) SteamAccent else SteamTextPrimary
                                        )
                                    },
                                    onClick = {
                                        onSetQuietHoursRange(quietHoursStartHour, hour)
                                        endHourMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Visning & Länkar
        SettingsSectionCard(
            title = "Visning & Länkar",
            icon = Icons.Rounded.CurrencyExchange,
            accent = SteamAccent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Standardvaluta",
                        style = MaterialTheme.typography.titleSmall,
                        color = SteamTextPrimary
                    )
                    Text(
                        text = "Används i Puls, Bevaka & prisvarningar",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamTextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CurrencyMode.entries.forEach { mode ->
                        val active = currency == mode
                        FilterChip(
                            selected = active,
                            onClick = {
                                onSelectCurrency(mode)
                                onShowMessage("Standardvaluta ändrad till ${mode.code}.")
                            },
                            label = {
                                Text(
                                    text = mode.code,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SteamAccent,
                                selectedLabelColor = Color(0xFF020617),
                                containerColor = SteamBg,
                                labelColor = SteamTextSecondary
                            ),
                            modifier = Modifier.testTag("settings_currency_${mode.code.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = SteamBorder)

            SettingsSwitchRow(
                title = "Öppna alltid moddar i Steam-appen",
                subtitle = "Avmarkera för att öppna Workshop-moddar direkt i webbläsaren",
                checked = alwaysOpenModsInSteamApp,
                onCheckedChange = onSetAlwaysOpenModsInSteamApp,
                switchTestTag = "settings_open_mods_in_steam_switch",
                leadingIcon = Icons.Rounded.Link
            )
        }

        // 5. Backup & Lagring
        SettingsSectionCard(
            title = "Backup & Lagring",
            icon = Icons.Rounded.Restore,
            accent = SteamNeon
        ) {
            Text(
                text = "JSON-backup i urklipp (följda spel, målpriser & notisinställningar)",
                style = MaterialTheme.typography.labelSmall,
                color = SteamTextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val json = onExportBackupJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("SteamPulse Backup", json))
                        onShowMessage("JSON-backup kopierad till urklipp!")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamAccent,
                        contentColor = Color(0xFF020617)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("settings_copy_backup_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kopiera backup", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        val result = onImportBackupJson(clipText)
                        if (result.success) {
                            onShowMessage(
                                "Återställde ${result.restoredGamesCount} spel, ${result.restoredWatchlistCount} bevakningar & inställningar."
                            )
                        } else {
                            onShowMessage(result.errorMessage ?: "Kunde inte återställa backup från urklipp.")
                        }
                    },
                    border = BorderStroke(1.dp, SteamNeon.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("settings_restore_backup_button")
                ) {
                    Icon(Icons.Rounded.ContentPasteGo, contentDescription = null, tint = SteamNeon, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Återställ från urklipp", color = SteamNeon, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            HorizontalDivider(color = SteamBorder)

            OutlinedButton(
                onClick = { showClearCacheDialog = true },
                border = BorderStroke(1.dp, SteamBorder),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("settings_clear_cache_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    tint = SteamAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Rensa lokal cache", color = SteamTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }

        // About Footer at Bottom of Settings
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "SteamPulse & Deals • Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SteamTextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.testTag("settings_about_footer")
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = SteamSurface,
            icon = {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = SteamAmber)
            },
            title = {
                Text("Rensa lokal cache?", color = SteamTextPrimary)
            },
            text = {
                Text(
                    text = "Rensar cachade spelbilder och temporära Workshop-listor. Dina följda spel, bevakningslista och inloggning sparas.",
                    color = SteamTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        runCatching {
                            context.imageLoader.memoryCache?.clear()
                            context.imageLoader.diskCache?.clear()
                            context.cacheDir?.listFiles()?.forEach { file ->
                                if (file.name != "WebView") {
                                    file.deleteRecursively()
                                }
                            }
                        }
                        onClearCachedLists()
                        onShowMessage("Lokal cache rensad (följda spel & bevakning behölls).")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SteamAmber,
                        contentColor = Color(0xFF020617)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_clear_cache_button")
                ) {
                    Text("Rensa", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearCacheDialog = false },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("cancel_clear_cache_button")
                ) {
                    Text("Avbryt", color = SteamTextSecondary)
                }
            },
            modifier = Modifier.testTag("clear_cache_confirm_dialog")
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SteamTextPrimary
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = SteamAccent,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(18.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SteamTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SteamTextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF020617),
                checkedTrackColor = SteamNeon,
                uncheckedThumbColor = SteamTextSecondary,
                uncheckedTrackColor = SteamCard
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(switchTestTag)
        )
    }
}

private fun formatHourLabel(hour: Int): String {
    val safe = hour.coerceIn(0, 23)
    return if (safe < 10) "0$safe:00" else "$safe:00"
}
