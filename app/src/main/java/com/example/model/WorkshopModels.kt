package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WorkshopLeaderboardMode(
    val label: String,
    val apiRequest: String
) {
    TWO_WEEKS("Topp 100 (2v)", "top100in2weeks"),
    ALL_TIME("Topp 100 (All-time)", "top100forever"),
    MOST_OWNED("Mest ägda", "top100owned")
}

enum class WorkshopSectionTab(val label: String) {
    DISCOVER("Utforska"),
    FOLLOWED("Mina & Följda")
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

object WorkshopFollowSource {
    const val BOOKMARK = "BOOKMARK"
    const val STEAM_SYNC = "STEAM_SYNC"
    const val BOTH = "BOTH"
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
    val isKnownWorkshopPowerhouse: Boolean,
    val customImageUrl: String = ""
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

@IgnoreExtraProperties
data class FollowedWorkshopGame(
    @DocumentId val id: String = "",
    val userId: String = "",
    val appId: Int = 0,
    val gameName: String = "",
    val developer: String = "",
    val headerImageUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toGameSummary(): WorkshopGameSummary {
        return WorkshopGameSummary(
            rank = 0,
            appId = appId,
            name = gameName.ifBlank { "Steam App #$appId" },
            developer = developer.ifBlank { "Steam Workshop" },
            publisher = "",
            ccu = 0,
            ownersRange = "—",
            positiveReviews = 0,
            negativeReviews = 0,
            averagePlaytimeMinutes = 0,
            priceCents = 0,
            discountPercent = 0,
            isKnownWorkshopPowerhouse = true,
            customImageUrl = headerImageUrl
        )
    }
}

@IgnoreExtraProperties
data class FollowedWorkshopMod(
    @DocumentId val id: String = "",
    val userId: String = "",
    val publishedFileId: String = "",
    val appId: Int = 0,
    val gameName: String = "",
    val title: String = "",
    val author: String = "",
    val previewUrl: String = "",
    val itemUrl: String = "",
    val source: String = WorkshopFollowSource.BOOKMARK,
    val updatedEpochSec: Long = 0L,
    val subscriptions: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toModItem(): WorkshopModItem {
        return WorkshopModItem(
            publishedFileId = publishedFileId,
            appId = appId,
            title = title.ifBlank { "Workshop Item #$publishedFileId" },
            author = author.ifBlank { "Steam-skapare" },
            previewUrl = previewUrl,
            itemUrl = itemUrl.ifBlank { "https://steamcommunity.com/sharedfiles/filedetails/?id=$publishedFileId" },
            subscriptions = subscriptions.takeIf { it > 0L },
            updatedEpochSec = updatedEpochSec.takeIf { it > 0L }
        )
    }
}

@IgnoreExtraProperties
data class SteamWorkshopSyncProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val steamId64: String = "",
    val profileInput: String = "",
    val syncedCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class FollowedFeedModItem(
    val mod: WorkshopModItem,
    val gameName: String
)

sealed interface WorkshopFollowedFeedUiState {
    data object Idle : WorkshopFollowedFeedUiState
    data object Loading : WorkshopFollowedFeedUiState
    data class Success(val mods: List<FollowedFeedModItem>) : WorkshopFollowedFeedUiState
    data class Error(val message: String) : WorkshopFollowedFeedUiState
}

data class SteamSubscribedSyncResult(
    val steamId64: String,
    val mods: List<FollowedWorkshopMod>,
    val queriedUrl: String,
    val httpStatusCode: Int,
    val isAuthenticatedSubscriptions: Boolean = false
)

sealed interface SteamSyncUiState {
    data object Idle : SteamSyncUiState
    data object Syncing : SteamSyncUiState
    data class Success(val steamId64: String, val syncedCount: Int) : SteamSyncUiState
    data class EmptyDiagnostic(
        val steamId64: String,
        val queriedUrl: String,
        val httpStatusCode: Int,
        val message: String
    ) : SteamSyncUiState
    data class Error(val message: String) : SteamSyncUiState
}

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
