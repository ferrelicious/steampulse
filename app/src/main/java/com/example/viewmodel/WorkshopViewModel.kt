package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SteamProfilePrivateException
import com.example.data.SteamWorkshopRepository
import com.example.data.WorkshopFollowRepository
import com.example.data.WorkshopLocalStore
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
import com.google.firebase.Timestamp
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
    private val userId: String? = null,
    private val localStore: WorkshopLocalStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkshopScreenUiState())
    val uiState: StateFlow<WorkshopScreenUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val cacheByMode = mutableMapOf<WorkshopLeaderboardMode, List<WorkshopGameSummary>>()
    private val pendingLocalGameIds = mutableSetOf<Int>()
    private val removedGameIds = mutableSetOf<Int>()
    private val pendingLocalModIds = mutableSetOf<String>()
    private val removedModIds = mutableSetOf<String>()
    private var hasPendingLocalSyncProfile = false

    private var loadGamesJob: Job? = null
    private var searchStoreJob: Job? = null
    private var loadModsJob: Job? = null
    private var loadDetailJob: Job? = null
    private var loadFollowedFeedJob: Job? = null
    private var syncSteamJob: Job? = null
    private var lastFeedGameIds: Set<Int> = emptySet()

    init {
        hydrateFromLocalStore()
        loadTop100Games(WorkshopLeaderboardMode.TWO_WEEKS)
        startObservingFollowedState()
    }

    private fun storeUserKey(): String = userId?.takeIf { it.isNotBlank() } ?: "default"

    private fun hydrateFromLocalStore() {
        val store = localStore ?: return
        val snapshot = store.loadSnapshot(storeUserKey())
        if (snapshot.followedGames.isEmpty() && snapshot.followedMods.isEmpty() && snapshot.syncProfile == null) {
            return
        }
        _uiState.update { current ->
            val prefill = current.steamProfileInput.ifBlank {
                snapshot.syncProfile?.profileInput.orEmpty()
                    .ifBlank { snapshot.syncProfile?.steamId64.orEmpty() }
            }
            current.copy(
                followedGames = snapshot.followedGames,
                followedMods = snapshot.followedMods,
                syncProfile = snapshot.syncProfile,
                steamProfileInput = prefill
            )
        }
        lastFeedGameIds = snapshot.followedGames.map { it.appId }.toSet()
    }

    private fun startObservingFollowedState() {
        val repo = followRepository ?: return
        val uid = userId?.takeIf { it.isNotBlank() } ?: return
        val storeKey = storeUserKey()

        viewModelScope.launch {
            repo.observeFollowedGames(uid)
                .catch { e -> Log.w(TAG, "Failed observing followed games (using local cache)", e) }
                .collect { remoteGames ->
                    val remoteIds = remoteGames.map { it.appId }.toSet()
                    pendingLocalGameIds.removeAll(remoteIds)
                    removedGameIds.retainAll(remoteIds)

                    val currentGames = _uiState.value.followedGames
                    val mergedGames = if (remoteGames.isEmpty() && currentGames.isNotEmpty() && removedGameIds.isEmpty()) {
                        // Do not let an empty offline Firestore cache wipe locally persisted games.
                        currentGames
                    } else {
                        val filteredRemote = remoteGames.filterNot { it.appId in removedGameIds }
                        val keptPending = currentGames.filter { it.appId in pendingLocalGameIds }
                        (keptPending + filteredRemote)
                            .distinctBy { it.appId }
                            .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                    }

                    _uiState.update { it.copy(followedGames = mergedGames) }
                    localStore?.saveFollowedGames(storeKey, mergedGames)

                    val newIds = mergedGames.map { it.appId }.toSet()
                    if (newIds != lastFeedGameIds) {
                        lastFeedGameIds = newIds
                        if (_uiState.value.activeSectionTab == WorkshopSectionTab.FOLLOWED || mergedGames.isEmpty()) {
                            refreshFollowedGamesFeed(mergedGames)
                        }
                    }
                }
        }

        viewModelScope.launch {
            repo.observeFollowedMods(uid)
                .catch { e -> Log.w(TAG, "Failed observing followed mods (using local cache)", e) }
                .collect { remoteMods ->
                    val remoteIds = remoteMods.map { it.publishedFileId }.toSet()
                    removedModIds.retainAll(remoteIds)

                    val currentMods = _uiState.value.followedMods
                    val mergedMods = if (remoteMods.isEmpty() && currentMods.isNotEmpty() && removedModIds.isEmpty()) {
                        // Do not let an empty offline Firestore cache wipe locally synced/bookmarked mods.
                        currentMods
                    } else {
                        val keptPending = currentMods.filter { it.publishedFileId in pendingLocalModIds }
                        val filteredRemote = remoteMods.filterNot { it.publishedFileId in removedModIds }
                        (keptPending + filteredRemote)
                            .distinctBy { it.publishedFileId }
                            .sortedByDescending { maxOf(it.updatedEpochSec, it.updatedAt?.seconds ?: 0L) }
                    }

                    _uiState.update { it.copy(followedMods = mergedMods) }
                    localStore?.saveFollowedMods(storeKey, mergedMods)
                }
        }

        viewModelScope.launch {
            repo.observeSyncProfile(uid)
                .catch { e -> Log.w(TAG, "Failed observing sync profile (using local cache)", e) }
                .collect { remoteProfile ->
                    if (remoteProfile == null) return@collect
                    _uiState.update { current ->
                        val localProfile = current.syncProfile
                        val effectiveProfile = if (hasPendingLocalSyncProfile && localProfile != null) {
                            if (remoteProfile.steamId64 == localProfile.steamId64 &&
                                remoteProfile.syncedCount == localProfile.syncedCount
                            ) {
                                hasPendingLocalSyncProfile = false
                                remoteProfile
                            } else {
                                localProfile
                            }
                        } else {
                            remoteProfile
                        }
                        val prefill = if (current.steamProfileInput.isBlank()) {
                            effectiveProfile.profileInput.ifBlank { effectiveProfile.steamId64 }
                        } else {
                            current.steamProfileInput
                        }
                        localStore?.saveSyncProfile(storeKey, effectiveProfile)
                        current.copy(
                            syncProfile = effectiveProfile,
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
        val currentlyFollowed = _uiState.value.followedGameIds.contains(game.appId)
        val storeKey = storeUserKey()

        val updatedGames = if (currentlyFollowed) {
            pendingLocalGameIds.remove(game.appId)
            removedGameIds.add(game.appId)
            _uiState.value.followedGames.filterNot { it.appId == game.appId }
        } else {
            removedGameIds.remove(game.appId)
            pendingLocalGameIds.add(game.appId)
            val nowTs = Timestamp.now()
            val newFollowed = FollowedWorkshopGame(
                id = game.appId.toString(),
                userId = userId.orEmpty(),
                appId = game.appId,
                gameName = game.name.trim().ifBlank { "Steam App #${game.appId}" }.take(160),
                developer = game.developer.trim().take(120),
                headerImageUrl = game.customImageUrl.ifBlank { game.headerImageUrl }.trim().take(500),
                createdAt = nowTs,
                updatedAt = nowTs
            )
            (listOf(newFollowed) + _uiState.value.followedGames).distinctBy { it.appId }
        }

        _uiState.update { current ->
            val nextFeedState = if (updatedGames.isEmpty()) {
                WorkshopFollowedFeedUiState.Idle
            } else if (current.activeSectionTab != WorkshopSectionTab.FOLLOWED) {
                WorkshopFollowedFeedUiState.Idle
            } else {
                current.followedNewModsFeed
            }
            current.copy(
                followedGames = updatedGames,
                followedNewModsFeed = nextFeedState
            )
        }
        localStore?.saveFollowedGames(storeKey, updatedGames)
        lastFeedGameIds = updatedGames.map { it.appId }.toSet()
        if (_uiState.value.activeSectionTab == WorkshopSectionTab.FOLLOWED || updatedGames.isEmpty()) {
            refreshFollowedGamesFeed(updatedGames)
        }

        _toastEvents.tryEmit(
            if (currentlyFollowed) {
                "Slutade följa ${game.name}"
            } else {
                "Följer nu ${game.name} i Mina & Följda"
            }
        )

        val repo = followRepository ?: return
        viewModelScope.launch {
            try {
                val res = if (currentlyFollowed) {
                    repo.unfollowGame(game.appId)
                } else {
                    repo.followGame(game)
                }
                res.onSuccess {
                    if (currentlyFollowed) {
                        removedGameIds.remove(game.appId)
                    } else {
                        pendingLocalGameIds.remove(game.appId)
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Background Firestore follow sync deferred for appId=${game.appId}", err)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background Firestore follow sync failed cleanly for appId=${game.appId}", e)
            }
        }
    }

    fun toggleBookmarkMod(mod: WorkshopModItem, gameNameOverride: String? = null) {
        val storeKey = storeUserKey()
        val existingLocal = _uiState.value.followedMods.firstOrNull { it.publishedFileId == mod.publishedFileId }
        val resolvedGameName = gameNameOverride?.takeIf { it.isNotBlank() }
            ?: _uiState.value.selectedGame?.name
            ?: _uiState.value.followedGames.firstOrNull { it.appId == mod.appId }?.gameName
            ?: existingLocal?.gameName?.takeIf { it.isNotBlank() }
            ?: if (mod.appId > 0) "Steam App #${mod.appId}" else "Steam Workshop"

        val nowTs = Timestamp.now()
        val existingSource = existingLocal?.source
        val isNowBookmarked: Boolean
        val updatedMods = when (existingSource) {
            WorkshopFollowSource.BOOKMARK -> {
                isNowBookmarked = false
                pendingLocalModIds.remove(mod.publishedFileId)
                removedModIds.add(mod.publishedFileId)
                _uiState.value.followedMods.filterNot { it.publishedFileId == mod.publishedFileId }
            }

            WorkshopFollowSource.BOTH -> {
                isNowBookmarked = false
                removedModIds.remove(mod.publishedFileId)
                pendingLocalModIds.add(mod.publishedFileId)
                _uiState.value.followedMods.map { item ->
                    if (item.publishedFileId == mod.publishedFileId) {
                        item.copy(source = WorkshopFollowSource.STEAM_SYNC, updatedAt = nowTs)
                    } else {
                        item
                    }
                }
            }

            WorkshopFollowSource.STEAM_SYNC -> {
                isNowBookmarked = true
                removedModIds.remove(mod.publishedFileId)
                pendingLocalModIds.add(mod.publishedFileId)
                _uiState.value.followedMods.map { item ->
                    if (item.publishedFileId == mod.publishedFileId) {
                        item.copy(
                            gameName = resolvedGameName.ifBlank { item.gameName },
                            title = mod.title.ifBlank { item.title },
                            author = mod.author.ifBlank { item.author },
                            previewUrl = mod.previewUrl.ifBlank { item.previewUrl },
                            source = WorkshopFollowSource.BOTH,
                            updatedAt = nowTs
                        )
                    } else {
                        item
                    }
                }
            }

            else -> {
                isNowBookmarked = true
                removedModIds.remove(mod.publishedFileId)
                pendingLocalModIds.add(mod.publishedFileId)
                val newMod = FollowedWorkshopMod(
                    id = mod.publishedFileId,
                    userId = userId.orEmpty(),
                    publishedFileId = mod.publishedFileId,
                    appId = mod.appId.coerceIn(0, 100_000_000),
                    gameName = resolvedGameName,
                    title = mod.title.trim().ifBlank { "Workshop Item #${mod.publishedFileId}" }.take(240),
                    author = mod.author.trim().take(120),
                    previewUrl = mod.previewUrl.trim().take(600),
                    itemUrl = mod.itemUrl.trim()
                        .ifBlank { "https://steamcommunity.com/sharedfiles/filedetails/?id=${mod.publishedFileId}" }
                        .take(400),
                    source = WorkshopFollowSource.BOOKMARK,
                    updatedEpochSec = (mod.updatedEpochSec ?: 0L).coerceAtLeast(0L),
                    subscriptions = (mod.subscriptions ?: 0L).coerceAtLeast(0L),
                    createdAt = nowTs,
                    updatedAt = nowTs
                )
                (listOf(newMod) + _uiState.value.followedMods)
                    .distinctBy { it.publishedFileId }
                    .sortedByDescending { maxOf(it.updatedEpochSec, it.updatedAt?.seconds ?: 0L) }
            }
        }

        _uiState.update { it.copy(followedMods = updatedMods) }
        localStore?.saveFollowedMods(storeKey, updatedMods)

        _toastEvents.tryEmit(
            if (isNowBookmarked) {
                "Bokmärkte \"${mod.title}\""
            } else {
                "Tog bort bokmärket för \"${mod.title}\""
            }
        )

        val repo = followRepository ?: return
        viewModelScope.launch {
            try {
                repo.toggleBookmarkMod(mod, resolvedGameName, existingSource)
                    .onSuccess {
                        if (isNowBookmarked || existingSource == WorkshopFollowSource.BOTH) {
                            pendingLocalModIds.remove(mod.publishedFileId)
                        } else {
                            removedModIds.remove(mod.publishedFileId)
                        }
                    }
                    .onFailure { err ->
                        Log.w(TAG, "Background Firestore bookmark sync deferred for ${mod.publishedFileId}", err)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background Firestore bookmark sync failed cleanly for ${mod.publishedFileId}", e)
            }
        }
    }

    fun removeFollowedMod(publishedFileId: String) {
        val storeKey = storeUserKey()
        pendingLocalModIds.remove(publishedFileId)
        removedModIds.add(publishedFileId)
        val updatedMods = _uiState.value.followedMods.filterNot { it.publishedFileId == publishedFileId }
        _uiState.update { it.copy(followedMods = updatedMods) }
        localStore?.saveFollowedMods(storeKey, updatedMods)
        _toastEvents.tryEmit("Tog bort modden från Mina & Följda")

        val repo = followRepository ?: return
        viewModelScope.launch {
            try {
                repo.removeFollowedMod(publishedFileId)
                    .onSuccess { removedModIds.remove(publishedFileId) }
                    .onFailure { err ->
                        Log.w(TAG, "Background Firestore mod removal deferred for $publishedFileId", err)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background Firestore mod removal failed cleanly for $publishedFileId", e)
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
        val rawInput = _uiState.value.steamProfileInput.trim()
        if (rawInput.isBlank()) {
            _uiState.update {
                it.copy(steamSyncState = SteamSyncUiState.Error("Ange ditt SteamID64 eller din Steam-profil-URL."))
            }
            return
        }

        val storeKey = storeUserKey()
        syncSteamJob?.cancel()
        syncSteamJob = viewModelScope.launch {
            _uiState.update { it.copy(steamSyncState = SteamSyncUiState.Syncing) }
            try {
                // Step 1: Fetch and parse directly from steamcommunity.com FIRST, completely decoupled from Firestore.
                val syncResult = repository.fetchSubscribedWorkshopMods(rawInput)
                val nowTs = Timestamp.now()
                val newProfile = SteamWorkshopSyncProfile(
                    id = WorkshopFollowRepository.SYNC_DOC_ID,
                    userId = userId.orEmpty(),
                    steamId64 = syncResult.steamId64,
                    profileInput = rawInput,
                    syncedCount = syncResult.mods.size,
                    createdAt = _uiState.value.syncProfile?.createdAt ?: nowTs,
                    updatedAt = nowTs
                )

                if (syncResult.mods.isNotEmpty()) {
                    // Step 2: Merge into UI & local persistent store immediately so the user sees mods even offline.
                    val existingById = _uiState.value.followedMods.associateBy { it.publishedFileId }
                    val syncedById = linkedMapOf<String, FollowedWorkshopMod>()
                    syncResult.mods.forEach { mod ->
                        val existing = existingById[mod.publishedFileId]
                        val targetSource = if (
                            existing?.source == WorkshopFollowSource.BOOKMARK ||
                            existing?.source == WorkshopFollowSource.BOTH
                        ) {
                            WorkshopFollowSource.BOTH
                        } else {
                            WorkshopFollowSource.STEAM_SYNC
                        }
                        removedModIds.remove(mod.publishedFileId)
                        pendingLocalModIds.add(mod.publishedFileId)
                        syncedById[mod.publishedFileId] = mod.copy(
                            id = mod.publishedFileId,
                            userId = userId.orEmpty(),
                            source = targetSource,
                            createdAt = existing?.createdAt ?: nowTs,
                            updatedAt = nowTs
                        )
                    }
                    val preservedBookmarks = _uiState.value.followedMods.filter {
                        it.publishedFileId !in syncedById
                    }
                    val mergedMods = (syncedById.values + preservedBookmarks)
                        .distinctBy { it.publishedFileId }
                        .sortedByDescending { maxOf(it.updatedEpochSec, it.updatedAt?.seconds ?: 0L) }

                    hasPendingLocalSyncProfile = true
                    localStore?.saveFollowedMods(storeKey, mergedMods)
                    localStore?.saveSyncProfile(storeKey, newProfile)

                    _uiState.update {
                        it.copy(
                            followedMods = mergedMods,
                            syncProfile = newProfile,
                            steamSyncState = SteamSyncUiState.Success(syncResult.steamId64, syncResult.mods.size),
                            showSteamSyncDialog = false
                        )
                    }
                    _toastEvents.tryEmit("Synkade ${syncResult.mods.size} Steam-favoriter")
                } else {
                    hasPendingLocalSyncProfile = true
                    localStore?.saveSyncProfile(storeKey, newProfile)
                    _uiState.update {
                        it.copy(
                            syncProfile = newProfile,
                            steamSyncState = SteamSyncUiState.EmptyDiagnostic(
                                steamId64 = syncResult.steamId64,
                                queriedUrl = syncResult.queriedUrl,
                                httpStatusCode = syncResult.httpStatusCode,
                                message = "0 publika Steam-favoriter (myfavorites) hittades. Steam begränsar \"mysubscriptions\" till inloggade webbläsarsessioner — stjärnmarkera/favoritmarkera moddar på Steam (\"Favoriter\") eller bokmärk dem direkt i SteamPulse."
                            ),
                            showSteamSyncDialog = true
                        )
                    }
                }

                // Step 3: Non-blocking background sync to Firestore; never fails or blocks the Steam dialog.
                val repo = followRepository
                if (repo != null && !userId.isNullOrBlank()) {
                    viewModelScope.launch {
                        try {
                            repo.syncSubscribedMods(
                                steamId64 = syncResult.steamId64,
                                profileInput = rawInput,
                                syncedMods = syncResult.mods
                            ).onSuccess {
                                pendingLocalModIds.removeAll(syncResult.mods.map { it.publishedFileId }.toSet())
                                hasPendingLocalSyncProfile = false
                            }.onFailure { err ->
                                Log.w(TAG, "Background Firestore sync deferred after Steam fetch", err)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Background Firestore sync failed cleanly after Steam fetch", e)
                        }
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
                            e.localizedMessage ?: "Kunde inte synka Steam-favoriter."
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
