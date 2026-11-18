package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.model.GameCategory
import com.example.model.SteamGame
import com.example.model.StoreOffer
import com.example.model.StoreUrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class SteamAppDetails(
    val appId: Int,
    val title: String,
    val developer: String,
    val genres: List<String>,
    val isFreeToPlay: Boolean,
    val headerImageUrl: String,
    val shortDescription: String,
    val releaseDate: String,
    val initialPriceEur: Double,
    val finalPriceEur: Double,
    val discountPercent: Int
)

class SteamPulseRepository(
    private val cacheDir: File? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    private data class CacheEntry<T>(
        val value: T,
        val cachedAtMs: Long
    )

    private val categoryMemoryCache = ConcurrentHashMap<GameCategory, CacheEntry<List<SteamGame>>>()
    private val specialsMemoryCache = ConcurrentHashMap<String, CacheEntry<List<SteamGame>>>()
    private val searchMemoryCache = ConcurrentHashMap<String, CacheEntry<List<SteamGame>>>()
    private val appDetailsMemoryCache = ConcurrentHashMap<Int, CacheEntry<SteamAppDetails>>()
    private val playersMemoryCache = ConcurrentHashMap<Int, CacheEntry<Int>>()

    private val diskCacheFolder: File? by lazy {
        cacheDir?.let { File(it, "steam_pulse_cache").apply { mkdirs() } }
    }

    suspend fun fetchSteamSpecials(forceRefresh: Boolean = false): List<SteamGame> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            specialsMemoryCache["specials"]?.takeIf { now - it.cachedAtMs <= CATEGORY_TTL_MS }?.let {
                return@withContext it.value
            }
        }
        val specials = fetchFeaturedCategoryItems("specials", forceRefresh)
            .filter { it.steamDiscountPercent > 0 }
            .sortedByDescending { it.steamDiscountPercent }
        if (specials.isNotEmpty()) {
            specialsMemoryCache["specials"] = CacheEntry(specials, now)
        }
        specials
    }

    suspend fun fetchCategoryGames(
        category: GameCategory,
        forceRefresh: Boolean = false
    ): List<SteamGame> = withContext(Dispatchers.IO) {
        if (category == GameCategory.FOLLOWED) return@withContext emptyList()

        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            categoryMemoryCache[category]?.takeIf { now - it.cachedAtMs <= CATEGORY_TTL_MS }?.let {
                return@withContext it.value
            }
        }

        val games = when (category) {
            GameCategory.MOST_PLAYED -> fetchMostPlayedGamesInternal(forceRefresh)
            GameCategory.TOP_SELLERS -> fetchFeaturedCategoryItems("top_sellers", forceRefresh)
            GameCategory.NEW_AND_TRENDING -> fetchNewAndTrendingInternal(forceRefresh)
            GameCategory.FOLLOWED -> emptyList()
        }

        if (games.isNotEmpty()) {
            categoryMemoryCache[category] = CacheEntry(games, now)
        }
        games
    }

    suspend fun searchStoreGames(query: String): List<SteamGame> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val cacheKey = trimmed.lowercase()
        val now = System.currentTimeMillis()
        searchMemoryCache[cacheKey]?.takeIf { now - it.cachedAtMs <= SEARCH_TTL_MS }?.let {
            return@withContext it.value
        }

        val url = buildStoreSearchUrl(trimmed)
        val body = executeGet(url)
        val parsed = parseStoreSearchJson(body)
        if (parsed.isNotEmpty()) {
            searchMemoryCache[cacheKey] = CacheEntry(parsed, now)
        }
        parsed
    }

    suspend fun fetchCurrentPlayers(
        appId: Int,
        forceRefresh: Boolean = false
    ): Int? = withContext(Dispatchers.IO) {
        if (appId <= 0) return@withContext null
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            playersMemoryCache[appId]?.takeIf { now - it.cachedAtMs <= PLAYERS_TTL_MS }?.let {
                return@withContext it.value
            }
            readDiskCache("players_$appId.json", PLAYERS_TTL_MS)?.let { cachedJson ->
                val count = parseCurrentPlayersJson(cachedJson)
                if (count != null) {
                    playersMemoryCache[appId] = CacheEntry(count, now)
                    return@withContext count
                }
            }
        }

        val url = buildCurrentPlayersUrl(appId)
        val body = runCatching { executeGet(url) }.getOrNull() ?: return@withContext null
        val count = parseCurrentPlayersJson(body) ?: return@withContext null
        playersMemoryCache[appId] = CacheEntry(count, now)
        writeDiskCache("players_$appId.json", body)
        count
    }

    suspend fun fetchAppDetails(
        appId: Int,
        forceRefresh: Boolean = false
    ): SteamAppDetails? = withContext(Dispatchers.IO) {
        if (appId <= 0) return@withContext null
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            appDetailsMemoryCache[appId]?.takeIf { now - it.cachedAtMs <= APP_DETAILS_TTL_MS }?.let {
                return@withContext it.value
            }
            readDiskCache("app_$appId.json", APP_DETAILS_TTL_MS)?.let { cachedJson ->
                val parsed = parseAppDetailsJson(cachedJson, appId)
                if (parsed != null) {
                    appDetailsMemoryCache[appId] = CacheEntry(parsed, now)
                    return@withContext parsed
                }
            }
        }

        val url = buildAppDetailsUrl(appId)
        val body = runCatching { executeGet(url) }.getOrNull() ?: return@withContext null
        val parsed = parseAppDetailsJson(body, appId) ?: return@withContext null
        appDetailsMemoryCache[appId] = CacheEntry(parsed, now)
        writeDiskCache("app_$appId.json", body)
        parsed
    }

    suspend fun enrichGame(game: SteamGame, forceRefresh: Boolean = false): SteamGame = coroutineScope {
        if (game.appId <= 0) return@coroutineScope game
        val detailsDeferred = async { fetchAppDetails(game.appId, forceRefresh) }
        val playersDeferred = async { fetchCurrentPlayers(game.appId, forceRefresh) }
        val details = detailsDeferred.await()
        val players = playersDeferred.await()
        mergeGameMetadata(game, details, players)
    }

    suspend fun enrichGames(
        games: List<SteamGame>,
        limit: Int = 20,
        concurrency: Int = 5
    ): List<SteamGame> = coroutineScope {
        if (games.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val target = games.take(limit)
        val enrichedTarget = target.map { game ->
            async {
                semaphore.withPermit {
                    runCatching { enrichGame(game) }.getOrDefault(game)
                }
            }
        }.awaitAll()

        if (games.size <= limit) enrichedTarget
        else enrichedTarget + games.drop(limit)
    }

    suspend fun buildGamesForAppIds(appIds: List<Int>): List<SteamGame> = coroutineScope {
        val uniqueIds = appIds.filter { it > 0 }.distinct()
        if (uniqueIds.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(5)
        uniqueIds.mapIndexed { index, appId ->
            async {
                semaphore.withPermit {
                    val base = createSkeletonGame(appId = appId, rank = index + 1)
                    runCatching { enrichGame(base) }.getOrDefault(base)
                }
            }
        }.awaitAll()
    }

    private suspend fun fetchMostPlayedGamesInternal(forceRefresh: Boolean): List<SteamGame> {
        val rawChartsJson = if (!forceRefresh) {
            readDiskCache("cat_most_played.json", CATEGORY_TTL_MS)
        } else {
            null
        } ?: runCatching {
            executeGet(MOST_PLAYED_CHARTS_URL).also { writeDiskCache("cat_most_played.json", it) }
        }.getOrNull()

        val chartGames = rawChartsJson?.let { parseMostPlayedChartsJson(it, limit = 25) }.orEmpty()
        if (chartGames.isNotEmpty()) {
            return chartGames
        }

        // Live fallback to SteamSpy top100in2weeks if Steam Charts service is temporarily unreachable
        val spyJson = executeGet(STEAMSPY_TOP100_URL)
        return parseSteamSpyMostPlayedJson(spyJson, limit = 25)
    }

    private suspend fun fetchFeaturedCategoriesJson(forceRefresh: Boolean): String {
        if (!forceRefresh) {
            readDiskCache("cat_featured_se.json", CATEGORY_TTL_MS)?.let { return it }
        }
        val body = executeGet(FEATURED_CATEGORIES_URL)
        if (body.isNotBlank()) {
            writeDiskCache("cat_featured_se.json", body)
        }
        return body
    }

    private suspend fun fetchFeaturedCategoryItems(
        sectionKey: String,
        forceRefresh: Boolean
    ): List<SteamGame> {
        val json = fetchFeaturedCategoriesJson(forceRefresh)
        return parseFeaturedCategoriesJson(json, sectionKey)
    }

    private suspend fun fetchNewAndTrendingInternal(forceRefresh: Boolean): List<SteamGame> {
        val json = fetchFeaturedCategoriesJson(forceRefresh)
        val newReleases = parseFeaturedCategoriesJson(json, "new_releases")
        val specials = parseFeaturedCategoriesJson(json, "specials")
        return (newReleases + specials)
            .distinctBy { it.appId }
            .take(25)
            .mapIndexed { index, game -> game.copy(rank = index + 1) }
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Steam svarade med HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw IOException("Kunde inte hämta data från Steam: ${e.localizedMessage ?: "nätverksfel"}", e)
        }
    }

    private fun readDiskCache(filename: String, ttlMs: Long): String? {
        val dir = diskCacheFolder ?: return null
        val file = File(dir, filename)
        if (!file.exists()) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (age > ttlMs) return null
        return runCatching { file.readText().takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun writeDiskCache(filename: String, content: String) {
        val dir = diskCacheFolder ?: return
        if (content.isBlank()) return
        runCatching {
            File(dir, filename).writeText(content)
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private const val CATEGORY_TTL_MS = 5 * 60 * 1000L
        private const val SEARCH_TTL_MS = 5 * 60 * 1000L
        private const val PLAYERS_TTL_MS = 5 * 60 * 1000L
        private const val APP_DETAILS_TTL_MS = 30 * 60 * 1000L

        const val MOST_PLAYED_CHARTS_URL =
            "https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/"
        const val FEATURED_CATEGORIES_URL =
            "https://store.steampowered.com/api/featuredcategories/?cc=SE&l=swedish"
        const val STEAMSPY_TOP100_URL =
            "https://steamspy.com/api.php?request=top100in2weeks"

        fun buildStoreSearchUrl(query: String): String {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            return "https://store.steampowered.com/api/storesearch/?term=$encoded&l=swedish&cc=SE"
        }

        fun buildCurrentPlayersUrl(appId: Int): String =
            "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$appId"

        fun buildAppDetailsUrl(appId: Int): String =
            "https://store.steampowered.com/api/appdetails?appids=$appId&cc=se&l=swedish"

        fun steamHeaderUrl(appId: Int): String =
            "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"

        fun convertSteamCentsToEur(cents: Int, currencyCode: String): Double {
            if (cents <= 0) return 0.0
            val major = cents / 100.0
            return when (currencyCode.uppercase()) {
                "SEK" -> major / SteamGamesCatalog.EXCHANGE_RATE_SEK
                "USD" -> major * 0.92
                else -> major
            }
        }

        private fun gradientPairForAppId(appId: Int): Pair<Color, Color> {
            val palette = listOf(
                Color(0xFF0EA5E9) to Color(0xFF1E3A8A),
                Color(0xFF10B981) to Color(0xFF064E3B),
                Color(0xFFF59E0B) to Color(0xFF9A3412),
                Color(0xFF6366F1) to Color(0xFF312E81),
                Color(0xFFEC4899) to Color(0xFF831843),
                Color(0xFF14B8A6) to Color(0xFF134E4A)
            )
            return palette[kotlin.math.abs(appId) % palette.size]
        }

        private fun buildShortCode(title: String, appId: Int): String {
            val letters = title
                .split(" ", ":", "-")
                .filter { it.isNotBlank() }
                .take(3)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
            return letters.ifBlank { appId.toString().take(4) }
        }

        fun createSkeletonGame(
            appId: Int,
            rank: Int = 0,
            title: String = "Steam App #$appId",
            playersNow: Int = 0,
            peak24h: Int = 0,
            initialPriceEur: Double = 0.0,
            finalPriceEur: Double = 0.0,
            discountPercent: Int = 0,
            isFreeToPlay: Boolean = false,
            headerImageUrl: String = steamHeaderUrl(appId)
        ): SteamGame {
            val (startColor, endColor) = gradientPairForAppId(appId)
            val offer = StoreOffer(
                store = "Steam (Officiell)",
                isOfficial = true,
                region = "SE / EU",
                platform = if (isFreeToPlay) "Gratis att spela" else "Steam Direkt",
                priceEur = finalPriceEur,
                urlType = StoreUrlType.STEAM
            )
            return SteamGame(
                id = "app_$appId",
                rank = rank,
                appId = appId,
                title = title,
                shortCode = buildShortCode(title, appId),
                gradientStart = startColor,
                gradientEnd = endColor,
                developer = "Steam",
                genres = emptyList(),
                isFreeToPlay = isFreeToPlay,
                playersNow = playersNow,
                peak24h = peak24h,
                allTimePeak = peak24h,
                steamPriceEur = initialPriceEur,
                historicalLowEur = finalPriceEur,
                offers = listOf(offer),
                headerImageUrl = headerImageUrl.ifBlank { steamHeaderUrl(appId) },
                finalPriceEur = finalPriceEur,
                steamDiscountPercent = discountPercent,
                isPlayersLoaded = playersNow > 0
            )
        }

        fun mergeGameMetadata(
            base: SteamGame,
            details: SteamAppDetails?,
            playersCount: Int?
        ): SteamGame {
            val resolvedTitle = details?.title?.takeIf { it.isNotBlank() } ?: base.title
            val resolvedDev = details?.developer?.takeIf { it.isNotBlank() } ?: base.developer
            val resolvedGenres = details?.genres?.takeIf { it.isNotEmpty() } ?: base.genres
            val resolvedFree = details?.isFreeToPlay ?: base.isFreeToPlay
            val resolvedInitial = details?.initialPriceEur ?: base.steamPriceEur
            val resolvedFinal = if (resolvedFree) 0.0 else (details?.finalPriceEur ?: base.finalPriceEur)
            val resolvedDiscount = details?.discountPercent ?: base.steamDiscountPercent
            val resolvedHeader = details?.headerImageUrl?.takeIf { it.isNotBlank() } ?: base.headerImageUrl
            val resolvedPlayers = playersCount ?: base.playersNow
            val resolvedPeak = maxOf(base.peak24h, resolvedPlayers)

            val steamOffer = StoreOffer(
                store = "Steam (Officiell)",
                isOfficial = true,
                region = "SE / EU",
                platform = if (resolvedFree) "Gratis att spela" else "Steam Direkt",
                priceEur = resolvedFinal,
                urlType = StoreUrlType.STEAM
            )

            return base.copy(
                title = resolvedTitle,
                shortCode = buildShortCode(resolvedTitle, base.appId),
                developer = resolvedDev,
                genres = resolvedGenres,
                isFreeToPlay = resolvedFree,
                playersNow = resolvedPlayers,
                peak24h = resolvedPeak,
                allTimePeak = maxOf(base.allTimePeak, resolvedPeak),
                steamPriceEur = resolvedInitial,
                finalPriceEur = resolvedFinal,
                historicalLowEur = resolvedFinal,
                steamDiscountPercent = resolvedDiscount,
                offers = listOf(steamOffer),
                headerImageUrl = resolvedHeader,
                shortDescription = details?.shortDescription ?: base.shortDescription,
                releaseDate = details?.releaseDate ?: base.releaseDate,
                isDetailsLoaded = details != null || base.isDetailsLoaded,
                isPlayersLoaded = playersCount != null || base.isPlayersLoaded
            )
        }

        fun parseStoreSearchJson(jsonString: String): List<SteamGame> {
            if (jsonString.isBlank()) return emptyList()
            val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return emptyList()
            val items = root.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    val appId = obj.optInt("id", 0)
                    if (appId <= 0) continue
                    val name = obj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val priceObj = obj.optJSONObject("price")
                    val currency = priceObj?.optString("currency", "EUR").orEmpty().ifBlank { "EUR" }
                    val initialCents = priceObj?.optInt("initial", 0) ?: 0
                    val finalCents = priceObj?.optInt("final", 0) ?: 0
                    val discount = priceObj?.optInt("discount_percent", 0) ?: 0

                    val initialEur = convertSteamCentsToEur(initialCents, currency)
                    val finalEur = convertSteamCentsToEur(finalCents, currency)
                    val isFree = priceObj == null || (initialCents == 0 && finalCents == 0)

                    add(
                        createSkeletonGame(
                            appId = appId,
                            rank = size + 1,
                            title = name,
                            initialPriceEur = initialEur,
                            finalPriceEur = finalEur,
                            discountPercent = discount,
                            isFreeToPlay = isFree,
                            headerImageUrl = steamHeaderUrl(appId)
                        )
                    )
                }
            }.distinctBy { it.appId }
        }

        fun parseCurrentPlayersJson(jsonString: String): Int? {
            if (jsonString.isBlank()) return null
            val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return null
            val response = root.optJSONObject("response") ?: return null
            if (response.optInt("result", 0) != 1) return null
            if (!response.has("player_count")) return null
            return response.optInt("player_count", -1).takeIf { it >= 0 }
        }

        fun parseAppDetailsJson(jsonString: String, appId: Int): SteamAppDetails? {
            if (jsonString.isBlank()) return null
            val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return null
            val appObj = root.optJSONObject(appId.toString()) ?: return null
            if (!appObj.optBoolean("success", false)) return null
            val data = appObj.optJSONObject("data") ?: return null

            val title = data.optString("name", "").trim()
            if (title.isBlank()) return null

            val devArray = data.optJSONArray("developers") ?: JSONArray()
            val developers = buildList {
                for (i in 0 until devArray.length()) {
                    devArray.optString(i, "").trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
            val developer = developers.joinToString(", ").ifBlank { "Steam" }

            val genresArray = data.optJSONArray("genres") ?: JSONArray()
            val genres = buildList {
                for (i in 0 until genresArray.length()) {
                    genresArray.optJSONObject(i)?.optString("description", "")?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { add(it) }
                }
            }

            val isFree = data.optBoolean("is_free", false)
            val headerImage = data.optString("header_image", "").trim()
                .ifBlank { steamHeaderUrl(appId) }
            val shortDescription = data.optString("short_description", "").trim()
            val releaseDate = data.optJSONObject("release_date")?.optString("date", "").orEmpty().trim()

            val priceObj = data.optJSONObject("price_overview")
            val currency = priceObj?.optString("currency", "EUR").orEmpty().ifBlank { "EUR" }
            val initialCents = priceObj?.optInt("initial", 0) ?: 0
            val finalCents = priceObj?.optInt("final", 0) ?: 0
            val discount = priceObj?.optInt("discount_percent", 0) ?: 0

            val initialEur = if (isFree) 0.0 else convertSteamCentsToEur(initialCents, currency)
            val finalEur = if (isFree) 0.0 else convertSteamCentsToEur(finalCents, currency)

            return SteamAppDetails(
                appId = appId,
                title = title,
                developer = developer,
                genres = genres,
                isFreeToPlay = isFree || (priceObj == null && finalEur <= 0.0),
                headerImageUrl = headerImage,
                shortDescription = shortDescription,
                releaseDate = releaseDate,
                initialPriceEur = initialEur,
                finalPriceEur = finalEur,
                discountPercent = discount
            )
        }

        fun parseMostPlayedChartsJson(jsonString: String, limit: Int = 25): List<SteamGame> {
            if (jsonString.isBlank()) return emptyList()
            val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return emptyList()
            val ranks = root.optJSONObject("response")?.optJSONArray("ranks") ?: return emptyList()
            return buildList {
                for (i in 0 until ranks.length()) {
                    if (size >= limit) break
                    val obj = ranks.optJSONObject(i) ?: continue
                    val appId = obj.optInt("appid", 0)
                    if (appId <= 0) continue
                    val rank = obj.optInt("rank", size + 1)
                    val concurrent = obj.optInt("concurrent_in_game", 0)
                    val peak = obj.optInt("peak_in_game", 0)
                    add(
                        createSkeletonGame(
                            appId = appId,
                            rank = rank,
                            title = "Steam App #$appId",
                            playersNow = if (concurrent > 0) concurrent else peak,
                            peak24h = peak
                        )
                    )
                }
            }
        }

        fun parseFeaturedCategoriesJson(jsonString: String, sectionKey: String): List<SteamGame> {
            if (jsonString.isBlank()) return emptyList()
            val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return emptyList()
            val section = root.optJSONObject(sectionKey) ?: return emptyList()
            val items = section.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    val appId = obj.optInt("id", 0)
                    if (appId <= 0) continue
                    val name = obj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val currency = obj.optString("currency", "EUR").ifBlank { "EUR" }
                    val originalCents = obj.optInt("original_price", 0)
                    val finalCents = obj.optInt("final_price", 0)
                    val discount = obj.optInt("discount_percent", 0)
                    val header = obj.optString("header_image", "").trim()
                        .ifBlank { obj.optString("large_capsule_image", "").trim() }
                        .ifBlank { steamHeaderUrl(appId) }

                    val initialEur = convertSteamCentsToEur(originalCents, currency)
                    val finalEur = convertSteamCentsToEur(finalCents, currency)
                    val isFree = originalCents == 0 && finalCents == 0

                    add(
                        createSkeletonGame(
                            appId = appId,
                            rank = size + 1,
                            title = name,
                            initialPriceEur = if (initialEur > 0.0) initialEur else finalEur,
                            finalPriceEur = finalEur,
                            discountPercent = discount,
                            isFreeToPlay = isFree,
                            headerImageUrl = header
                        )
                    )
                }
            }.distinctBy { it.appId }
        }

        fun parseSteamSpyMostPlayedJson(jsonString: String, limit: Int = 25): List<SteamGame> {
            if (jsonString.isBlank()) return emptyList()
            val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return emptyList()
            val parsed = buildList {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key) ?: continue
                    val appId = obj.optInt("appid", key.toIntOrNull() ?: 0)
                    if (appId <= 0) continue
                    val name = obj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val ccu = obj.optInt("ccu", 0)
                    val priceCents = obj.optString("price", "0").toIntOrNull() ?: obj.optInt("price", 0)
                    val initialCents = obj.optString("initialprice", "0").toIntOrNull() ?: obj.optInt("initialprice", 0)
                    val discount = obj.optString("discount", "0").toIntOrNull() ?: obj.optInt("discount", 0)

                    val finalEur = convertSteamCentsToEur(priceCents, "USD")
                    val initialEur = convertSteamCentsToEur(initialCents, "USD").let {
                        if (it > 0.0) it else finalEur
                    }
                    add(
                        createSkeletonGame(
                            appId = appId,
                            rank = 0,
                            title = name,
                            playersNow = ccu,
                            peak24h = ccu,
                            initialPriceEur = initialEur,
                            finalPriceEur = finalEur,
                            discountPercent = discount,
                            isFreeToPlay = priceCents == 0 && initialCents == 0
                        )
                    )
                }
            }
            return parsed.sortedByDescending { it.playersNow }
                .take(limit)
                .mapIndexed { idx, item -> item.copy(rank = idx + 1) }
        }
    }
}
