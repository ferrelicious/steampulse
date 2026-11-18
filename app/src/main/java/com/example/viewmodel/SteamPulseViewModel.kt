package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.SteamGamesCatalog
import com.example.data.SteamPulseRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WatchlistUiState {
    data object Loading : WatchlistUiState
    data class Success(val items: List<WatchlistItem>) : WatchlistUiState
    data class Error(val message: String) : WatchlistUiState
}

data class SteamPulseUiState(
    val currency: CurrencyMode = CurrencyMode.SEK,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedCategory: GameCategory = GameCategory.MOST_PLAYED,
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val isLoadingCategory: Boolean = false,
    val categoryError: String? = null,
    val games: List<SteamGame> = emptyList(),
    val knownGames: List<SteamGame> = emptyList(),
    val selectedGameId: String? = null,
    val selectedEdition: GameEdition = GameEdition.STANDARD
) {
    val isSearchActive: Boolean
        get() = searchQuery.trim().length >= 2

    val selectedGame: SteamGame?
        get() = games.firstOrNull { it.id == selectedGameId }
            ?: knownGames.firstOrNull { it.id == selectedGameId }
}

class SteamPulseViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val currentUserId: String,
    private val localStore: WorkshopLocalStore? = null,
    private val pulseRepository: SteamPulseRepository = SteamPulseRepository()
) : ViewModel() {

    private val _currency = MutableStateFlow(localStore?.getPreferredCurrency(currentUserId) ?: CurrencyMode.SEK)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _searchError = MutableStateFlow<String?>(null)
    private val _searchResults = MutableStateFlow<List<SteamGame>>(emptyList())

    private val _selectedCategory = MutableStateFlow(GameCategory.MOST_PLAYED)
    private val _sortOrder = MutableStateFlow(SortOrder.DEFAULT)
    private val _isLoadingCategory = MutableStateFlow(false)
    private val _categoryError = MutableStateFlow<String?>(null)
    private val _categoryGames = MutableStateFlow<List<SteamGame>>(emptyList())
    private val _knownGamesMap = MutableStateFlow<Map<Int, SteamGame>>(emptyMap())

    private val _selectedGameId = MutableStateFlow<String?>(null)
    private val _selectedEdition = MutableStateFlow(GameEdition.STANDARD)
    private val _followedAppIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _localWatchlist = MutableStateFlow(localStore?.loadWatchlist(currentUserId).orEmpty())

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var searchJob: Job? = null
    private var categoryJob: Job? = null
    private var enrichJob: Job? = null

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

    private val searchStateFlow = combine(
        _searchQuery,
        _isSearching,
        _searchError,
        _searchResults
    ) { query, searching, error, results ->
        SearchStateSnapshot(query, searching, error, results)
    }

    private val categoryStateFlow = combine(
        _selectedCategory,
        _sortOrder,
        _isLoadingCategory,
        _categoryError,
        _categoryGames
    ) { category, sort, loading, error, games ->
        CategoryStateSnapshot(category, sort, loading, error, games)
    }

    private val selectionFlow = combine(
        _selectedGameId,
        _selectedEdition,
        _currency,
        _knownGamesMap
    ) { gameId, edition, currency, knownMap ->
        SelectionSnapshot(gameId, edition, currency, knownMap)
    }

    val uiState: StateFlow<SteamPulseUiState> = combine(
        searchStateFlow,
        categoryStateFlow,
        selectionFlow
    ) { search, category, selection ->
        val isSearchActive = search.query.trim().length >= 2
        val baseList = if (isSearchActive) search.results else category.games

        val sortedList = when (category.sort) {
            SortOrder.DEFAULT -> baseList
            SortOrder.PLAYERS_DESC -> baseList.sortedByDescending { it.playersNow }
            SortOrder.PRICE_ASC -> baseList.sortedBy { SteamGamesCatalog.getBestOffer(it).second }
            SortOrder.DISCOUNT_DESC -> baseList.sortedByDescending {
                SteamGamesCatalog.getDiscountPercent(it)
            }
        }

        SteamPulseUiState(
            currency = selection.currency,
            searchQuery = search.query,
            isSearching = search.isSearching,
            searchError = search.error,
            selectedCategory = category.category,
            sortOrder = category.sort,
            isLoadingCategory = category.isLoading,
            categoryError = category.error,
            games = sortedList,
            knownGames = selection.knownMap.values.toList(),
            selectedGameId = selection.gameId,
            selectedEdition = selection.edition
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SteamPulseUiState()
    )

    init {
        loadCategory(GameCategory.MOST_PLAYED)
        hydrateWatchedGamesMetadata()
    }

    private fun hydrateWatchedGamesMetadata() {
        val watchedAppIds = _localWatchlist.value.mapNotNull { extractAppIdFromWatchlistId(it.id) }
        if (watchedAppIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                pulseRepository.buildGamesForAppIds(watchedAppIds)
            }.onSuccess { resolved ->
                registerKnownGames(resolved)
            }
        }
    }

    private fun registerKnownGames(games: List<SteamGame>) {
        if (games.isEmpty()) return
        _knownGamesMap.update { current ->
            val updated = current.toMutableMap()
            games.forEach { game ->
                val existing = updated[game.appId]
                updated[game.appId] = if (existing == null || game.isDetailsLoaded || !existing.isDetailsLoaded) {
                    game
                } else {
                    existing.copy(
                        playersNow = if (game.playersNow > 0) game.playersNow else existing.playersNow,
                        isPlayersLoaded = existing.isPlayersLoaded || game.isPlayersLoaded
                    )
                }
            }
            updated
        }
    }

    fun updateFollowedAppIds(appIds: Set<Int>) {
        val changed = _followedAppIds.value != appIds
        _followedAppIds.value = appIds
        if (changed && _selectedCategory.value == GameCategory.FOLLOWED && _searchQuery.value.trim().length < 2) {
            loadCategory(GameCategory.FOLLOWED)
        }
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
        hydrateWatchedGamesMetadata()
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
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _isSearching.value = false
            _searchError.value = null
            _searchResults.value = emptyList()
            return
        }

        _isSearching.value = true
        _searchError.value = null

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runCatching {
                pulseRepository.searchStoreGames(trimmed)
            }.onSuccess { results ->
                _searchResults.value = results
                _isSearching.value = false
                registerKnownGames(results)

                if (results.isNotEmpty()) {
                    enrichJob?.cancel()
                    enrichJob = launch {
                        val enriched = pulseRepository.enrichGames(results, limit = 12, concurrency = 5)
                        if (_searchQuery.value.trim() == trimmed) {
                            _searchResults.value = enriched
                            registerKnownGames(enriched)
                        }
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _isSearching.value = false
                _searchError.value = error.message ?: "Kunde inte söka i Steam Store."
            }
        }
    }

    fun selectCategory(category: GameCategory) {
        _selectedCategory.value = category
        if (_searchQuery.value.isNotEmpty()) {
            updateSearchQuery("")
        }
        loadCategory(category)
    }

    fun refreshActiveList() {
        val trimmed = _searchQuery.value.trim()
        if (trimmed.length >= 2) {
            updateSearchQuery(_searchQuery.value)
        } else {
            loadCategory(_selectedCategory.value, forceRefresh = true)
        }
    }

    fun selectSortOrder(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
    }

    fun resetFilters() {
        updateSearchQuery("")
        _sortOrder.value = SortOrder.DEFAULT
        if (_selectedCategory.value != GameCategory.MOST_PLAYED) {
            _selectedCategory.value = GameCategory.MOST_PLAYED
            loadCategory(GameCategory.MOST_PLAYED)
        }
    }

    private fun loadCategory(category: GameCategory, forceRefresh: Boolean = false) {
        categoryJob?.cancel()
        enrichJob?.cancel()

        categoryJob = viewModelScope.launch {
            _isLoadingCategory.value = true
            _categoryError.value = null

            if (category == GameCategory.FOLLOWED) {
                val watchedIds = _localWatchlist.value.mapNotNull { extractAppIdFromWatchlistId(it.id) }
                val allIds = (_followedAppIds.value + watchedIds).filter { it > 0 }.distinct()
                if (allIds.isEmpty()) {
                    _categoryGames.value = emptyList()
                    _isLoadingCategory.value = false
                    return@launch
                }
                runCatching {
                    pulseRepository.buildGamesForAppIds(allIds)
                }.onSuccess { followedList ->
                    _categoryGames.value = followedList
                    _isLoadingCategory.value = false
                    registerKnownGames(followedList)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _isLoadingCategory.value = false
                    _categoryError.value = error.message ?: "Kunde inte ladda följda spel."
                }
                return@launch
            }

            runCatching {
                val baseGames = pulseRepository.fetchCategoryGames(category, forceRefresh)
                if (category == GameCategory.MOST_PLAYED && baseGames.isNotEmpty()) {
                    // Enrich top 10 before hiding skeleton so real titles, artwork & prices appear immediately
                    pulseRepository.enrichGames(baseGames, limit = 10, concurrency = 5)
                } else {
                    baseGames
                }
            }.onSuccess { initialGames ->
                _categoryGames.value = initialGames
                _isLoadingCategory.value = false
                registerKnownGames(initialGames)

                if (initialGames.isNotEmpty()) {
                    enrichJob = launch {
                        val fullyEnriched = pulseRepository.enrichGames(initialGames, limit = 25, concurrency = 5)
                        if (_selectedCategory.value == category && _searchQuery.value.trim().length < 2) {
                            _categoryGames.value = fullyEnriched
                            registerKnownGames(fullyEnriched)
                        }
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _isLoadingCategory.value = false
                _categoryError.value = error.message ?: "Kunde inte hämta data från Steam."
            }
        }
    }

    fun openGameDetail(gameId: String) {
        _selectedEdition.value = GameEdition.STANDARD
        _selectedGameId.value = gameId

        val currentGame = uiState.value.games.firstOrNull { it.id == gameId }
            ?: _knownGamesMap.value.values.firstOrNull { it.id == gameId }
        val appId = currentGame?.appId ?: extractAppIdFromWatchlistId(gameId)
        if (appId != null && appId > 0 && (currentGame == null || !currentGame.isDetailsLoaded || !currentGame.isPlayersLoaded)) {
            viewModelScope.launch {
                val base = currentGame ?: SteamPulseRepository.createSkeletonGame(appId = appId)
                val enriched = runCatching { pulseRepository.enrichGame(base) }.getOrNull() ?: return@launch
                registerKnownGames(listOf(enriched))
                _categoryGames.update { list -> list.map { if (it.appId == enriched.appId) enriched else it } }
                _searchResults.update { list -> list.map { if (it.appId == enriched.appId) enriched else it } }
            }
        }
    }

    fun closeGameDetail() {
        _selectedGameId.value = null
    }

    fun selectEdition(edition: GameEdition) {
        _selectedEdition.value = edition
    }

    fun toggleWatchlist(game: SteamGame, isCurrentlyWatched: Boolean) {
        viewModelScope.launch {
            registerKnownGames(listOf(game))
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
            registerKnownGames(listOf(game))
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

    private data class SearchStateSnapshot(
        val query: String,
        val isSearching: Boolean,
        val error: String?,
        val results: List<SteamGame>
    )

    private data class CategoryStateSnapshot(
        val category: GameCategory,
        val sort: SortOrder,
        val isLoading: Boolean,
        val error: String?,
        val games: List<SteamGame>
    )

    private data class SelectionSnapshot(
        val gameId: String?,
        val edition: GameEdition,
        val currency: CurrencyMode,
        val knownMap: Map<Int, SteamGame>
    )

    companion object {
        private const val TAG = "SteamPulseVM"
        const val SEARCH_DEBOUNCE_MS = 400L

        fun extractAppIdFromWatchlistId(id: String): Int? {
            if (id.startsWith("app_")) return id.removePrefix("app_").toIntOrNull()
            return id.toIntOrNull()
        }

        fun provideFactory(
            repository: WatchlistRepository,
            currentUserId: String,
            localStore: WorkshopLocalStore? = null,
            pulseRepository: SteamPulseRepository = SteamPulseRepository()
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SteamPulseViewModel(repository, currentUserId, localStore, pulseRepository)
            }
        }
    }
}
