package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.GitHubReleaseRepository
import com.example.data.SteamGamesCatalog
import com.example.data.SteamWorkshopRepository
import com.example.data.WatchlistRepository
import com.example.data.WorkshopFollowRepository
import com.example.data.WorkshopLocalStore
import com.example.firebase.configureAppCheck
import com.example.model.CurrencyMode
import com.example.model.WorkshopSectionTab
import com.example.notifications.WorkshopNotificationHelper
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.rememberAppUpdateCoordinator
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.PulseDealsScreen
import com.example.ui.screens.ReleaseScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SteamWebViewLoginDialog
import com.example.ui.screens.WatchlistScreen
import com.example.ui.screens.WorkshopScreen
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamCard
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamPulseTheme
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.viewmodel.AiChatViewModel
import com.example.viewmodel.ImageStudioViewModel
import com.example.viewmodel.ReleaseViewModel
import com.example.viewmodel.SteamPulseViewModel
import com.example.viewmodel.WatchlistUiState
import com.example.viewmodel.WorkshopViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    PULSE("Puls", Icons.Rounded.Bolt, Icons.Outlined.Bolt, "tab_pulse"),
    WORKSHOP("Workshop", Icons.Rounded.Extension, Icons.Outlined.Extension, "tab_workshop"),
    WATCHLIST("Bevaka", Icons.Rounded.Notifications, Icons.Outlined.Notifications, "tab_watchlist"),
    AI_CHAT("AI-Råd", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_ai_chat"),
    IMAGE_STUDIO("Studio", Icons.Rounded.Image, Icons.Outlined.Image, "tab_image_studio")
}

