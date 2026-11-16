package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.SteamGamesCatalog
import com.example.data.WatchlistRepository
import com.example.data.WorkshopLocalStore
import com.example.model.CurrencyMode
import com.example.model.GameCategory
import com.example.model.GameEdition
import com.example.model.SortOrder
import com.example.model.SteamGame
import com.example.model.WatchlistItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

sealed interface WatchlistUiState {
    data object Loading : WatchlistUiState
    data class Success(val items: List<WatchlistItem>) : WatchlistUiState
    data class Error(val message: String) : WatchlistUiState
}

data class SteamPulseUiState(
    val globalOnline: Int = 37_842_190,
    val globalInGame: Int = 11_419_650,
    val currency: CurrencyMode = CurrencyMode.SEK,
    val searchQuery: String = "",
    val selectedCategory: GameCategory = GameCategory.ALL,
    val sortOrder: SortOrder = SortOrder.PLAYERS_DESC,
    val games: List<SteamGame> = SteamGamesCatalog.initialGames,
    val selectedGameId: String? = null,
    val selectedEdition: GameEdition = GameEdition.STANDARD
) {
    val selectedGame: SteamGame?
        get() = games.firstOrNull { it.id == selectedGameId }
}

class SteamPulseViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val currentUserId: String,
    private val localStore: WorkshopLocalStore? = null
) : ViewModel() {

    private val _baseGames = MutableStateFlow(SteamGamesCatalog.initialGames)
    private val _currency = MutableStateFlow(localStore?.getPreferredCurrency(currentUserId) ?: CurrencyMode.SEK)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(GameCategory.ALL)
    private val _sortOrder = MutableStateFlow(SortOrder.PLAYERS_DESC)
    private val _globalOnline = MutableStateFlow(37_842_190)
    private val _globalInGame = MutableStateFlow(11_419_650)
    private val _selectedGameId = MutableStateFlow<String?>(null)
    private val _selectedEdition = MutableStateFlow(GameEdition.STANDARD)
    private val _followedAppIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _localWatchlist = MutableStateFlow(localStore?.loadWatchlist(currentUserId).orEmpty())

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    val watchlistState: StateFlow<WatchlistUiState> = if (currentUserId == "guest") {
        _localWatchlist
            .map<List<WatchlistItem>, WatchlistUiState> { WatchlistUiState.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = WatchlistUiState.Success(_localWatchlist.value)
            )
    } else {
        combine(
            watchlistRepository
                .observeWatchlist(currentUserId)
                .map<List<WatchlistItem>, WatchlistUiState> { remoteItems ->
                    localStore?.saveWatchlist(currentUserId, remoteItems)
                    _localWatchlist.value = remoteItems
                    WatchlistUiState.Success(remoteItems)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing watchlist (using local cache)", rootError)
                    val cached = _localWatchlist.value
                    if (cached.isNotEmpty() || firestoreError?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(WatchlistUiState.Success(cached))
                    } else {
                        emit(WatchlistUiState.Error(rootError.message ?: "Kunde inte hämta bevakningslistan."))
                    }
                },
            _localWatchlist
        ) { remoteState, localItems ->
            when (remoteState) {
                is WatchlistUiState.Success -> {
                    if (localItems.isNotEmpty() && remoteState.items.isEmpty()) {
                        WatchlistUiState.Success(localItems)
                    } else {
                        remoteState
                    }
                }
                is WatchlistUiState.Loading,
                is WatchlistUiState.Error -> {
                    if (localItems.isNotEmpty()) WatchlistUiState.Success(localItems) else remoteState
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (_localWatchlist.value.isNotEmpty()) {
                WatchlistUiState.Success(_localWatchlist.value)
            } else {
                WatchlistUiState.Loading
            }
        )
    }

    private val filterParamsFlow = combine(
        _searchQuery,
        _selectedCategory,
        _sortOrder,
        _currency
    ) { query, category, sort, currency ->
        FilterParams(query, category, sort, currency)
    }

    private val selectionFlow = combine(
        _selectedGameId,
        _selectedEdition,
        _globalOnline,
        _globalInGame
    ) { gameId, edition, online, inGame ->
        SelectionTelemetry(gameId, edition, online, inGame)
    }

    private val followedAndWatchedFlow = combine(
        _followedAppIds,
        watchlistState
    ) { followedAppIds, wlState ->
        val watchedIds = (wlState as? WatchlistUiState.Success)?.items?.map { it.id }?.toSet().orEmpty()
        followedAppIds to watchedIds
    }

    val uiState: StateFlow<SteamPulseUiState> = combine(
        _baseGames,
        filterParamsFlow,
        selectionFlow,
        followedAndWatchedFlow
    ) { games, params, selection, (followedAppIds, watchedIds) ->
        val filtered = games.filter { game ->
            val matchesQuery = params.query.isBlank() ||
                game.title.contains(params.query, ignoreCase = true) ||
                game.genres.any { it.contains(params.query, ignoreCase = true) } ||
                game.developer.contains(params.query, ignoreCase = true)
            if (!matchesQuery) return@filter false

            when (params.category) {
                GameCategory.FOLLOWED -> watchedIds.contains(game.id) || followedAppIds.contains(game.appId)
                GameCategory.ALL -> true
                GameCategory.DEALS -> SteamGamesCatalog.getDiscountPercent(game) >= 20
                else -> game.genres.contains(params.category.label)
            }
        }

        val sorted = when (params.sort) {
            SortOrder.PLAYERS_DESC -> filtered.sortedByDescending { it.playersNow }
            SortOrder.PEAK24_DESC -> filtered.sortedByDescending { it.peak24h }
            SortOrder.PRICE_ASC -> filtered.sortedBy { SteamGamesCatalog.getBestOffer(it).second }
            SortOrder.DISCOUNT_DESC -> filtered.sortedByDescending {
                SteamGamesCatalog.getDiscountPercent(it)
            }
        }

        SteamPulseUiState(
            globalOnline = selection.globalOnline,
            globalInGame = selection.globalInGame,
            currency = params.currency,
            searchQuery = params.query,
            selectedCategory = params.category,
            sortOrder = params.sort,
            games = sorted,
            selectedGameId = selection.gameId,
            selectedEdition = selection.edition
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SteamPulseUiState()
    )

    init {
        startLiveTelemetryTicker()
    }

    private fun startLiveTelemetryTicker() {
        viewModelScope.launch {
            while (true) {
                delay(6_000L)
                var totalDelta = 0
                _baseGames.update { currentList ->
                    currentList.map { game ->
                        val step = ((Random.nextDouble() - 0.48) * (game.playersNow * 0.0025)).toInt()
                        totalDelta += step
                        game.copy(playersNow = max(1000, game.playersNow + step))
                    }
                }
                _globalOnline.update { max(1_000_000, it + totalDelta * 2) }
                _globalInGame.update { max(500_000, it + totalDelta) }
            }
        }
    }

    fun updateFollowedAppIds(appIds: Set<Int>) {
        _followedAppIds.value = appIds
    }

    fun setCurrency(currency: CurrencyMode) {
        _currency.value = currency
        localStore?.setPreferredCurrency(currentUserId, currency)
    }

    fun reloadLocalDataFromStore() {
        val store = localStore ?: return
        _currency.value = store.getPreferredCurrency(currentUserId)
        val restoredWatchlist = store.loadWatchlist(currentUserId)
        _localWatchlist.value = restoredWatchlist
        if (currentUserId != "guest" && restoredWatchlist.isNotEmpty()) {
            viewModelScope.launch {
                restoredWatchlist.forEach { item ->
                    runCatching {
                        watchlistRepository.upsertWatchlistItem(
                            gameId = item.id,
                            gameTitle = item.gameTitle,
                            targetPriceEur = item.targetPriceEur,
                            preferredCurrency = item.preferredCurrency
                        )
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: GameCategory) {
        _selectedCategory.value = category
    }

    fun selectSortOrder(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
    }

    fun resetFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = GameCategory.ALL
        _sortOrder.value = SortOrder.PLAYERS_DESC
    }

    fun openGameDetail(gameId: String) {
        _selectedEdition.value = GameEdition.STANDARD
        _selectedGameId.value = gameId
    }

    fun closeGameDetail() {
        _selectedGameId.value = null
    }

    fun selectEdition(edition: GameEdition) {
        _selectedEdition.value = edition
    }

    fun toggleWatchlist(game: SteamGame, isCurrentlyWatched: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyWatched) {
                val updated = localStore?.removeWatchlistItemLocal(currentUserId, game.id)
                    ?: _localWatchlist.value.filterNot { it.id == game.id }
                _localWatchlist.value = updated
                if (currentUserId != "guest") {
                    runCatching { watchlistRepository.deleteWatchlistItem(game.id) }
                }
                _toastEvents.emit("${game.title} togs bort från bevakningslistan.")
            } else {
                val (_, bestPriceEur) = SteamGamesCatalog.getBestOffer(game)
                val currency = _currency.value.code
                val nowTs = Timestamp.now()
                val newItem = WatchlistItem(
                    id = game.id,
                    userId = currentUserId,
                    gameTitle = game.title,
                    targetPriceEur = bestPriceEur,
                    preferredCurrency = currency,
                    createdAt = nowTs,
                    updatedAt = nowTs
                )
                val updated = localStore?.upsertWatchlistItemLocal(currentUserId, newItem)
                    ?: (listOf(newItem) + _localWatchlist.value.filterNot { it.id == game.id })
                _localWatchlist.value = updated
                if (currentUserId != "guest") {
                    runCatching {
                        watchlistRepository.upsertWatchlistItem(
                            gameId = game.id,
                            gameTitle = game.title,
                            targetPriceEur = bestPriceEur,
                            preferredCurrency = currency
                        )
                    }
                }
                _toastEvents.emit("${game.title} lades till i din bevakningslista!")
            }
        }
    }

    fun savePriceAlert(game: SteamGame, targetPriceInput: Double) {
        viewModelScope.launch {
            val currency = _currency.value
            val targetEur = SteamGamesCatalog.toEurFromCurrency(targetPriceInput, currency)
                .coerceIn(0.0, 1000.0)
            val nowTs = Timestamp.now()
            val newItem = WatchlistItem(
                id = game.id,
                userId = currentUserId,
                gameTitle = game.title,
                targetPriceEur = targetEur,
                preferredCurrency = currency.code,
                createdAt = nowTs,
                updatedAt = nowTs
            )
            val updated = localStore?.upsertWatchlistItemLocal(currentUserId, newItem)
                ?: (listOf(newItem) + _localWatchlist.value.filterNot { it.id == game.id })
            _localWatchlist.value = updated
            if (currentUserId != "guest") {
                runCatching {
                    watchlistRepository.upsertWatchlistItem(
                        gameId = game.id,
                        gameTitle = game.title,
                        targetPriceEur = targetEur,
                        preferredCurrency = currency.code
                    )
                }
            }
            val formatted = SteamGamesCatalog.formatPrice(targetEur, currency)
            _toastEvents.emit("Prisvarning sparad för ${game.title} ($formatted)!")
        }
    }

    fun removeWatchlistItem(gameId: String) {
        viewModelScope.launch {
            val updated = localStore?.removeWatchlistItemLocal(currentUserId, gameId)
                ?: _localWatchlist.value.filterNot { it.id == gameId }
            _localWatchlist.value = updated
            if (currentUserId != "guest") {
                runCatching { watchlistRepository.deleteWatchlistItem(gameId) }
            }
            _toastEvents.emit("Spelet togs bort från bevakningslistan.")
        }
    }

    private data class FilterParams(
        val query: String,
        val category: GameCategory,
        val sort: SortOrder,
        val currency: CurrencyMode
    )

    private data class SelectionTelemetry(
        val gameId: String?,
        val edition: GameEdition,
        val globalOnline: Int,
        val globalInGame: Int
    )

    companion object {
        private const val TAG = "SteamPulseVM"

        fun provideFactory(
            repository: WatchlistRepository,
            currentUserId: String,
            localStore: WorkshopLocalStore? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SteamPulseViewModel(repository, currentUserId, localStore)
            }
        }
    }
}
