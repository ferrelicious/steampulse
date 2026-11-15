package com.example.model

enum class WorkshopLeaderboardMode(
    val label: String,
    val apiRequest: String
) {
    TWO_WEEKS("Topp 100 (2v)", "top100in2weeks"),
    ALL_TIME("Topp 100 (All-time)", "top100forever"),
    MOST_OWNED("Mest ägda", "top100owned")
}

enum class WorkshopSortOrder(
    val label: String,
    val browseSort: String,
    val daysParam: Int
) {
    MOST_SUBSCRIBED("Mest Subscribed", "totaluniquesubscribers", -1),
    MOST_RECENT("Nyaste moddar", "mostrecent", -1),
    TRENDING("Populära nu", "trend", 7),
    TOP_RATED("Högst betyg", "toprated", -1)
}

data class WorkshopGameSummary(
    val rank: Int,
    val appId: Int,
    val name: String,
    val developer: String,
    val publisher: String,
    val ccu: Int,
    val ownersRange: String,
    val positiveReviews: Int,
    val negativeReviews: Int,
    val averagePlaytimeMinutes: Int,
    val priceCents: Int,
    val discountPercent: Int,
    val isKnownWorkshopPowerhouse: Boolean
) {
    val headerImageUrl: String
        get() = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"

    val fallbackHeaderImageUrl: String
        get() = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/header.jpg"

    val capsuleImageUrl: String
        get() = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/capsule_231x87.jpg"

    val workshopHomeUrl: String
        get() = "https://steamcommunity.com/app/$appId/workshop/"

    val positiveRatioPercent: Int?
        get() {
            val total = positiveReviews + negativeReviews
            return if (total > 0) ((positiveReviews.toDouble() / total) * 100.0).toInt() else null
        }
}

data class WorkshopModItem(
    val publishedFileId: String,
    val appId: Int,
    val title: String,
    val author: String,
    val previewUrl: String,
    val itemUrl: String,
    val ratingStarsUrl: String = "",
    val subscriptions: Long? = null,
    val favorites: Long? = null,
    val views: Long? = null,
    val fileSizeBytes: Long? = null,
    val updatedEpochSec: Long? = null,
    val description: String = "",
    val tags: List<String> = emptyList()
)

data class WorkshopModsFetchResult(
    val mods: List<WorkshopModItem>,
    val hasMore: Boolean,
    val diagnosticInfo: String = ""
)

sealed interface WorkshopGamesUiState {
    data object Loading : WorkshopGamesUiState
    data class Success(val games: List<WorkshopGameSummary>) : WorkshopGamesUiState
    data class Error(val message: String) : WorkshopGamesUiState
}

sealed interface WorkshopModsUiState {
    data object Loading : WorkshopModsUiState
    data class Success(
        val mods: List<WorkshopModItem>,
        val hasMore: Boolean,
        val currentPage: Int,
        val diagnosticInfo: String = ""
    ) : WorkshopModsUiState
    data class Error(val message: String) : WorkshopModsUiState
}