class MainActivity : ComponentActivity() {
    private var pendingNavigationIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)
        WorkshopNotificationHelper.createNotificationChannel(this)
        pendingNavigationIntent = intent

        enableEdgeToEdge()

        setContent {
            SteamPulseTheme {
                AppNavigation(
                    launchIntent = pendingNavigationIntent,
                    onConsumeLaunchIntent = { pendingNavigationIntent = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigationIntent = intent
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    launchIntent: Intent? = null,
    onConsumeLaunchIntent: () -> Unit = {}
) {
    val context = LocalContext.current
    val localStore = remember { WorkshopLocalStore(context) }
    var isGuestMode by remember { mutableStateOf(localStore.isGuestMode()) }
    val updateMessagesFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 8) }
    val updateCoordinator = rememberAppUpdateCoordinator(
        onStatusMessage = { msg -> updateMessagesFlow.tryEmit(msg) }
    )

    LaunchedEffect(Unit) {
        updateCoordinator.checkForUpdates(manual = false)
    }

    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null && !isGuestMode) {
        AuthScreen(
            onAuthSuccess = {
                localStore.setGuestMode(false)
                isGuestMode = false
                val newUid = auth.currentUser?.uid
                if (!newUid.isNullOrBlank()) {
                    localStore.migrateGuestDataIfEmpty(newUid)
                }
            },
            onContinueAsGuest = {
                localStore.setGuestMode(true)
                isGuestMode = true
            }
        )
    } else {
        val effectiveUid = user?.uid ?: "guest"
        LaunchedEffect(effectiveUid) {
            localStore.setActiveUserId(effectiveUid)
        }
        key(effectiveUid) {
            AuthenticatedAppShell(
                currentUserId = effectiveUid,
                isGuest = user == null,
                userDisplayName = user?.displayName,
                userEmail = user?.email,
                launchIntent = launchIntent,
                onConsumeLaunchIntent = onConsumeLaunchIntent,
                hasAppUpdateAvailable = updateCoordinator.hasAvailableUpdate,
                isCheckingAppUpdate = updateCoordinator.isChecking,
                appUpdateStatusMessage = updateCoordinator.lastManualCheckMessage,
                updateMessagesFlow = updateMessagesFlow,
                onCheckForUpdate = { updateCoordinator.checkForUpdates(manual = true) },
                onNavigateToLogin = {
                    localStore.setGuestMode(false)
                    isGuestMode = false
                }
            )
        }
    }

    val pendingUpdateRelease = updateCoordinator.pendingRelease
    if (pendingUpdateRelease != null) {
        AppUpdateDialog(
            release = pendingUpdateRelease,
            isDownloading = updateCoordinator.activeDownloadId != null,
            awaitingPermission = updateCoordinator.awaitingInstallPermission,
            onUpdateNow = updateCoordinator::startUpdateNow,
            onLater = updateCoordinator::dismissForLater
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    isGuest: Boolean = false,
    userDisplayName: String? = null,
    userEmail: String? = null,
    launchIntent: Intent? = null,
    onConsumeLaunchIntent: () -> Unit = {},
    hasAppUpdateAvailable: Boolean = false,
    isCheckingAppUpdate: Boolean = false,
    appUpdateStatusMessage: String? = null,
    updateMessagesFlow: MutableSharedFlow<String>? = null,
    onCheckForUpdate: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val pulseViewModel: SteamPulseViewModel = viewModel(
        key = "pulse_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SteamPulseViewModel(
                    watchlistRepository = WatchlistRepository(db),
                    currentUserId = currentUserId,
                    localStore = WorkshopLocalStore(app)
                )
            }
        }
    )
    val aiChatViewModel: AiChatViewModel = viewModel(key = "chat_$currentUserId")
    val imageStudioViewModel: ImageStudioViewModel = viewModel(key = "studio_$currentUserId")
    val releaseViewModel: ReleaseViewModel = viewModel(
        key = "releases",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                ReleaseViewModel(GitHubReleaseRepository(app))
            }
        }
    )
    val workshopViewModel: WorkshopViewModel = viewModel(
        key = "workshop_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                WorkshopViewModel(
                    repository = SteamWorkshopRepository(),
                    followRepository = WorkshopFollowRepository(db),
                    userId = currentUserId,
                    localStore = WorkshopLocalStore(app)
                )
            }
        }
    )

    val uiState by pulseViewModel.uiState.collectAsStateWithLifecycle()
    val watchlistState by pulseViewModel.watchlistState.collectAsStateWithLifecycle()
    val releaseState by releaseViewModel.uiState.collectAsStateWithLifecycle()
    val workshopState by workshopViewModel.uiState.collectAsStateWithLifecycle()
    val watchlistItems = (watchlistState as? WatchlistUiState.Success)?.items.orEmpty()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.PULSE) }
    var showReleasePage by rememberSaveable { mutableStateOf(false) }
    var showSettingsPage by rememberSaveable { mutableStateOf(false) }
    var pendingAiPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsLocalStore = remember(context) { WorkshopLocalStore(context) }

    LaunchedEffect(launchIntent) {
        val targetTab = launchIntent?.getStringExtra(WorkshopNotificationHelper.EXTRA_OPEN_TAB)
        if (targetTab == WorkshopNotificationHelper.TAB_WORKSHOP) {
            showSettingsPage = false
            showReleasePage = false
            currentTab = AppTab.WORKSHOP
            val targetSection = launchIntent.getStringExtra(WorkshopNotificationHelper.EXTRA_OPEN_WORKSHOP_SECTION)
            if (targetSection == WorkshopNotificationHelper.SECTION_FOLLOWED) {
                workshopViewModel.selectSectionTab(WorkshopSectionTab.FOLLOWED)
            }
            onConsumeLaunchIntent()
        }
    }

    LaunchedEffect(workshopState.followedGameIds) {
        pulseViewModel.updateFollowedAppIds(workshopState.followedGameIds)
    }

    LaunchedEffect(pulseViewModel, workshopViewModel, updateMessagesFlow) {
        launch {
            pulseViewModel.toastEvents.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
        launch {
            workshopViewModel.toastEvents.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
        if (updateMessagesFlow != null) {
            launch {
                updateMessagesFlow.collect { msg ->
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    if (showSettingsPage) {
        BackHandler {
            showSettingsPage = false
        }
    } else if (showReleasePage) {
        BackHandler {
            showReleasePage = false
        }
    } else if (currentTab != AppTab.PULSE) {
        BackHandler {
            currentTab = AppTab.PULSE
        }
    }

    Scaffold(
        containerColor = SteamBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            SteamPulseTopBar(
                currency = uiState.currency,
                isGuest = isGuest,
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                hasUpdateAvailable = releaseState.hasUpdateAvailable || hasAppUpdateAvailable,
                onSelectCurrency = pulseViewModel::setCurrency,
                onNavigateToLogin = onNavigateToLogin,
                onOpenReleases = {
                    showSettingsPage = false
                    showReleasePage = true
                },
                onOpenSettings = {
                    showReleasePage = false
                    showSettingsPage = true
                },
                onCheckForUpdate = onCheckForUpdate,
                onSignOut = { signOut(credentialManager, scope) }
            )
        },
        bottomBar = {
            BoxWithConstraints {
                if (maxWidth < 720.dp) {
                    NavigationBar(
                        containerColor = SteamSurface,
                        contentColor = SteamTextPrimary
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    showSettingsPage = false
                                    showReleasePage = false
                                    currentTab = tab
                                },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        watchlistCount = watchlistItems.size
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF020617),
                                    selectedTextColor = SteamAccent,
                                    indicatorColor = SteamAccent,
                                    unselectedIconColor = SteamTextSecondary,
                                    unselectedTextColor = SteamTextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val useRail = maxWidth >= 720.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    NavigationRail(
                        containerColor = SteamSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    showSettingsPage = false
                                    showReleasePage = false
                                    currentTab = tab
                                },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        watchlistCount = watchlistItems.size
                                    )
                                },
                                label = { Text(tab.label, fontSize = 11.sp) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color(0xFF020617),
                                    selectedTextColor = SteamAccent,
                                    indicatorColor = SteamAccent,
                                    unselectedIconColor = SteamTextSecondary,
                                    unselectedTextColor = SteamTextSecondary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 900.dp)
                            .fillMaxSize()
                    ) {
                        if (showSettingsPage) {
                            SettingsScreen(
                                isGuest = isGuest,
                                userDisplayName = userDisplayName,
                                userEmail = userEmail,
                                hasSteamSession = workshopState.hasSteamSession,
                                steamSessionSteamId64 = workshopState.steamSessionSteamId64,
                                isCheckingForUpdate = isCheckingAppUpdate,
                                updateStatusMessage = appUpdateStatusMessage,
                                notificationsEnabled = workshopState.newModNotificationsEnabled,
                                checkIntervalHours = workshopState.checkIntervalHours,
                                notifyNewModsEnabled = workshopState.notifyNewModsEnabled,
                                notifyModUpdatesEnabled = workshopState.notifyModUpdatesEnabled,
                                quietHoursEnabled = workshopState.quietHoursEnabled,
                                quietHoursStartHour = workshopState.quietHoursStartHour,
                                quietHoursEndHour = workshopState.quietHoursEndHour,
                                currency = uiState.currency,
                                alwaysOpenModsInSteamApp = workshopState.alwaysOpenModsInSteamApp,
                                onBack = { showSettingsPage = false },
                                onCheckForUpdate = onCheckForUpdate,
                                onOpenReleases = {
                                    showSettingsPage = false
                                    showReleasePage = true
                                },
                                onNavigateToLogin = {
                                    showSettingsPage = false
                                    onNavigateToLogin()
                                },
                                onSignOut = {
                                    showSettingsPage = false
                                    signOut(credentialManager, scope)
                                },
                                onConnectSteamSession = workshopViewModel::openSteamWebViewLoginDialog,
                                onClearSteamSession = workshopViewModel::logoutSteamSession,
                                onSetNotificationsEnabled = workshopViewModel::setNewModNotificationsEnabled,
                                onSelectCheckIntervalHours = workshopViewModel::selectCheckIntervalHours,
                                onSetNotifyNewModsEnabled = workshopViewModel::setNotifyNewModsEnabled,
                                onSetNotifyModUpdatesEnabled = workshopViewModel::setNotifyModUpdatesEnabled,
                                onSetQuietHoursEnabled = workshopViewModel::setQuietHoursEnabled,
                                onSetQuietHoursRange = workshopViewModel::setQuietHoursRange,
                                onSelectCurrency = pulseViewModel::setCurrency,
                                onSetAlwaysOpenModsInSteamApp = workshopViewModel::setAlwaysOpenModsInSteamApp,
                                onExportBackupJson = { settingsLocalStore.exportBackupJson(currentUserId) },
                                onImportBackupJson = { rawJson ->
                                    settingsLocalStore.importBackupJson(currentUserId, rawJson).also { result ->
                                        if (result.success) {
                                            workshopViewModel.reloadFromLocalStoreAfterRestore()
                                            pulseViewModel.reloadLocalDataFromStore()
                                        }
                                    }
                                },
                                onClearCachedLists = workshopViewModel::clearCachedLists,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )

                            if (workshopState.showSteamWebViewLoginDialog) {
                                SteamWebViewLoginDialog(
                                    onDismiss = workshopViewModel::dismissSteamWebViewLoginDialog,
                                    onSteamLoginCaptured = workshopViewModel::onSteamWebViewAuthenticated
                                )
                            }
                        } else if (showReleasePage) {
                            ReleaseScreen(
                                uiState = releaseState,
                                onRepoInputChange = releaseViewModel::updateRepoInput,
                                onTogglePrereleases = releaseViewModel::toggleIncludePrereleases,
                                onSaveAndCheck = releaseViewModel::saveAndCheckUpdates,
                                onRefresh = releaseViewModel::refreshReleases,
                                onClose = { showReleasePage = false }
                            )
                        } else {
                            when (currentTab) {
                                AppTab.PULSE -> PulseDealsScreen(
                                    uiState = uiState,
                                    watchlistItems = watchlistItems,
                                    onSearchChange = pulseViewModel::updateSearchQuery,
                                    onCategorySelect = pulseViewModel::selectCategory,
                                    onSortSelect = pulseViewModel::selectSortOrder,
                                    onResetFilters = pulseViewModel::resetFilters,
                                    onOpenGameDetail = pulseViewModel::openGameDetail,
                                    onCloseGameDetail = pulseViewModel::closeGameDetail,
                                    onSelectEdition = pulseViewModel::selectEdition,
                                    onToggleWatchlist = pulseViewModel::toggleWatchlist,
                                    onSavePriceAlert = pulseViewModel::savePriceAlert,
                                    onAskAiAboutGame = { game ->
                                        val (bestOffer, bestEur) = SteamGamesCatalog.getBestOffer(game)
                                        val bestFormatted = SteamGamesCatalog.formatPrice(bestEur, uiState.currency)
                                        val steamFormatted = SteamGamesCatalog.formatPrice(game.steamPriceEur, uiState.currency)
                                        pendingAiPrompt =
                                            "Analysera dealen för ${game.title}: Steam kostar $steamFormatted, lägsta pris är $bestFormatted hos ${bestOffer.store}. Bör jag slå till nu eller vänta på rea?"
                                        currentTab = AppTab.AI_CHAT
                                    },
                                    onOpenWorkshopMods = { game ->
                                        showReleasePage = false
                                        workshopViewModel.focusGameFromPulse(game.appId, game.title)
                                        currentTab = AppTab.WORKSHOP
                                    }
                                )

                                AppTab.WORKSHOP -> WorkshopScreen(
                                    uiState = workshopState,
                                    onSelectSectionTab = workshopViewModel::selectSectionTab,
                                    onSelectLeaderboardMode = workshopViewModel::selectLeaderboardMode,
                                    onGameSearchChange = workshopViewModel::updateGameSearchQuery,
                                    onSubmitGameSearch = workshopViewModel::submitGameSearch,
                                    onToggleOnlyWorkshopPowerhouses = workshopViewModel::toggleOnlyWorkshopPowerhouses,
                                    onRefreshGames = workshopViewModel::refreshTop100Games,
                                    onOpenGameWorkshop = workshopViewModel::openGameWorkshop,
                                    onOpenCustomAppId = workshopViewModel::openCustomAppWorkshop,
                                    onCloseGameWorkshop = workshopViewModel::closeGameWorkshop,
                                    onSelectSortOrder = workshopViewModel::selectSortOrder,
                                    onModSearchChange = workshopViewModel::updateModSearchQuery,
                                    onSubmitModSearch = workshopViewModel::submitModSearch,
                                    onRefreshMods = workshopViewModel::refreshMods,
                                    onLoadMoreMods = workshopViewModel::loadMoreMods,
                                    onOpenModDetail = workshopViewModel::openModDetail,
                                    onCloseModDetail = workshopViewModel::closeModDetail,
                                    onToggleFollowGame = workshopViewModel::toggleFollowGame,
                                    onToggleBookmarkMod = workshopViewModel::toggleBookmarkMod,
                                    onRemoveFollowedMod = workshopViewModel::removeFollowedMod,
                                    onRefreshFollowedFeed = { workshopViewModel.refreshFollowedGamesFeed() },
                                    onOpenSteamSyncDialog = workshopViewModel::openSteamSyncDialog,
                                    onDismissSteamSyncDialog = workshopViewModel::dismissSteamSyncDialog,
                                    onSteamProfileInputChange = workshopViewModel::updateSteamProfileInput,
                                    onSubmitSteamSync = workshopViewModel::syncSteamSubscriptions,
                                    onOpenSteamWebViewLogin = workshopViewModel::openSteamWebViewLoginDialog,
                                    onDismissSteamWebViewLogin = workshopViewModel::dismissSteamWebViewLoginDialog,
                                    onSteamWebViewAuthenticated = workshopViewModel::onSteamWebViewAuthenticated,
                                    onLogoutSteamSession = workshopViewModel::logoutSteamSession,
                                    onWorkshopVisited = workshopViewModel::markWorkshopVisited,
                                    onSetNewModNotificationsEnabled = workshopViewModel::setNewModNotificationsEnabled,
                                    onShowNotificationFeedback = workshopViewModel::emitToast,
                                    onSelectCheckIntervalHours = workshopViewModel::selectCheckIntervalHours,
                                    onSelectFollowedModsFilter = workshopViewModel::selectFollowedModsFilter,
                                    onMarkModUpdateSeen = workshopViewModel::markModUpdateSeen
                                )

                                AppTab.WATCHLIST -> WatchlistScreen(
                                    watchlistState = watchlistState,
                                    games = uiState.games,
                                    currency = uiState.currency,
                                    onOpenGame = { gameId ->
                                        pulseViewModel.openGameDetail(gameId)
                                        currentTab = AppTab.PULSE
                                    },
                                    onRemoveItem = pulseViewModel::removeWatchlistItem,
                                    onUpdateTargetPrice = pulseViewModel::savePriceAlert
                                )

                                AppTab.AI_CHAT -> AiChatScreen(
                                    viewModel = aiChatViewModel,
                                    initialPrompt = pendingAiPrompt,
                                    onConsumeInitialPrompt = { pendingAiPrompt = null }
                                )

                                AppTab.IMAGE_STUDIO -> ImageStudioScreen(
                                    viewModel = imageStudioViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: AppTab,
    selected: Boolean,
    watchlistCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab == AppTab.WATCHLIST && watchlistCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = SteamCard,
                    contentColor = SteamAccent
                ) {
                    Text(
                        text = watchlistCount.toString(),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp
                    )
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamPulseTopBar(
    currency: CurrencyMode,
    isGuest: Boolean,
    userDisplayName: String?,
    userEmail: String?,
    hasUpdateAvailable: Boolean,
    onSelectCurrency: (CurrencyMode) -> Unit,
    onNavigateToLogin: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
    onSignOut: () -> Unit
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SteamSurface,
            titleContentColor = SteamTextPrimary
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(SteamAccent, SteamNeon))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF020617),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        text = "SteamPulse",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                        color = SteamTextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "& DEALS 2026",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = SteamAccent,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // Compact SEK / EUR Currency Switcher
            Surface(
                color = SteamBg,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, SteamBorder),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    CurrencyMode.entries.forEach { mode ->
                        val active = currency == mode
                        Surface(
                            onClick = { onSelectCurrency(mode) },
                            color = if (active) SteamAccent else Color.Transparent,
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.testTag("currency_${mode.code.lowercase()}")
                        ) {
                            Text(
                                text = mode.code,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = if (active) Color(0xFF020617) else SteamTextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Single account button: "Logga in" in guest mode, or user avatar/sign-out menu when signed in
            if (isGuest) {
                Surface(
                    onClick = onNavigateToLogin,
                    color = SteamAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, SteamAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("guest_login_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "Logga in",
                            tint = SteamAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Logga in",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = SteamAccent
                        )
                    }
                }
            } else {
                val initial = (userDisplayName?.firstOrNull()
                    ?: userEmail?.firstOrNull()
                    ?: 'S').uppercaseChar().toString()
                Box {
                    IconButton(
                        onClick = { accountMenuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("account_menu_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(SteamAccent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = SteamAccent
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false },
                        containerColor = SteamSurface
                    ) {
                        val label = userDisplayName?.takeIf { it.isNotBlank() }
                            ?: userEmail?.takeIf { it.isNotBlank() }
                            ?: "Inloggat konto"
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = SteamTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            enabled = false,
                            onClick = {}
                        )
                        HorizontalDivider(color = SteamBorder)
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = SteamNeon
                                )
                            },
                            text = {
                                Text(
                                    text = "Sök uppdatering",
                                    color = SteamTextPrimary
                                )
                            },
                            onClick = {
                                accountMenuExpanded = false
                                onCheckForUpdate()
                            },
                            modifier = Modifier.testTag("check_update_menu_item")
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = if (hasUpdateAvailable) SteamNeon else SteamAccent
                                )
                            },
                            text = {
                                Text(
                                    text = if (hasUpdateAvailable) {
                                        "Uppdatera (v${BuildConfig.VERSION_NAME})"
                                    } else {
                                        "Appversion v${BuildConfig.VERSION_NAME}"
                                    },
                                    color = if (hasUpdateAvailable) SteamNeon else SteamTextPrimary
                                )
                            },
                            onClick = {
                                accountMenuExpanded = false
                                onOpenReleases()
                            },
                            modifier = Modifier.testTag("open_releases_button")
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    tint = SteamTextSecondary
                                )
                            },
                            text = {
                                Text(
                                    text = "Logga ut",
                                    color = SteamTextPrimary
                                )
                            },
                            onClick = {
                                accountMenuExpanded = false
                                onSignOut()
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Inställningar",
                    tint = SteamTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

