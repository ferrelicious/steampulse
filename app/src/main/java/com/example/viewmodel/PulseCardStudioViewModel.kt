package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SteamGamesCatalog
import com.example.data.SteamWorkshopRepository
import com.example.model.SteamGame
import com.example.model.WorkshopModItem
import com.example.model.WorkshopSortOrder
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

data class PulseCardStudioUiState(
    val games: List<SteamGame> = emptyList(),
    val selectedGame: SteamGame? = null,
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

    init {
        loadGames()
    }

    private fun loadGames() {
        val gameList = SteamGamesCatalog.initialGames
        val initialGame = gameList.firstOrNull()
        _uiState.update { current ->
            current.copy(
                games = gameList,
                selectedGame = initialGame
            )
        }
        initialGame?.let { fetchModsForGame(it.appId) }
    }

    fun selectGame(game: SteamGame) {
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMods = true) }
            runCatching {
                workshopRepository.fetchWorkshopMods(
                    appId = appId,
                    sortOrder = WorkshopSortOrder.MOST_SUBSCRIBED,
                    searchQuery = "",
                    page = 1
                )
            }.onSuccess { result ->
                _uiState.update { it.copy(topMods = result.mods, isLoadingMods = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMods = false) }
            }
        }
    }

    fun emitToast(msg: String) {
        _toastEvents.tryEmit(msg)
    }
}
