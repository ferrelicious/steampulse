package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SteamProfilePrivateException
import com.example.data.SteamSessionExpiredException
import com.example.data.SteamWorkshopRepository
import com.example.data.WorkshopFollowRepository
import com.example.data.WorkshopLocalStore
import com.example.model.FollowedModsFilterMode
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
    val showSteamWebViewLoginDialog: Boolean = false,
    val steamProfileInput: String = "",
    val hasSteamSession: Boolean = false,
    val steamSessionSteamId64: String = "",
    val previousWorkshopVisitEpochSec: Long = 0L,
    val activeGamePreviousVisitEpochSec: Long = 0L,
    val followedGamesPreviousVisitEpochSec: Map<Int, Long> = emptyMap(),
    val newModNotificationsEnabled: Boolean = true,
    val checkIntervalHours: Long = WorkshopLocalStore.DEFAULT_CHECK_INTERVAL_HOURS,
    val followedModsFilterMode: FollowedModsFilterMode = FollowedModsFilterMode.ALL,
    val seenModUpdateEpochSec: Map<String, Long> = emptyMap()
) {
    val isShowingStoreSearch: Boolean
        get() = storeSearchResults != null

    val followedGameIds: Set<Int>
        get() = followedGames.map { it.appId }.toSet()

    val bookmarkedModIds: Set<String>
        get() = followedMods.filter {
            it.source == WorkshopFollowSource.BOOKMARK || it.source == WorkshopFollowSource.BOTH
        }.map { it.publishedFileId }.toSet()

    val updatedFollowedModsCount: Int
        get() = followedMods.count {
            it.toModItem().isUpdatedMod(seenModUpdateEpochSec[it.publishedFileId] ?: 0L)
        }

    val filteredFollowedMods: List<FollowedWorkshopMod>
        get() = when (followedModsFilterMode) {
            FollowedModsFilterMode.ALL -> followedMods
            FollowedModsFilterMode.RECENTLY_UPDATED -> followedMods
                .filter { it.toModItem().isUpdatedMod(seenModUpdateEpochSec[it.publishedFileId] ?: 0L) }
                .sortedByDescending { it.updatedEpochSec }
        }

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
    private var steamSessionCookie: String = ""
    private var hasRecordedSessionVisit = false
    private val recordedFollowedGameVisits = mutableSetOf<Int>()

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
        val storeKey = storeUserKey()
        val snapshot = store.loadSnapshot(storeKey)
        val prevWorkshopVisit = store.getWorkshopLastVisitEpochSec(storeKey)
        val followedPrevMap = store.loadGameLastVisitMap(storeKey, snapshot.followedGames.map { it.appId })
        steamSessionCookie = snapshot.steamSessionCookie
        val sessionId64 = snapshot.steamSessionSteamId64
            .ifBlank { SteamWorkshopRepository.extractSteamId64FromCookie(snapshot.steamSessionCookie).orEmpty() }
        val isSessionActive = snapshot.steamSessionCookie.isNotBlank() && sessionId64.isNotBlank()

        val seenUpdates = store.initializeModUpdateBaselinesIfMissing(storeKey, snapshot.followedMods)

        _uiState.update { current ->
            val prefill = current.steamProfileInput.ifBlank {
                snapshot.syncProfile?.profileInput.orEmpty()
                    .ifBlank { snapshot.syncProfile?.steamId64.orEmpty() }
                    .ifBlank { sessionId64 }
            }
            current.copy(
                followedGames = snapshot.followedGames,
                followedMods = snapshot.followedMods,
                syncProfile = snapshot.syncProfile,
                steamProfileInput = prefill,
                hasSteamSession = isSessionActive,
                steamSessionSteamId64 = if (isSessionActive) sessionId64 else "",
                previousWorkshopVisitEpochSec = prevWorkshopVisit,
                followedGamesPreviousVisitEpochSec = followedPrevMap,
                newModNotificationsEnabled = store.isNewModNotificationsEnabled(storeKey),
                checkIntervalHours = store.getCheckIntervalHours(storeKey),
                seenModUpdateEpochSec = seenUpdates
            )
        }
        lastFeedGameIds = snapshot.followedGames.map { it.appId }.toSet()
    }

    fun selectFollowedModsFilter(mode: FollowedModsFilterMode) {
        _uiState.update { it.copy(followedModsFilterMode = mode) }
    }

    fun selectCheckIntervalHours(hours: Long) {
        val storeKey = storeUserKey()
        localStore?.setCheckIntervalHours(storeKey, hours)
        val sanitized = localStore?.getCheckIntervalHours(storeKey)
            ?: if (hours in WorkshopLocalStore.SUPPORTED_CHECK_INTERVAL_HOURS) hours else WorkshopLocalStore.DEFAULT_CHECK_INTERVAL_HOURS
        _uiState.update { it.copy(checkIntervalHours = sanitized) }
    }

    fun markModUpdateSeen(mod: WorkshopModItem) {
        val storeKey = storeUserKey()
        val storedModUpdated = _uiState.value.followedMods
            .firstOrNull { it.publishedFileId == mod.publishedFileId }
            ?.updatedEpochSec
            ?: 0L
        val effectiveUpdated = maxOf(mod.updatedEpochSec ?: 0L, storedModUpdated)
        if (effectiveUpdated <= 0L) return

        localStore?.saveModUpdateSeen(storeKey, mod.publishedFileId, effectiveUpdated)
        _uiState.update { current ->
            val prev = current.seenModUpdateEpochSec[mod.publishedFileId] ?: 0L
            if (effectiveUpdated > prev) {
                current.copy(
                    seenModUpdateEpochSec = current.seenModUpdateEpochSec + (mod.publishedFileId to effectiveUpdated)
                )
            } else {
                current
            }
        }
    }

    fun setNewModNotificationsEnabled(enabled: Boolean) {
        val storeKey = storeUserKey()
        localStore?.setNewModNotificationsEnabled(storeKey, enabled)
        _uiState.update { it.copy(newModNotificationsEnabled = enabled) }
    }

    fun emitToast(message: String) {
        if (message.isNotBlank()) {
            _toastEvents.tryEmit(message)
        }
    }

    fun markWorkshopVisited() {
        if (hasRecordedSessionVisit) return
        hasRecordedSessionVisit = true
        val store = localStore ?: return
        val storeKey = storeUserKey()
        val nowEpochSec = System.currentTimeMillis() / 1000L
        store.saveWorkshopLastVisitEpochSec(storeKey, nowEpochSec)
    }

    private fun startObservingFollowedState() {
        val repo = followRepository ?: return
        val uid = userId?.takeIf { it.isNotBlank() && it != "guest" } ?: return
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

                    val existingVisitMap = _uiState.value.followedGamesPreviousVisitEpochSec
                    val updatedVisitMap = existingVisitMap.toMutableMap()
                    val store = localStore
                    val overallPrev = _uiState.value.previousWorkshopVisitEpochSec
                    mergedGames.forEach { game ->
                        if (!updatedVisitMap.containsKey(game.appId)) {
                            val saved = store?.getGameLastVisitEpochSec(storeKey, game.appId) ?: 0L
                            updatedVisitMap[game.appId] = if (saved > 0L) saved else overallPrev
                        }
                    }

                    _uiState.update {
                        it.copy(
                            followedGames = mergedGames,
                            followedGamesPreviousVisitEpochSec = updatedVisitMap
                        )
                    }
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
                    val currentById = currentMods.associateBy { it.publishedFileId }
                    val mergedMods = if (remoteMods.isEmpty() && currentMods.isNotEmpty() && removedModIds.isEmpty()) {
                        // Do not let an empty offline Firestore cache wipe locally synced/bookmarked mods.
                        currentMods
                    } else {
                        val keptPending = currentMods.filter { it.publishedFileId in pendingLocalModIds }
                        val filteredRemote = remoteMods
                            .filterNot { it.publishedFileId in removedModIds }
                            .map { remote ->
                                val localCreated = currentById[remote.publishedFileId]?.createdEpochSec ?: 0L
                                if (remote.createdEpochSec <= 0L && localCreated > 0L) {
                                    remote.copy(createdEpochSec = localCreated)
                                } else {
                                    remote
                                }
                            }
                        (keptPending + filteredRemote)
                            .distinctBy { it.publishedFileId }
                            .sortedByDescending { maxOf(it.updatedEpochSec, it.updatedAt?.seconds ?: 0L) }
                    }

                    val seenUpdates = localStore?.initializeModUpdateBaselinesIfMissing(
                        userId = storeKey,
                        mods = mergedMods,
                        previousLocalById = currentById
                    )
                    _uiState.update { current ->
                        current.copy(
                            followedMods = mergedMods,
                            seenModUpdateEpochSec = seenUpdates ?: current.seenModUpdateEpochSec
                        )
                    }
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
            val store = localStore
            val storeKey = storeUserKey()
            // Pick up any updated timestamps persisted in the background by WorkshopCheckWorker.
            if (store != null) {
                val localSnapshot = store.loadSnapshot(storeKey)
                val localById = localSnapshot.followedMods.associateBy { it.publishedFileId }
                if (localById.isNotEmpty()) {
                    val currentMods = _uiState.value.followedMods
                    val currentById = currentMods.associateBy { it.publishedFileId }
                    val mergedLatest = currentMods.map { cur ->
                        val local = localById[cur.publishedFileId]
                        if (local != null && local.updatedEpochSec > cur.updatedEpochSec) {
                            cur.copy(
                                updatedEpochSec = local.updatedEpochSec,
                                createdEpochSec = cur.createdEpochSec.takeIf { it > 0L } ?: local.createdEpochSec
                            )
                        } else {
                            cur
                        }
                    }
                    if (mergedLatest != currentMods) {
                        val seenUpdates = store.initializeModUpdateBaselinesIfMissing(
                            userId = storeKey,
                            mods = mergedLatest,
                            previousLocalById = currentById
                        )
                        _uiState.update {
                            it.copy(
                                followedMods = mergedLatest,
                                seenModUpdateEpochSec = seenUpdates
                            )
                        }
                    }
                }
            }

            val games = _uiState.value.followedGames
            if (store != null && games.isNotEmpty()) {
                val storeKey = storeUserKey()
                val nowEpochSec = System.currentTimeMillis() / 1000L
                val existingMap = _uiState.value.followedGamesPreviousVisitEpochSec.toMutableMap()
                val overallPrev = _uiState.value.previousWorkshopVisitEpochSec
                games.forEach { game ->
                    if (!existingMap.containsKey(game.appId)) {
                        val saved = store.getGameLastVisitEpochSec(storeKey, game.appId)
                        existingMap[game.appId] = if (saved > 0L) saved else overallPrev
                    }
                    if (recordedFollowedGameVisits.add(game.appId)) {
                        store.saveGameLastVisitEpochSec(storeKey, game.appId, nowEpochSec)
                    }
                }
                _uiState.update { it.copy(followedGamesPreviousVisitEpochSec = existingMap) }
            }

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
        if (currentlyFollowed) {
            localStore?.clearLastNotifiedForGame(storeKey, game.appId)
        }
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
        if (userId.isNullOrBlank() || userId == "guest") return
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
                            createdEpochSec = mod.createdEpochSec ?: item.createdEpochSec,
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
                    createdEpochSec = (mod.createdEpochSec ?: 0L).coerceAtLeast(0L),
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
        if (userId.isNullOrBlank() || userId == "guest") return
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
        localStore?.saveFollowedMods(storeKey, updatedMods)
        localStore?.removeSeenModUpdate(storeKey, publishedFileId)
        _uiState.update { current ->
            current.copy(
                followedMods = updatedMods,
                seenModUpdateEpochSec = current.seenModUpdateEpochSec - publishedFileId
            )
        }
        _toastEvents.tryEmit("Tog bort modden från Mina & Följda")

        val repo = followRepository ?: return
        if (userId.isNullOrBlank() || userId == "guest") return
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
                    .ifBlank { current.steamSessionSteamId64 }
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

    fun openSteamWebViewLoginDialog() {
        _uiState.update { it.copy(showSteamWebViewLoginDialog = true) }
    }

    fun dismissSteamWebViewLoginDialog() {
        _uiState.update { it.copy(showSteamWebViewLoginDialog = false) }
    }

    fun onSteamWebViewAuthenticated(steamId64: String, cookieString: String) {
        val cleanCookie = cookieString.trim()
        val cleanSteamId = steamId64.trim()
        if (cleanCookie.isBlank() || cleanSteamId.isBlank()) return

        // Strictly local device storage — NEVER uploaded to Firebase Firestore.
        steamSessionCookie = cleanCookie
        localStore?.saveSteamSession(storeUserKey(), cleanSteamId, cleanCookie)

        _uiState.update { current ->
            current.copy(
                showSteamWebViewLoginDialog = false,
                hasSteamSession = true,
                steamSessionSteamId64 = cleanSteamId,
                steamProfileInput = cleanSteamId,
                steamSyncState = SteamSyncUiState.Idle
            )
        }
        syncSteamSubscriptions()
    }

    fun logoutSteamSession() {
        steamSessionCookie = ""
        localStore?.clearSteamSession(storeUserKey())
        _uiState.update { current ->
            current.copy(
                hasSteamSession = false,
                steamSessionSteamId64 = "",
                steamSyncState = SteamSyncUiState.Idle
            )
        }
        _toastEvents.tryEmit("Loggade ut från Steam-synk")
    }

    fun updateSteamProfileInput(input: String) {
        _uiState.update { it.copy(steamProfileInput = input) }
    }

    fun syncSteamSubscriptions() {
        val sessionCookieSnapshot = steamSessionCookie.takeIf { it.isNotBlank() }
        val rawInput = _uiState.value.steamProfileInput.trim()
            .ifBlank { _uiState.value.steamSessionSteamId64.trim() }
        if (rawInput.isBlank() && sessionCookieSnapshot == null) {
            _uiState.update {
                it.copy(steamSyncState = SteamSyncUiState.Error("Ange ditt SteamID64, din Steam-profil-URL eller logga in med Steam."))
            }
            return
        }

        val storeKey = storeUserKey()
        syncSteamJob?.cancel()
        syncSteamJob = viewModelScope.launch {
            _uiState.update { it.copy(steamSyncState = SteamSyncUiState.Syncing) }
            try {
                // Step 1: Fetch and parse directly from steamcommunity.com FIRST, completely decoupled from Firestore.
                val syncResult = repository.fetchSubscribedWorkshopMods(
                    rawProfileInput = rawInput,
                    sessionCookie = sessionCookieSnapshot
                )
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
                    val updatedSeenMap = localStore?.initializeModUpdateBaselinesIfMissing(
                        userId = storeKey,
                        mods = mergedMods,
                        previousLocalById = existingById
                    )

                    _uiState.update { current ->
                        current.copy(
                            followedMods = mergedMods,
                            syncProfile = newProfile,
                            steamSyncState = SteamSyncUiState.Success(syncResult.steamId64, syncResult.mods.size),
                            showSteamSyncDialog = false,
                            seenModUpdateEpochSec = updatedSeenMap ?: current.seenModUpdateEpochSec
                        )
                    }
                    _toastEvents.tryEmit(
                        if (syncResult.isAuthenticatedSubscriptions) {
                            "Synkade ${syncResult.mods.size} prenumererade moddar från Steam"
                        } else {
                            "Synkade ${syncResult.mods.size} Steam-favoriter"
                        }
                    )
                } else {
                    hasPendingLocalSyncProfile = true
                    localStore?.saveSyncProfile(storeKey, newProfile)
                    val emptyMsg = if (syncResult.isAuthenticatedSubscriptions) {
                        "0 prenumererade Workshop-moddar hittades på ditt Steam-konto (${syncResult.steamId64})."
                    } else {
                        "0 publika Steam-favoriter (myfavorites) hittades. Steam begränsar \"mysubscriptions\" till inloggade webbläsarsessioner — logga in med Steam för att synka dina prenumerationer, favoritmarkera moddar på Steam eller bokmärk dem direkt i SteamPulse."
                    }
                    _uiState.update {
                        it.copy(
                            syncProfile = newProfile,
                            steamSyncState = SteamSyncUiState.EmptyDiagnostic(
                                steamId64 = syncResult.steamId64,
                                queriedUrl = syncResult.queriedUrl,
                                httpStatusCode = syncResult.httpStatusCode,
                                message = emptyMsg
                            ),
                            showSteamSyncDialog = true
                        )
                    }
                }

                // Step 3: Non-blocking background sync to Firestore (NEVER uploads steamSessionCookie); never fails or blocks the Steam dialog.
                val repo = followRepository
                if (repo != null && !userId.isNullOrBlank() && userId != "guest") {
                    viewModelScope.launch {
                        try {
                            repo.syncSubscribedMods(
                                steamId64 = syncResult.steamId64,
                                profileInput = rawInput.ifBlank { syncResult.steamId64 },
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
            } catch (e: SteamSessionExpiredException) {
                steamSessionCookie = ""
                localStore?.clearSteamSession(storeKey)
                _uiState.update {
                    it.copy(
                        hasSteamSession = false,
                        steamSessionSteamId64 = "",
                        steamSyncState = SteamSyncUiState.Error(
                            e.message ?: "Din Steam-session har gått ut. Logga in med Steam igen."
                        )
                    )
                }
            } catch (e: SteamProfilePrivateException) {
                _uiState.update {
                    it.copy(steamSyncState = SteamSyncUiState.Error(e.message ?: "Steam-profilen är privat."))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing Steam subscriptions/favorites for '$rawInput'", e)
                _uiState.update {
                    it.copy(
                        steamSyncState = SteamSyncUiState.Error(
                            e.localizedMessage ?: "Kunde inte synka Steam-moddar."
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
        val store = localStore
        val storeKey = storeUserKey()
        val prevGameVisit = store?.getGameLastVisitEpochSec(storeKey, game.appId)
            ?.takeIf { it > 0L }
            ?: _uiState.value.previousWorkshopVisitEpochSec
        val nowEpochSec = System.currentTimeMillis() / 1000L
        store?.saveGameLastVisitEpochSec(storeKey, game.appId, nowEpochSec)

        _uiState.update {
            it.copy(
                selectedGame = game,
                sortOrder = WorkshopSortOrder.MOST_SUBSCRIBED,
                modSearchQuery = "",
                modsState = WorkshopModsUiState.Loading,
                isLoadingMoreMods = false,
                selectedModDetail = null,
                activeGamePreviousVisitEpochSec = prevGameVisit
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

    fun focusGameFromPulse(appId: Int, title: String) {
        _uiState.update { it.copy(activeSectionTab = WorkshopSectionTab.DISCOVER) }
        val existing = ((_uiState.value.gamesState as? WorkshopGamesUiState.Success)?.games)
            ?.firstOrNull { it.appId == appId }
        val isSupported = appId > 0 && (
            SteamWorkshopRepository.KNOWN_WORKSHOP_APP_IDS.contains(appId) ||
                existing?.isKnownWorkshopPowerhouse == true
            )
        if (isSupported) {
            openCustomAppWorkshop(appId = appId, displayName = title)
        } else {
            closeGameWorkshop()
            updateGameSearchQuery(title)
            submitGameSearch()
        }
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
        markModUpdateSeen(mod)
        _uiState.update { it.copy(selectedModDetail = mod, isModDetailLoading = true) }
        loadDetailJob?.cancel()
        loadDetailJob = viewModelScope.launch {
            try {
                val enriched = repository.fetchModDetails(mod)
                markModUpdateSeen(enriched)
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
