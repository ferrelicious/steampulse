package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SteamWorkshopRepository
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopGamesUiState
import com.example.model.WorkshopLeaderboardMode
import com.example.model.WorkshopModItem
import com.example.model.WorkshopModsUiState
import com.example.model.WorkshopSortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkshopScreenUiState(
    val leaderboardMode: WorkshopLeaderboardMode = WorkshopLeaderboardMode.TWO_WEEKS,
    val gameSearchQuery: String = "",
    val onlyWorkshopPowerhouses: Boolean = false,
    val gamesState: WorkshopGamesUiState = WorkshopGamesUiState.Loading,
    val selectedGame: WorkshopGameSummary? = null,
    val sortOrder: WorkshopSortOrder = WorkshopSortOrder.MOST_SUBSCRIBED,
    val modSearchQuery: String = "",
    val modsState: WorkshopModsUiState = WorkshopModsUiState.Loading,
    val isLoadingMoreMods: Boolean = false,
    val selectedModDetail: WorkshopModItem? = null,
    val isModDetailLoading: Boolean = false
) {
    val filteredGames: List<WorkshopGameSummary>
        get() {
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
    private val repository: SteamWorkshopRepository = SteamWorkshopRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkshopScreenUiState())
    val uiState: StateFlow<WorkshopScreenUiState> = _uiState.asStateFlow()

    private val cacheByMode = mutableMapOf<WorkshopLeaderboardMode, List<WorkshopGameSummary>>()
    private var loadGamesJob: Job? = null
    private var loadModsJob: Job? = null
    private var loadDetailJob: Job? = null

    init {
        loadTop100Games(WorkshopLeaderboardMode.TWO_WEEKS)
    }

    fun selectLeaderboardMode(mode: WorkshopLeaderboardMode) {
        if (_uiState.value.leaderboardMode == mode && _uiState.value.gamesState is WorkshopGamesUiState.Success) {
            return
        }
        _uiState.update { it.copy(leaderboardMode = mode) }
        loadTop100Games(mode)
    }

    fun updateGameSearchQuery(query: String) {
        _uiState.update { it.copy(gameSearchQuery = query) }
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
