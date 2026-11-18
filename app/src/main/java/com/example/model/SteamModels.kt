package com.example.model

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CurrencyMode(val code: String, val symbol: String) {
    SEK("SEK", "kr"),
    EUR("EUR", "€")
}

enum class GameEdition(val label: String) {
    STANDARD("Standard Edition"),
    DELUXE("Deluxe / Ultimate")
}

enum class SortOrder(val label: String) {
    DEFAULT("Standardordning"),
    PLAYERS_DESC("Flest spelare just nu"),
    PRICE_ASC("Lägsta pris"),
    DISCOUNT_DESC("Störst rabatt (%)")
}

enum class GameCategory(val label: String) {
    MOST_PLAYED("Mest spelade"),
    TOP_SELLERS("Toppsäljare"),
    NEW_AND_TRENDING("Nya & Trendande"),
    FOLLOWED("Mina följda")
}

enum class StoreUrlType {
    STEAM,
    G2A,
    CDKEYS,
    ENEBA,
    KINGUIN,
    GMG,
    FANATICAL
}

data class StoreOffer(
    val store: String,
    val isOfficial: Boolean,
    val region: String,
    val platform: String,
    val priceEur: Double,
    val urlType: StoreUrlType
) {
    val typeLabel: String
        get() = if (isOfficial) "Officiell återförsäljare" else "Keyshop / Marknadsplats"
}

data class SteamGame(
    val id: String,
    val rank: Int,
    val appId: Int,
    val title: String,
    val shortCode: String,
    val gradientStart: Color,
    val gradientEnd: Color,
    val developer: String,
    val genres: List<String>,
    val isFreeToPlay: Boolean,
    val editionNote: String? = null,
    val playersNow: Int,
    val peak24h: Int,
    val allTimePeak: Int,
    val steamPriceEur: Double,
    val deluxeMultiplier: Double = 1.3,
    val historicalLowEur: Double = steamPriceEur,
    val trend24h: List<Int> = emptyList(),
    val offers: List<StoreOffer> = emptyList(),
    val headerImageUrl: String = if (appId > 0) {
        "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"
    } else {
        ""
    },
    val finalPriceEur: Double = offers.minByOrNull { it.priceEur }?.priceEur ?: steamPriceEur,
    val steamDiscountPercent: Int = 0,
    val shortDescription: String = "",
    val releaseDate: String = "",
    val isDetailsLoaded: Boolean = false,
    val isPlayersLoaded: Boolean = playersNow > 0
)

@IgnoreExtraProperties
data class WatchlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val gameTitle: String = "",
    val targetPriceEur: Double = 0.0,
    val preferredCurrency: String = "SEK",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
