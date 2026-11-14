package com.example.data

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.model.CurrencyMode
import com.example.model.GameEdition
import com.example.model.SteamGame
import com.example.model.StoreOffer
import com.example.model.StoreUrlType
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

object SteamGamesCatalog {
    const val EXCHANGE_RATE_SEK = 11.40

    val initialGames: List<SteamGame> = listOf(
        SteamGame(
            id = "cs2",
            rank = 1,
            appId = 730,
            title = "Counter-Strike 2",
            shortCode = "CS2",
            gradientStart = Color(0xFFF59E0B),
            gradientEnd = Color(0xFFEA580C),
            developer = "Valve",
            genres = listOf("FPS/Taktiskt", "Gratis att spela"),
            isFreeToPlay = true,
            editionNote = "Prime Status Upgrade",
            playersNow = 1482910,
            peak24h = 1612400,
            allTimePeak = 1862531,
            steamPriceEur = 14.99,
            deluxeMultiplier = 1.8,
            historicalLowEur = 11.99,
            trend24h = listOf(1120, 980, 890, 840, 870, 950, 1100, 1290, 1450, 1580, 1612, 1482),
            offers = listOf(
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 14.99, StoreUrlType.STEAM),
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 12.49, StoreUrlType.CDKEYS),
                StoreOffer("Eneba", false, "Global / EU", "Steam Gift", 12.89, StoreUrlType.ENEBA),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 13.19, StoreUrlType.G2A),
                StoreOffer("Kinguin", false, "Global / EU", "Steam Key", 13.45, StoreUrlType.KINGUIN)
            )
        ),
        SteamGame(
            id = "dota2",
            rank = 2,
            appId = 570,
            title = "Dota 2",
            shortCode = "DOTA",
            gradientStart = Color(0xFFDC2626),
            gradientEnd = Color(0xFF9F1239),
            developer = "Valve",
            genres = listOf("Gratis att spela", "RPG/Action"),
            isFreeToPlay = true,
            editionNote = "Gratis + Crownfall Bundle",
            playersNow = 764320,
            peak24h = 845100,
            allTimePeak = 1295114,
            steamPriceEur = 0.0,
            deluxeMultiplier = 1.0,
            historicalLowEur = 0.0,
            trend24h = listOf(610, 540, 490, 470, 510, 580, 670, 750, 820, 845, 810, 764),
            offers = listOf(
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Gratis att spela", 0.0, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "pubg",
            rank = 3,
            appId = 578080,
            title = "PUBG: BATTLEGROUNDS",
            shortCode = "PUBG",
            gradientStart = Color(0xFFEAB308),
            gradientEnd = Color(0xFFB45309),
            developer = "KRAFTON, Inc.",
            genres = listOf("FPS/Taktiskt", "Gratis att spela", "Överlevnad/Sandbox"),
            isFreeToPlay = true,
            editionNote = "Battlegrounds Plus",
            playersNow = 689450,
            peak24h = 792300,
            allTimePeak = 3257248,
            steamPriceEur = 12.99,
            deluxeMultiplier = 1.9,
            historicalLowEur = 9.49,
            trend24h = listOf(520, 440, 390, 380, 420, 510, 630, 740, 792, 760, 720, 689),
            offers = listOf(
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Gratis / Plus DLC", 12.99, StoreUrlType.STEAM),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 9.79, StoreUrlType.G2A),
                StoreOffer("Eneba", false, "Global / EU", "Steam Key", 10.15, StoreUrlType.ENEBA),
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 10.49, StoreUrlType.CDKEYS)
            )
        ),
        SteamGame(
            id = "phantom-blade-zero",
            rank = 4,
            appId = 2841920,
            title = "Phantom Blade Zero",
            shortCode = "PBZ",
            gradientStart = Color(0xFF334155),
            gradientEnd = Color(0xFF0E7490),
            developer = "S-GAME",
            genres = listOf("RPG/Action"),
            isFreeToPlay = false,
            playersNow = 512840,
            peak24h = 589200,
            allTimePeak = 642100,
            steamPriceEur = 69.99,
            deluxeMultiplier = 1.28,
            historicalLowEur = 43.99,
            trend24h = listOf(380, 340, 310, 300, 330, 390, 450, 510, 560, 589, 550, 512),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 44.49, StoreUrlType.CDKEYS),
                StoreOffer("Eneba", false, "EU / Global", "Steam Key", 46.20, StoreUrlType.ENEBA),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 47.90, StoreUrlType.G2A),
                StoreOffer("Fanatical", true, "Global / EU", "Steam Key", 57.99, StoreUrlType.FANATICAL),
                StoreOffer("Green Man Gaming", true, "Global / EU", "Steam Key", 59.49, StoreUrlType.GMG),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 69.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "monster-hunter-wilds",
            rank = 5,
            appId = 2246340,
            title = "Monster Hunter Wilds",
            shortCode = "MHW",
            gradientStart = Color(0xFF059669),
            gradientEnd = Color(0xFF115E59),
            developer = "CAPCOM Co., Ltd.",
            genres = listOf("RPG/Action"),
            isFreeToPlay = false,
            playersNow = 428910,
            peak24h = 495600,
            allTimePeak = 1384608,
            steamPriceEur = 69.99,
            deluxeMultiplier = 1.28,
            historicalLowEur = 36.49,
            trend24h = listOf(310, 280, 250, 245, 270, 320, 380, 430, 475, 495, 460, 428),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 36.99, StoreUrlType.CDKEYS),
                StoreOffer("Kinguin", false, "Global / EU", "Steam Key", 38.49, StoreUrlType.KINGUIN),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 39.20, StoreUrlType.G2A),
                StoreOffer("Green Man Gaming", true, "Global / EU", "Steam Key", 52.49, StoreUrlType.GMG),
                StoreOffer("Fanatical", true, "Global / EU", "Steam Key", 53.99, StoreUrlType.FANATICAL),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 69.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "marvel-rivals",
            rank = 6,
            appId = 2767030,
            title = "Marvel Rivals",
            shortCode = "MR",
            gradientStart = Color(0xFF4F46E5),
            gradientEnd = Color(0xFF6B21A8),
            developer = "NetEase Games",
            genres = listOf("FPS/Taktiskt", "Gratis att spela"),
            isFreeToPlay = true,
            playersNow = 364120,
            peak24h = 418900,
            allTimePeak = 644269,
            steamPriceEur = 0.0,
            deluxeMultiplier = 1.0,
            historicalLowEur = 0.0,
            trend24h = listOf(270, 230, 200, 195, 220, 265, 315, 360, 395, 418, 390, 364),
            offers = listOf(
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Gratis att spela", 0.0, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "apex-legends",
            rank = 7,
            appId = 1172470,
            title = "Apex Legends™",
            shortCode = "APEX",
            gradientStart = Color(0xFFE11D48),
            gradientEnd = Color(0xFF7F1D1D),
            developer = "Respawn Entertainment",
            genres = listOf("FPS/Taktiskt", "Gratis att spela"),
            isFreeToPlay = true,
            playersNow = 298450,
            peak24h = 352100,
            allTimePeak = 624473,
            steamPriceEur = 0.0,
            deluxeMultiplier = 1.0,
            historicalLowEur = 0.0,
            trend24h = listOf(230, 200, 180, 175, 195, 230, 275, 315, 340, 352, 325, 298),
            offers = listOf(
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Gratis att spela", 0.0, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "slay-the-spire-2",
            rank = 8,
            appId = 2868840,
            title = "Slay the Spire 2",
            shortCode = "STS2",
            gradientStart = Color(0xFF7C3AED),
            gradientEnd = Color(0xFF86198F),
            developer = "Mega Crit",
            genres = listOf("RPG/Action"),
            isFreeToPlay = false,
            playersNow = 245780,
            peak24h = 279400,
            allTimePeak = 312900,
            steamPriceEur = 24.99,
            deluxeMultiplier = 1.35,
            historicalLowEur = 18.49,
            trend24h = listOf(190, 175, 160, 155, 170, 195, 220, 245, 268, 279, 260, 245),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 18.79, StoreUrlType.CDKEYS),
                StoreOffer("Fanatical", true, "Global / EU", "Steam Key", 19.49, StoreUrlType.FANATICAL),
                StoreOffer("Green Man Gaming", true, "Global / EU", "Steam Key", 19.99, StoreUrlType.GMG),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 20.49, StoreUrlType.G2A),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 24.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "rust",
            rank = 9,
            appId = 252490,
            title = "Rust",
            shortCode = "RUST",
            gradientStart = Color(0xFFEA580C),
            gradientEnd = Color(0xFF292524),
            developer = "Facepunch Studios",
            genres = listOf("Överlevnad/Sandbox", "FPS/Taktiskt"),
            isFreeToPlay = false,
            playersNow = 198640,
            peak24h = 224500,
            allTimePeak = 245243,
            steamPriceEur = 39.99,
            deluxeMultiplier = 1.4,
            historicalLowEur = 16.99,
            trend24h = listOf(155, 140, 128, 125, 138, 158, 180, 202, 218, 224, 210, 198),
            offers = listOf(
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 19.49, StoreUrlType.G2A),
                StoreOffer("Eneba", false, "Global / EU", "Steam Gift", 20.29, StoreUrlType.ENEBA),
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 21.19, StoreUrlType.CDKEYS),
                StoreOffer("Kinguin", false, "Global / EU", "Steam Key", 21.80, StoreUrlType.KINGUIN),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 39.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "forza-horizon-6",
            rank = 10,
            appId = 2915410,
            title = "Forza Horizon 6",
            shortCode = "FH6",
            gradientStart = Color(0xFFEC4899),
            gradientEnd = Color(0xFF0891B2),
            developer = "Playground Games",
            genres = listOf("RPG/Action"),
            isFreeToPlay = false,
            playersNow = 176320,
            peak24h = 204800,
            allTimePeak = 248900,
            steamPriceEur = 69.99,
            deluxeMultiplier = 1.42,
            historicalLowEur = 46.99,
            trend24h = listOf(130, 118, 108, 105, 115, 135, 160, 182, 198, 204, 190, 176),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 47.49, StoreUrlType.CDKEYS),
                StoreOffer("Eneba", false, "Global / EU", "Steam Key", 49.90, StoreUrlType.ENEBA),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 51.20, StoreUrlType.G2A),
                StoreOffer("Green Man Gaming", true, "Global / EU", "Steam Key", 59.49, StoreUrlType.GMG),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 69.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "baldurs-gate-3",
            rank = 11,
            appId = 1086940,
            title = "Baldur's Gate 3",
            shortCode = "BG3",
            gradientStart = Color(0xFFB45309),
            gradientEnd = Color(0xFF581C87),
            developer = "Larian Studios",
            genres = listOf("RPG/Action"),
            isFreeToPlay = false,
            playersNow = 152890,
            peak24h = 178400,
            allTimePeak = 875343,
            steamPriceEur = 59.99,
            deluxeMultiplier = 1.16,
            historicalLowEur = 38.99,
            trend24h = listOf(115, 105, 96, 94, 102, 118, 138, 156, 170, 178, 165, 152),
            offers = listOf(
                StoreOffer("G2A", false, "Global / EU", "Steam Gift", 41.99, StoreUrlType.G2A),
                StoreOffer("Eneba", false, "EU", "Steam Gift", 42.80, StoreUrlType.ENEBA),
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 44.49, StoreUrlType.CDKEYS),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 59.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "stalker-2",
            rank = 12,
            appId = 1643320,
            title = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
            shortCode = "STLK2",
            gradientStart = Color(0xFFA16207),
            gradientEnd = Color(0xFF022C22),
            developer = "GSC Game World",
            genres = listOf("FPS/Taktiskt", "Överlevnad/Sandbox", "RPG/Action"),
            isFreeToPlay = false,
            playersNow = 128450,
            peak24h = 149200,
            allTimePeak = 121335,
            steamPriceEur = 59.99,
            deluxeMultiplier = 1.33,
            historicalLowEur = 31.49,
            trend24h = listOf(95, 86, 78, 76, 84, 98, 115, 132, 144, 149, 138, 128),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 31.99, StoreUrlType.CDKEYS),
                StoreOffer("Eneba", false, "Global / EU", "Steam Key", 33.40, StoreUrlType.ENEBA),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 34.15, StoreUrlType.G2A),
                StoreOffer("Fanatical", true, "Global / EU", "Steam Key", 44.99, StoreUrlType.FANATICAL),
                StoreOffer("Green Man Gaming", true, "Global / EU", "Steam Key", 46.79, StoreUrlType.GMG),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 59.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "dayz",
            rank = 13,
            appId = 221100,
            title = "DayZ",
            shortCode = "DAYZ",
            gradientStart = Color(0xFF57534E),
            gradientEnd = Color(0xFF064E3B),
            developer = "Bohemia Interactive",
            genres = listOf("Överlevnad/Sandbox", "FPS/Taktiskt"),
            isFreeToPlay = false,
            playersNow = 109820,
            peak24h = 126400,
            allTimePeak = 87412,
            steamPriceEur = 44.99,
            deluxeMultiplier = 1.3,
            historicalLowEur = 18.99,
            trend24h = listOf(82, 74, 68, 66, 72, 84, 98, 112, 122, 126, 118, 109),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 21.49, StoreUrlType.CDKEYS),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 22.89, StoreUrlType.G2A),
                StoreOffer("Kinguin", false, "Global / EU", "Steam Key", 23.40, StoreUrlType.KINGUIN),
                StoreOffer("Fanatical", true, "Global / EU", "Steam Key", 29.99, StoreUrlType.FANATICAL),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 44.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "bannerlord",
            rank = 14,
            appId = 261550,
            title = "Mount & Blade II: Bannerlord",
            shortCode = "M&B2",
            gradientStart = Color(0xFF991B1B),
            gradientEnd = Color(0xFF78350F),
            developer = "TaleWorlds Entertainment",
            genres = listOf("RPG/Action", "Överlevnad/Sandbox"),
            isFreeToPlay = false,
            playersNow = 94510,
            peak24h = 112300,
            allTimePeak = 248216,
            steamPriceEur = 49.99,
            deluxeMultiplier = 1.2,
            historicalLowEur = 19.49,
            trend24h = listOf(72, 65, 60, 58, 63, 74, 86, 98, 108, 112, 102, 94),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 20.49, StoreUrlType.CDKEYS),
                StoreOffer("Eneba", false, "Global / EU", "Steam Key", 21.80, StoreUrlType.ENEBA),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 22.49, StoreUrlType.G2A),
                StoreOffer("Green Man Gaming", true, "Global / EU", "Steam Key", 29.99, StoreUrlType.GMG),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 49.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "ets2",
            rank = 15,
            appId = 227300,
            title = "Euro Truck Simulator 2",
            shortCode = "ETS2",
            gradientStart = Color(0xFF0284C7),
            gradientEnd = Color(0xFF1E3A8A),
            developer = "SCS Software",
            genres = listOf("Överlevnad/Sandbox"),
            isFreeToPlay = false,
            playersNow = 84290,
            peak24h = 98700,
            allTimePeak = 72678,
            steamPriceEur = 19.99,
            deluxeMultiplier = 1.75,
            historicalLowEur = 4.49,
            trend24h = listOf(62, 55, 49, 48, 53, 64, 76, 88, 95, 98, 91, 84),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 4.79, StoreUrlType.CDKEYS),
                StoreOffer("Eneba", false, "Global / EU", "Steam Key", 4.99, StoreUrlType.ENEBA),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 5.29, StoreUrlType.G2A),
                StoreOffer("Fanatical", true, "Global / EU", "Steam Key", 6.99, StoreUrlType.FANATICAL),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 19.99, StoreUrlType.STEAM)
            )
        ),
        SteamGame(
            id = "ready-or-not",
            rank = 16,
            appId = 1144200,
            title = "Ready or Not",
            shortCode = "RoN",
            gradientStart = Color(0xFF1E40AF),
            gradientEnd = Color(0xFF0F172A),
            developer = "VOID Interactive",
            genres = listOf("FPS/Taktiskt"),
            isFreeToPlay = false,
            playersNow = 71640,
            peak24h = 84900,
            allTimePeak = 55110,
            steamPriceEur = 49.99,
            deluxeMultiplier = 1.35,
            historicalLowEur = 22.99,
            trend24h = listOf(54, 48, 43, 42, 46, 55, 65, 74, 81, 84, 78, 71),
            offers = listOf(
                StoreOffer("CDKeys (Loaded)", false, "Global / EU", "Steam Key", 23.49, StoreUrlType.CDKEYS),
                StoreOffer("G2A", false, "Global / EU", "Steam Key", 24.90, StoreUrlType.G2A),
                StoreOffer("Kinguin", false, "Global / EU", "Steam Key", 25.49, StoreUrlType.KINGUIN),
                StoreOffer("Fanatical", true, "Global / EU", "Steam Key", 33.49, StoreUrlType.FANATICAL),
                StoreOffer("Steam (Officiell)", true, "Global / EU", "Steam Direkt", 49.99, StoreUrlType.STEAM)
            )
        )
    )

    fun formatNumber(num: Int): String {
        return NumberFormat.getNumberInstance(Locale.US).format(num)
    }

    fun formatPrice(eurAmount: Double, currency: CurrencyMode): String {
        if (eurAmount <= 0.0) return "Gratis"
        return when (currency) {
            CurrencyMode.EUR -> String.format(Locale.forLanguageTag("sv-SE"), "%.2f €", eurAmount)
            CurrencyMode.SEK -> "${(eurAmount * EXCHANGE_RATE_SEK).roundToInt()} kr"
        }
    }

    fun getNumericPrice(eurAmount: Double, currency: CurrencyMode): Double {
        if (eurAmount <= 0.0) return 0.0
        return when (currency) {
            CurrencyMode.EUR -> (eurAmount * 100.0).roundToInt() / 100.0
            CurrencyMode.SEK -> (eurAmount * EXCHANGE_RATE_SEK).roundToInt().toDouble()
        }
    }

    fun toEurFromCurrency(amount: Double, currency: CurrencyMode): Double {
        return when (currency) {
            CurrencyMode.EUR -> amount
            CurrencyMode.SEK -> amount / EXCHANGE_RATE_SEK
        }
    }

    fun getBestOffer(game: SteamGame, edition: GameEdition = GameEdition.STANDARD): Pair<StoreOffer, Double> {
        val mult = if (edition == GameEdition.DELUXE) game.deluxeMultiplier else 1.0
        val best = game.offers.minByOrNull { it.priceEur } ?: game.offers.first()
        return best to (best.priceEur * mult)
    }

    fun getDiscountPercent(game: SteamGame, edition: GameEdition = GameEdition.STANDARD): Int {
        if (game.steamPriceEur <= 0.0) return 0
        val mult = if (edition == GameEdition.DELUXE) game.deluxeMultiplier else 1.0
        val steamPrice = game.steamPriceEur * mult
        val (_, bestPrice) = getBestOffer(game, edition)
        val diff = steamPrice - bestPrice
        if (diff <= 0.0) return 0
        return ((diff / steamPrice) * 100.0).roundToInt()
    }

    fun buildStoreUrl(urlType: StoreUrlType, game: SteamGame): String {
        val q = Uri.encode(game.title)
        return when (urlType) {
            StoreUrlType.STEAM -> "https://store.steampowered.com/app/${game.appId}/"
            StoreUrlType.G2A -> "https://www.g2a.com/search?query=$q"
            StoreUrlType.CDKEYS -> "https://www.cdkeys.com/catalogsearch/result/?q=$q"
            StoreUrlType.ENEBA -> "https://www.eneba.com/store/all?text=$q"
            StoreUrlType.KINGUIN -> "https://www.kinguin.net/listing?phrase=$q"
            StoreUrlType.GMG -> "https://www.greenmangaming.com/search?query=$q"
            StoreUrlType.FANATICAL -> "https://www.fanatical.com/en/search?search=$q"
        }
    }

    fun buildSteamDbUrl(game: SteamGame): String = "https://steamdb.info/app/${game.appId}/"
    fun buildGgDealsUrl(game: SteamGame): String = "https://gg.deals/games/?title=${Uri.encode(game.title)}"
    fun buildItadUrl(game: SteamGame): String = "https://isthereanydeal.com/search/?q=${Uri.encode(game.title)}"
}
