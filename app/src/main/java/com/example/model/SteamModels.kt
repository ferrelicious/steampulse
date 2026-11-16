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
    PLAYERS_DESC("Flest spelare just nu"),
    PEAK24_DESC("24h Peak (Högst först)"),
    PRICE_ASC("Lägsta pris"),
    DISCOUNT_DESC("Störst rabatt (%)")
}

enum class GameCategory(val label: String) {
    FOLLOWED("Mina följda"),
    ALL("Alla spel"),
    FPS("FPS/Taktiskt"),
    RPG("RPG/Action"),
    SURVIVAL("Överlevnad/Sandbox"),
    FREE("Gratis att spela"),
    DEALS("Rea/Bäst pris")
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
    val deluxeMultiplier: Double,
    val historicalLowEur: Double,
    val trend24h: List<Int>,
    val offers: List<StoreOffer>
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
