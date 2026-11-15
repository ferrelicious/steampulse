package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SteamProfilePrivateException
import com.example.data.SteamWorkshopRepository
import com.example.data.WorkshopFollowRepository
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamSyncUiState
import com.example.model.SteamWorkshopSyncProfile
import com.example.model.WorkshopFollowSource
import com.example.model.WorkshopFollowedFeedUiState
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopGamesUiState
import com.example.model.WorkshopLeaderboardMode
import com.example.model.WorkshopModItem
import com.example.model.WorkshopModsUiState
import com.example.model.WorkshopSectionTab
import com.example.model.WorkshopSortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkshopScreenUiState(
    val activeSectionTab: WorkshopSectionTab = WorkshopSectionTab.DISCOVER,
    val leaderboardMode: WorkshopLeaderboardMode = WorkshopLeaderboardMode.TWO_WEEKS,
    val gameSearchQuery: String = "",
    val onlyWorkshopPowerhouses: Boolean = false,
    val gamesState: WorkshopGamesUiState = WorkshopGamesUiState.Loading,
    val isSearchingStore: Boolean = false,
    val storeSearchResults: List<WorkshopGameSummary>? = null,
    val storeSearchError: String? = null,
    val selectedGame: WorkshopGameSummary? = null,
    val sortOrder: WorkshopSortOrder = WorkshopSortOrder.MOST_SUBSCRIBED,
    val modSearchQuery: String = "",
    val modsState: WorkshopModsUiState = WorkshopModsUiState.Loading,
    val isLoadingMoreMods: Boolean = false,
    val selectedModDetail: WorkshopModItem? = null,
    val isModDetailLoading: Boolean = false,
    val followedGames: List<FollowedWorkshopGame> = emptyList(),
    val followedMods: List<FollowedWorkshopMod> = emptyList(),
    val syncProfile: SteamWorkshopSyncProfile? = null,
    val followedNewModsFeed: WorkshopFollowedFeedUiState = WorkshopFollowedFeedUiState.Idle,
    val steamSyncState: SteamSyncUiState = SteamSyncUiState.Idle,
    val showSteamSyncDialog: Boolean = false,
    val steamProfileInput: String = ""
) {
    val isShowingStoreSearch: Boolean
        get() = storeSearchResults != null

    val followedGameIds: Set<Int>
        get() = followedGames.map { it.appId }.toSet()

    val bookmarkedModIds: Set<String>
        get() = followedMods.filter {
            it.source == WorkshopFollowSource.BOOKMARK || it.source == WorkshopFollowSource.BOTH
        }.map { it.publishedFileId }.toSet()

    val filteredGames: List<WorkshopGameSummary>
        get() {
            val remoteResults = storeSearchResults
            if (remoteResults != null) {
                return remoteResults
            }

            val all = (gamesState as? WorkshopGamesUiState.Success)?.games.orEmpty()
            val q = gameSearchQuery.trim()
            return all.filter { game ->
                val matchesFilter = !onlyWorkshopPowerhouses || game.isKnownWorkshopPowerhouse
                val matchesQuery = q.isEmpty() ||
                    game.name.contains(q, ignoreCase = true) ||
                    game.developer.contains(q, ignoreCase = true) ||
                    game.appId.toString() == q
                matchesFilter && matchesQuery
            }
        }
}

