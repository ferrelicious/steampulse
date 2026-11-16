package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SteamGamesCatalog
import com.example.data.SteamWorkshopRepository
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamGame
import com.example.model.WorkshopModItem
import com.example.model.WorkshopSortOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CardAspectRatio(val label: String, val width: Int, val height: Int) {
    PORTRAIT_9_16("Story (9:16)", 1080, 1920),
    SQUARE_1_1("Kvadrat (1:1)", 1080, 1080)
}

data class PulseCardGameOption(
    val appId: Int,
    val title: String,
    val developer: String,
    val headerImageUrl: String,
    val steamGame: SteamGame? = null,
    val isFollowedWorkshop: Boolean = false
)

data class PulseCardStudioUiState(
    val games: List<PulseCardGameOption> = emptyList(),
    val selectedGame: PulseCardGameOption? = null,
    val aspectRatio: CardAspectRatio = CardAspectRatio.PORTRAIT_9_16,
    val showPlayerCount: Boolean = true,
    val showBestDeal: Boolean = true,
    val showModList: Boolean = false,
    val topMods: List<WorkshopModItem> = emptyList(),
    val isLoadingMods: Boolean = false
)

class PulseCardStudioViewModel(
    private val workshopRepository: SteamWorkshopRepository = SteamWorkshopRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(PulseCardStudioUiState())
    val uiState: StateFlow<PulseCardStudioUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var cachedFollowedMods: List<FollowedWorkshopMod> = emptyList()
    private var fetchModsJob: Job? = null

    init {
        syncGames(
            pulsGames = SteamGamesCatalog.initialGames,
            followedGames = emptyList(),
            followedMods = emptyList()
        )
    }

    fun syncGames(
        pulsGames: List<SteamGame>,
        followedGames: List<FollowedWorkshopGame>,
        followedMods: List<FollowedWorkshopMod>
    ) {
        cachedFollowedMods = followedMods

        val catalogByAppId = pulsGames.associateBy { it.appId }
        val followedAppIds = followedGames.map { it.appId }.toSet()
        val combined = LinkedHashMap<Int, PulseCardGameOption>()

        followedGames.forEach { followed ->
            val matchedCatalog = catalogByAppId[followed.appId]
            val header = followed.headerImageUrl.ifBlank {
                "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${followed.appId}/header.jpg"
            }
            combined[followed.appId] = PulseCardGameOption(
                appId = followed.appId,
                title = matchedCatalog?.title ?: followed.gameName.ifBlank { "Steam App #${followed.appId}" },
                developer = matchedCatalog?.developer ?: followed.developer.ifBlank { "Steam Workshop" },
                headerImageUrl = header,
                steamGame = matchedCatalog,
                isFollowedWorkshop = true
            )
        }

        pulsGames.forEach { game ->
            if (!combined.containsKey(game.appId)) {
                combined[game.appId] = PulseCardGameOption(
                    appId = game.appId,
                    title = game.title,
                    developer = game.developer,
                    headerImageUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${game.appId}/header.jpg",
                    steamGame = game,
                    isFollowedWorkshop = game.appId in followedAppIds
                )
            }
        }

        val options = combined.values.toList()
        val previousSelectedAppId = _uiState.value.selectedGame?.appId
        val nextSelected = options.firstOrNull { it.appId == previousSelectedAppId } ?: options.firstOrNull()
        val selectionChanged = nextSelected != null && nextSelected.appId != previousSelectedAppId

        _uiState.update { current ->
            current.copy(
                games = options,
                selectedGame = nextSelected
            )
        }

        if (selectionChanged && nextSelected != null) {
            fetchModsForGame(nextSelected.appId)
        }
    }

    fun selectGame(game: PulseCardGameOption) {
        _uiState.update { it.copy(selectedGame = game, topMods = emptyList()) }
        fetchModsForGame(game.appId)
    }

    fun setAspectRatio(ratio: CardAspectRatio) {
        _uiState.update { it.copy(aspectRatio = ratio) }
    }

    fun togglePlayerCount(show: Boolean) {
        _uiState.update { it.copy(showPlayerCount = show) }
    }

    fun toggleBestDeal(show: Boolean) {
        _uiState.update { it.copy(showBestDeal = show) }
    }

    fun toggleModList(show: Boolean) {
        _uiState.update { it.copy(showModList = show) }
    }

    private fun fetchModsForGame(appId: Int) {
        fetchModsJob?.cancel()
        val localMods = cachedFollowedMods
            .filter { it.appId == appId }
            .sortedByDescending { it.subscriptions }
            .take(4)
            .map { it.toModItem() }

        fetchModsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMods = true, topMods = localMods) }
            runCatching {
                workshopRepository.fetchWorkshopMods(
                    appId = appId,
                    sortOrder = WorkshopSortOrder.MOST_SUBSCRIBED,
                    searchQuery = "",
                    page = 1
                )
            }.onSuccess { result ->
                val resolved = result.mods.take(4).ifEmpty { localMods }
                _uiState.update { it.copy(topMods = resolved, isLoadingMods = false) }
            }.onFailure {
                _uiState.update { it.copy(topMods = localMods, isLoadingMods = false) }
            }
        }
    }

    fun emitToast(msg: String) {
        _toastEvents.tryEmit(msg)
    }
}