class WorkshopViewModel(
    private val repository: SteamWorkshopRepository = SteamWorkshopRepository(),
    private val followRepository: WorkshopFollowRepository? = null,
    private val userId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkshopScreenUiState())
    val uiState: StateFlow<WorkshopScreenUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val cacheByMode = mutableMapOf<WorkshopLeaderboardMode, List<WorkshopGameSummary>>()
    private var loadGamesJob: Job? = null
    private var searchStoreJob: Job? = null
    private var loadModsJob: Job? = null
    private var loadDetailJob: Job? = null
    private var loadFollowedFeedJob: Job? = null
    private var syncSteamJob: Job? = null
    private var lastFeedGameIds: Set<Int> = emptySet()

    init {
        loadTop100Games(WorkshopLeaderboardMode.TWO_WEEKS)
        startObservingFollowedState()
    }

    private fun startObservingFollowedState() {
        val repo = followRepository ?: return
        val uid = userId?.takeIf { it.isNotBlank() } ?: return

        viewModelScope.launch {
            repo.observeFollowedGames(uid)
                .catch { e -> Log.e(TAG, "Failed observing followed games", e) }
                .collect { games ->
                    _uiState.update { it.copy(followedGames = games) }
                    val newIds = games.map { it.appId }.toSet()
                    if (newIds != lastFeedGameIds) {
                        lastFeedGameIds = newIds
                        if (_uiState.value.activeSectionTab == WorkshopSectionTab.FOLLOWED || games.isEmpty()) {
                            refreshFollowedGamesFeed(games)
                        }
                    }
                }
        }

        viewModelScope.launch {
            repo.observeFollowedMods(uid)
                .catch { e -> Log.e(TAG, "Failed observing followed mods", e) }
                .collect { mods ->
                    _uiState.update { it.copy(followedMods = mods) }
                }
        }

        viewModelScope.launch {
            repo.observeSyncProfile(uid)
                .catch { e -> Log.e(TAG, "Failed observing sync profile", e) }
                .collect { profile ->
                    _uiState.update { current ->
                        val prefill = if (current.steamProfileInput.isBlank() && profile != null) {
                            profile.profileInput.ifBlank { profile.steamId64 }
                        } else {
                            current.steamProfileInput
                        }
                        current.copy(
                            syncProfile = profile,
                            steamProfileInput = prefill
                        )
                    }
                }
        }
    }

    fun selectSectionTab(tab: WorkshopSectionTab) {
        _uiState.update { it.copy(activeSectionTab = tab) }
        if (tab == WorkshopSectionTab.FOLLOWED) {
            val games = _uiState.value.followedGames
            val feedState = _uiState.value.followedNewModsFeed
            if (games.isNotEmpty() && (feedState is WorkshopFollowedFeedUiState.Idle || feedState is WorkshopFollowedFeedUiState.Error)) {
                refreshFollowedGamesFeed(games)
            }
        }
    }

    fun toggleFollowGame(game: WorkshopGameSummary) {
        val repo = followRepository
        if (repo == null) {
            _toastEvents.tryEmit("Logga in med Google för att följa spel.")
            return
        }
        val currentlyFollowed = _uiState.value.followedGameIds.contains(game.appId)
        viewModelScope.launch {
            val result = if (currentlyFollowed) {
                repo.unfollowGame(game.appId)
            } else {
                repo.followGame(game)
            }
            result.onSuccess {
                _toastEvents.tryEmit(
                    if (currentlyFollowed) {
                        "Slutade följa ${game.name}"
                    } else {
                        "Följer nu ${game.name} i Mina & Följda"
                    }
                )
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Kunde inte uppdatera följda spel.")
            }
        }
    }

    fun toggleBookmarkMod(mod: WorkshopModItem, gameNameOverride: String? = null) {
        val repo = followRepository
        if (repo == null) {
            _toastEvents.tryEmit("Logga in med Google för att bokmärka moddar.")
            return
        }
        val resolvedGameName = gameNameOverride?.takeIf { it.isNotBlank() }
            ?: _uiState.value.selectedGame?.name
            ?: _uiState.value.followedGames.firstOrNull { it.appId == mod.appId }?.gameName
            ?: _uiState.value.followedMods.firstOrNull { it.publishedFileId == mod.publishedFileId }?.gameName
            ?: if (mod.appId > 0) "Steam App #${mod.appId}" else "Steam Workshop"

        viewModelScope.launch {
            repo.toggleBookmarkMod(mod, resolvedGameName)
                .onSuccess { isBookmarked ->
                    _toastEvents.tryEmit(
                        if (isBookmarked) {
                            "Bokmärkte \"${mod.title}\""
                        } else {
                            "Tog bort bokmärket för \"${mod.title}\""
                        }
                    )
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(err.localizedMessage ?: "Kunde inte spara mod-bokmärke.")
                }
        }
    }

    fun removeFollowedMod(publishedFileId: String) {
        val repo = followRepository ?: return
        viewModelScope.launch {
            repo.removeFollowedMod(publishedFileId)
                .onSuccess {
                    _toastEvents.tryEmit("Tog bort modden från Mina & Följda")
                }
                .onFailure { err ->
                    _toastEvents.tryEmit(err.localizedMessage ?: "Kunde inte ta bort modden.")
                }
        }
    }

    fun refreshFollowedGamesFeed(gamesOverride: List<FollowedWorkshopGame>? = null) {
        val games = gamesOverride ?: _uiState.value.followedGames
        loadFollowedFeedJob?.cancel()
        if (games.isEmpty()) {
            _uiState.update { it.copy(followedNewModsFeed = WorkshopFollowedFeedUiState.Idle) }
            return
        }

        loadFollowedFeedJob = viewModelScope.launch {
            _uiState.update { it.copy(followedNewModsFeed = WorkshopFollowedFeedUiState.Loading) }
            try {
                val feed = repository.fetchLatestModsForGames(games)
                _uiState.update {
                    it.copy(followedNewModsFeed = WorkshopFollowedFeedUiState.Success(feed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed fetching followed games mod feed", e)
                _uiState.update {
                    it.copy(
                        followedNewModsFeed = WorkshopFollowedFeedUiState.Error(
                            e.localizedMessage ?: "Kunde inte hämta nya moddar för dina följda spel."
                        )
                    )
                }
            }
        }
    }

    fun openSteamSyncDialog() {
        _uiState.update { current ->
            val prefill = current.steamProfileInput.ifBlank {
                current.syncProfile?.profileInput.orEmpty()
                    .ifBlank { current.syncProfile?.steamId64.orEmpty() }
            }
            current.copy(
                showSteamSyncDialog = true,
                steamProfileInput = prefill,
                steamSyncState = if (current.steamSyncState is SteamSyncUiState.Syncing) {
                    current.steamSyncState
                } else {
                    SteamSyncUiState.Idle
                }
            )
        }
    }

    fun dismissSteamSyncDialog() {
        _uiState.update { current ->
            current.copy(
                showSteamSyncDialog = false,
                steamSyncState = if (current.steamSyncState is SteamSyncUiState.Error) {
                    SteamSyncUiState.Idle
                } else {
                    current.steamSyncState
                }
            )
        }
    }

    fun updateSteamProfileInput(input: String) {
        _uiState.update { it.copy(steamProfileInput = input) }
    }

    fun syncSteamSubscriptions() {
        val repo = followRepository
        if (repo == null) {
            _uiState.update {
                it.copy(steamSyncState = SteamSyncUiState.Error("Du måste vara inloggad med Google för att synka moddar."))
            }
            return
        }
        val rawInput = _uiState.value.steamProfileInput.trim()
        if (rawInput.isBlank()) {
            _uiState.update {
                it.copy(steamSyncState = SteamSyncUiState.Error("Ange ditt SteamID64 eller din Steam-profil-URL."))
            }
            return
        }

        syncSteamJob?.cancel()
        syncSteamJob = viewModelScope.launch {
            _uiState.update { it.copy(steamSyncState = SteamSyncUiState.Syncing) }
            try {
                val syncResult = repository.fetchSubscribedWorkshopMods(rawInput)
                val saveRes = repo.syncSubscribedMods(
                    steamId64 = syncResult.steamId64,
                    profileInput = rawInput,
                    syncedMods = syncResult.mods
                )
                saveRes.onSuccess { count ->
                    if (count > 0) {
                        _uiState.update {
                            it.copy(
                                steamSyncState = SteamSyncUiState.Success(syncResult.steamId64, count),
                                showSteamSyncDialog = false
                            )
                        }
                        _toastEvents.tryEmit("Synkade $count prenumererade moddar från Steam")
                    } else {
                        _uiState.update {
                            it.copy(
                                steamSyncState = SteamSyncUiState.EmptyDiagnostic(
                                    steamId64 = syncResult.steamId64,
                                    queriedUrl = syncResult.queriedUrl,
                                    httpStatusCode = syncResult.httpStatusCode,
                                    message = "0 publika Steam-prenumerationer returnerades. Kontrollera att \"Speldetaljer\" (Game details) är inställt på Offentlig i Steams sekretessinställningar."
                                ),
                                showSteamSyncDialog = true
                            )
                        }
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            steamSyncState = SteamSyncUiState.Error(
                                err.localizedMessage ?: "Kunde inte spara synkade moddar i molnet."
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SteamProfilePrivateException) {
                _uiState.update {
                    it.copy(steamSyncState = SteamSyncUiState.Error(e.message ?: "Steam-profilen är privat."))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing Steam subscriptions for '$rawInput'", e)
                _uiState.update {
                    it.copy(
                        steamSyncState = SteamSyncUiState.Error(
                            e.localizedMessage ?: "Kunde inte synka Steam-prenumerationer."
                        )
                    )
                }
            }
        }
    }

    fun selectLeaderboardMode(mode: WorkshopLeaderboardMode) {
        if (_uiState.value.leaderboardMode == mode &&
            _uiState.value.gamesState is WorkshopGamesUiState.Success &&
            _uiState.value.storeSearchResults == null
        ) {
            return
        }
        searchStoreJob?.cancel()
        _uiState.update {
            it.copy(
                leaderboardMode = mode,
                gameSearchQuery = "",
                isSearchingStore = false,
                storeSearchResults = null,
                storeSearchError = null
            )
        }
        loadTop100Games(mode)
    }

    fun updateGameSearchQuery(query: String) {
        _uiState.update { it.copy(gameSearchQuery = query) }
        val trimmed = query.trim()

        searchStoreJob?.cancel()
        if (trimmed.isEmpty() || trimmed.length < 2 || trimmed.all { it.isDigit() }) {
            _uiState.update {
                it.copy(
                    isSearchingStore = false,
                    storeSearchResults = null,
                    storeSearchError = null
                )
            }
            return
        }

        searchStoreJob = viewModelScope.launch {
            delay(400L)
            executeStoreSearch(trimmed)
        }
    }

    fun submitGameSearch() {
        val trimmed = _uiState.value.gameSearchQuery.trim()
        val numericId = trimmed.toIntOrNull()
        if (numericId != null && numericId > 0) {
            openCustomAppWorkshop(numericId)
            return
        }
        if (trimmed.length < 2) return
        searchStoreJob?.cancel()
        searchStoreJob = viewModelScope.launch {
            executeStoreSearch(trimmed)
        }
    }

    private suspend fun executeStoreSearch(trimmedQuery: String) {
        _uiState.update { it.copy(isSearchingStore = true, storeSearchError = null) }
        try {
            val rawResults = repository.searchSteamStoreGames(trimmedQuery)
            val cachedById = ((_uiState.value.gamesState as? WorkshopGamesUiState.Success)?.games)
                .orEmpty()
                .associateBy { it.appId }
            val mergedResults = rawResults.map { storeGame ->
                cachedById[storeGame.appId]?.let { cached ->
                    cached.copy(
                        customImageUrl = storeGame.customImageUrl.ifBlank { cached.customImageUrl }
                    )
                } ?: storeGame
            }
            _uiState.update { current ->
                if (current.gameSearchQuery.trim() == trimmedQuery) {
                    current.copy(
                        isSearchingStore = false,
                        storeSearchResults = mergedResults,
                        storeSearchError = null
                    )
                } else {
                    current
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed Steam Store search for '$trimmedQuery'", e)
            _uiState.update { current ->
                if (current.gameSearchQuery.trim() == trimmedQuery) {
                    current.copy(
                        isSearchingStore = false,
                        storeSearchError = e.localizedMessage ?: "Kunde inte söka i Steam Store."
                    )
                } else {
                    current
                }
            }
        }
    }

    fun toggleOnlyWorkshopPowerhouses() {
        _uiState.update { it.copy(onlyWorkshopPowerhouses = !it.onlyWorkshopPowerhouses) }
    }

    fun refreshTop100Games() {
        val mode = _uiState.value.leaderboardMode
        cacheByMode.remove(mode)
        loadTop100Games(mode)
    }

    private fun loadTop100Games(mode: WorkshopLeaderboardMode) {
        val cached = cacheByMode[mode]
        if (cached != null) {
            _uiState.update { it.copy(gamesState = WorkshopGamesUiState.Success(cached)) }
            return
        }

        loadGamesJob?.cancel()
        loadGamesJob = viewModelScope.launch {
            _uiState.update { it.copy(gamesState = WorkshopGamesUiState.Loading) }
            try {
                val games = repository.fetchTop100Games(mode)
                cacheByMode[mode] = games
                _uiState.update { it.copy(gamesState = WorkshopGamesUiState.Success(games)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load top 100 Steam games", e)
                _uiState.update {
                    it.copy(
                        gamesState = WorkshopGamesUiState.Error(
                            e.localizedMessage ?: "Kunde inte hämta Topp 100-spel från SteamSpy."
                        )
                    )
                }
            }
        }
    }

    fun openGameWorkshop(game: WorkshopGameSummary) {
        _uiState.update {
            it.copy(
                selectedGame = game,
                sortOrder = WorkshopSortOrder.MOST_SUBSCRIBED,
                modSearchQuery = "",
                modsState = WorkshopModsUiState.Loading,
                isLoadingMoreMods = false,
                selectedModDetail = null
            )
        }
        loadMods(appId = game.appId, sortOrder = WorkshopSortOrder.MOST_SUBSCRIBED, query = "", page = 1, append = false)
    }

    fun openCustomAppWorkshop(appId: Int, displayName: String? = null) {
        val existing = ((_uiState.value.gamesState as? WorkshopGamesUiState.Success)?.games)
            ?.firstOrNull { it.appId == appId }
        val game = existing ?: WorkshopGameSummary(
            rank = 0,
            appId = appId,
            name = displayName ?: "Steam App #$appId",
            developer = "Steam Community Workshop",
            publisher = "",
            ccu = 0,
            ownersRange = "—",
            positiveReviews = 0,
            negativeReviews = 0,
            averagePlaytimeMinutes = 0,
            priceCents = 0,
            discountPercent = 0,
            isKnownWorkshopPowerhouse = SteamWorkshopRepository.KNOWN_WORKSHOP_APP_IDS.contains(appId)
        )
        openGameWorkshop(game)
    }

    fun closeGameWorkshop() {
        loadModsJob?.cancel()
        loadDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedGame = null,
                selectedModDetail = null,
                isLoadingMoreMods = false
            )
        }
    }

    fun selectSortOrder(sortOrder: WorkshopSortOrder) {
        val game = _uiState.value.selectedGame ?: return
        if (_uiState.value.sortOrder == sortOrder && _uiState.value.modsState is WorkshopModsUiState.Success) {
            return
        }
        _uiState.update { it.copy(sortOrder = sortOrder) }
        loadMods(
            appId = game.appId,
            sortOrder = sortOrder,
            query = _uiState.value.modSearchQuery,
            page = 1,
            append = false
        )
    }

    fun updateModSearchQuery(query: String) {
        _uiState.update { it.copy(modSearchQuery = query) }
    }

    fun submitModSearch() {
        val game = _uiState.value.selectedGame ?: return
        loadMods(
            appId = game.appId,
            sortOrder = _uiState.value.sortOrder,
            query = _uiState.value.modSearchQuery,
            page = 1,
            append = false
        )
    }

    fun refreshMods() {
        val game = _uiState.value.selectedGame ?: return
        loadMods(
            appId = game.appId,
            sortOrder = _uiState.value.sortOrder,
            query = _uiState.value.modSearchQuery,
            page = 1,
            append = false
        )
    }

    fun loadMoreMods() {
        val game = _uiState.value.selectedGame ?: return
        val currentSuccess = _uiState.value.modsState as? WorkshopModsUiState.Success ?: return
        if (!currentSuccess.hasMore || _uiState.value.isLoadingMoreMods) return

        loadMods(
            appId = game.appId,
            sortOrder = _uiState.value.sortOrder,
            query = _uiState.value.modSearchQuery,
            page = currentSuccess.currentPage + 1,
            append = true
        )
    }

    private fun loadMods(
        appId: Int,
        sortOrder: WorkshopSortOrder,
        query: String,
        page: Int,
        append: Boolean
    ) {
        loadModsJob?.cancel()
        loadModsJob = viewModelScope.launch {
            if (append) {
                _uiState.update { it.copy(isLoadingMoreMods = true) }
            } else {
                _uiState.update { it.copy(modsState = WorkshopModsUiState.Loading, isLoadingMoreMods = false) }
            }

            try {
                val result = repository.fetchWorkshopMods(
                    appId = appId,
                    sortOrder = sortOrder,
                    searchQuery = query,
                    page = page
                )
                val previousMods = if (append) {
                    (_uiState.value.modsState as? WorkshopModsUiState.Success)?.mods.orEmpty()
                } else {
                    emptyList()
                }
                val merged = (previousMods + result.mods).distinctBy { it.publishedFileId }
                _uiState.update {
                    it.copy(
                        modsState = WorkshopModsUiState.Success(
                            mods = merged,
                            hasMore = result.hasMore,
                            currentPage = page,
                            diagnosticInfo = result.diagnosticInfo
                        ),
                        isLoadingMoreMods = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Steam Workshop mods for appId=$appId", e)
                if (append) {
                    _uiState.update { it.copy(isLoadingMoreMods = false) }
                } else {
                    _uiState.update {
                        it.copy(
                            modsState = WorkshopModsUiState.Error(
                                e.localizedMessage ?: "Kunde inte hämta Steam Workshop-moddar."
                            ),
                            isLoadingMoreMods = false
                        )
                    }
                }
            }
        }
    }

    fun openModDetail(mod: WorkshopModItem) {
        _uiState.update { it.copy(selectedModDetail = mod, isModDetailLoading = true) }
        loadDetailJob?.cancel()
        loadDetailJob = viewModelScope.launch {
            try {
                val enriched = repository.fetchModDetails(mod)
                _uiState.update { current ->
                    if (current.selectedModDetail?.publishedFileId == mod.publishedFileId) {
                        current.copy(selectedModDetail = enriched, isModDetailLoading = false)
                    } else {
                        current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isModDetailLoading = false) }
            }
        }
    }

    fun closeModDetail() {
        loadDetailJob?.cancel()
        _uiState.update { it.copy(selectedModDetail = null, isModDetailLoading = false) }
    }

    private companion object {
        const val TAG = "WorkshopViewModel"
    }
}
