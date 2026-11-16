package com.example.data

import android.text.Html
import com.example.model.FollowedFeedModItem
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamSubscribedSyncResult
import com.example.model.WorkshopFollowSource
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopLeaderboardMode
import com.example.model.WorkshopModItem
import com.example.model.WorkshopModsFetchResult
import com.example.model.WorkshopSortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SteamProfilePrivateException(message: String) : IOException(message)

class SteamSessionExpiredException(message: String) : IOException(message)

class SteamWorkshopRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    private val appNameCache = ConcurrentHashMap<Int, String>()

    private data class HttpFetchOutcome(
        val requestedUrl: String,
        val finalUrl: String,
        val statusCode: Int,
        val body: String
    )

    suspend fun fetchTop100Games(
        mode: WorkshopLeaderboardMode
    ): List<WorkshopGameSummary> = withContext(Dispatchers.IO) {
        val url = "https://steamspy.com/api.php?request=${mode.apiRequest}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        val bodyText = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("SteamSpy svarade med HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw IOException("Kunde inte hämta Topp 100-listan: ${e.localizedMessage ?: "nätverksfel"}", e)
        }

        parseSteamSpyTop100(bodyText, mode)
    }

    suspend fun searchSteamStoreGames(query: String): List<WorkshopGameSummary> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val url = buildStoreSearchUrl(trimmed)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        val bodyText = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Steam Store svarade med HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw IOException("Kunde inte söka i Steam Store: ${e.localizedMessage ?: "nätverksfel"}", e)
        }

        parseSteamStoreSearchJson(bodyText)
    }

    suspend fun fetchWorkshopMods(
        appId: Int,
        sortOrder: WorkshopSortOrder,
        searchQuery: String,
        page: Int
    ): WorkshopModsFetchResult = withContext(Dispatchers.IO) {
        val candidateUrls = buildBrowseCandidateUrls(
            appId = appId,
            sortOrder = sortOrder,
            searchQuery = searchQuery,
            page = page
        )

        var lastOutcome: HttpFetchOutcome? = null
        var firstSuccessOutcome: HttpFetchOutcome? = null
        var rawItems = emptyList<WorkshopModItem>()
        var lastError: IOException? = null

        for (url in candidateUrls) {
            try {
                val outcome = executeSteamGet(url)
                lastOutcome = outcome
                if (firstSuccessOutcome == null) {
                    firstSuccessOutcome = outcome
                }
                val parsed = parseWorkshopResponseBody(outcome.body, appId)
                if (parsed.isNotEmpty()) {
                    rawItems = parsed
                    lastOutcome = outcome
                    break
                }
            } catch (e: IOException) {
                lastError = e
            }
        }

        if (rawItems.isEmpty() && lastError != null && firstSuccessOutcome == null) {
            throw lastError
        }

        val referenceOutcome = if (rawItems.isNotEmpty()) lastOutcome else (firstSuccessOutcome ?: lastOutcome)
        val bodyForPaging = referenceOutcome?.body.orEmpty()
        val hasNextPage = bodyForPaging.contains("p=${page + 1}", ignoreCase = true) ||
            bodyForPaging.contains("ShowPage( ${page + 1}", ignoreCase = true) ||
            rawItems.size >= 24

        if (rawItems.isEmpty()) {
            val diag = buildDiagnosticSummary(
                appId = appId,
                outcome = referenceOutcome,
                itemCount = 0,
                lastError = lastError
            )
            return@withContext WorkshopModsFetchResult(
                mods = emptyList(),
                hasMore = false,
                diagnosticInfo = diag
            )
        }

        val detailsMap = fetchPublishedFileDetailsSafe(rawItems.map { it.publishedFileId })
        val enriched = rawItems.map { item ->
            val detail = detailsMap[item.publishedFileId]
            if (detail != null) {
                item.copy(
                    title = detail.title.ifBlank { item.title },
                    previewUrl = detail.previewUrl.ifBlank { item.previewUrl },
                    subscriptions = detail.subscriptions ?: item.subscriptions,
                    favorites = detail.favorites ?: item.favorites,
                    views = detail.views ?: item.views,
                    fileSizeBytes = detail.fileSizeBytes ?: item.fileSizeBytes,
                    updatedEpochSec = detail.updatedEpochSec ?: item.updatedEpochSec,
                    description = detail.description.ifBlank { item.description },
                    tags = if (detail.tags.isNotEmpty()) detail.tags else item.tags
                )
            } else {
                item
            }
        }

        val diag = buildDiagnosticSummary(
            appId = appId,
            outcome = referenceOutcome,
            itemCount = enriched.size,
            lastError = null
        )
        WorkshopModsFetchResult(
            mods = enriched,
            hasMore = hasNextPage,
            diagnosticInfo = diag
        )
    }

    private fun executeSteamGet(
        url: String,
        cookieHeader: String = STEAM_AGE_BYPASS_COOKIE
    ): HttpFetchOutcome {
        val acceptHeader = if (url.contains("ajax=1")) {
            "application/json, text/javascript, */*; q=0.01"
        } else {
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", acceptHeader)
            .header("Accept-Language", "en-US,en;q=0.9,sv;q=0.8")
            .header("Cookie", cookieHeader)
            .apply {
                if (url.contains("ajax=1")) {
                    header("X-Requested-With", "XMLHttpRequest")
                }
            }
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()
                if (!response.isSuccessful) {
                    throw IOException("Steam Workshop svarade med HTTP ${response.code}")
                }
                HttpFetchOutcome(
                    requestedUrl = url,
                    finalUrl = finalUrl,
                    statusCode = response.code,
                    body = body
                )
            }
        } catch (e: IOException) {
            throw IOException("Kunde inte ladda Steam Workshop: ${e.localizedMessage ?: "nätverksfel"}", e)
        }
    }

    private fun buildDiagnosticSummary(
        appId: Int,
        outcome: HttpFetchOutcome?,
        itemCount: Int,
        lastError: IOException?
    ): String {
        val statusLabel = outcome?.statusCode?.toString() ?: "Fel"
        val base = "AppID: $appId • HTTP $statusLabel • $itemCount träffar"
        if (outcome == null) {
            val errMsg = lastError?.message?.take(48).orEmpty()
            return if (errMsg.isNotBlank()) "$base • $errMsg" else base
        }

        val hints = mutableListOf<String>()
        val reqUri = runCatching { java.net.URI(outcome.requestedUrl) }.getOrNull()
        val finalUri = runCatching { java.net.URI(outcome.finalUrl) }.getOrNull()
        if (reqUri != null && finalUri != null && reqUri.path != finalUri.path) {
            hints.add("Omdirigerad till ${finalUri.path}")
        }
        val lowerBody = outcome.body.lowercase()
        when {
            lowerBody.contains("agecheck") || lowerBody.contains("agegate_box") -> {
                hints.add("Åldersspärr")
            }
            outcome.body.isBlank() -> {
                hints.add("Tomt svar")
            }
            itemCount == 0 && (lowerBody.contains("no items matching") || lowerBody.contains("inga objekt")) -> {
                hints.add("Inga objekt listade")
            }
            itemCount == 0 && !lowerBody.contains("filedetails/") && !lowerBody.contains("filedetails\\/") -> {
                hints.add("Inga Workshop-länkar i HTML")
            }
        }

        return if (hints.isEmpty()) base else "$base • ${hints.joinToString(" • ")}"
    }

    suspend fun fetchModDetails(item: WorkshopModItem): WorkshopModItem = withContext(Dispatchers.IO) {
        val fromApi = fetchPublishedFileDetailsSafe(listOf(item.publishedFileId))[item.publishedFileId]
        if (fromApi != null && fromApi.description.isNotBlank()) {
            return@withContext item.copy(
                title = fromApi.title.ifBlank { item.title },
                previewUrl = fromApi.previewUrl.ifBlank { item.previewUrl },
                subscriptions = fromApi.subscriptions ?: item.subscriptions,
                favorites = fromApi.favorites ?: item.favorites,
                views = fromApi.views ?: item.views,
                fileSizeBytes = fromApi.fileSizeBytes ?: item.fileSizeBytes,
                updatedEpochSec = fromApi.updatedEpochSec ?: item.updatedEpochSec,
                description = fromApi.description,
                tags = if (fromApi.tags.isNotEmpty()) fromApi.tags else item.tags
            )
        }

        // Fallback: parse public filedetails HTML page if API metadata was unavailable
        runCatching {
            val req = Request.Builder()
                .url(item.itemUrl)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9,sv;q=0.8")
                .header("Cookie", STEAM_AGE_BYPASS_COOKIE)
                .get()
                .build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (html.isBlank()) return@runCatching item

            val desc = DETAIL_DESC_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?.let { cleanHtmlText(it) }
                .orEmpty()
            val subscribers = SUBSCRIBERS_STAT_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?.replace(",", "")?.replace(" ", "")?.toLongOrNull()

            item.copy(
                description = desc.ifBlank { item.description },
                subscriptions = subscribers ?: item.subscriptions
            )
        }.getOrDefault(item)
    }

    private fun fetchPublishedFileDetailsSafe(ids: List<String>): Map<String, WorkshopModItem> {
        if (ids.isEmpty()) return emptyMap()
        return runCatching {
            val formBuilder = FormBody.Builder()
                .add("itemcount", ids.size.toString())
            ids.forEachIndexed { index, id ->
                formBuilder.add("publishedfileids[$index]", id)
            }

            val req = Request.Builder()
                .url("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .post(formBuilder.build())
                .build()

            val body = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyMap()
                resp.body?.string().orEmpty()
            }
            if (body.isBlank()) return emptyMap()

            val root = JSONObject(body)
            val detailsArray = root.optJSONObject("response")?.optJSONArray("publishedfiledetails")
                ?: return emptyMap()

            buildMap {
                for (i in 0 until detailsArray.length()) {
                    val obj = detailsArray.optJSONObject(i) ?: continue
                    if (obj.optInt("result", 0) != 1) continue
                    val id = obj.optString("publishedfileid", "")
                    if (id.isBlank()) continue

                    val tagsArray = obj.optJSONArray("tags")
                    val tags = buildList {
                        if (tagsArray != null) {
                            for (t in 0 until tagsArray.length()) {
                                val tag = tagsArray.optJSONObject(t)?.optString("tag", "").orEmpty()
                                if (tag.isNotBlank()) add(tag)
                            }
                        }
                    }

                    val rawDesc = obj.optString("description", "")
                    put(
                        id,
                        WorkshopModItem(
                            publishedFileId = id,
                            appId = obj.optInt("consumer_app_id", 0),
                            title = obj.optString("title", ""),
                            author = "",
                            previewUrl = obj.optString("preview_url", ""),
                            itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=$id",
                            subscriptions = obj.optLong("subscriptions", -1L).takeIf { it >= 0L },
                            favorites = obj.optLong("favorited", -1L).takeIf { it >= 0L },
                            views = obj.optLong("views", -1L).takeIf { it >= 0L },
                            fileSizeBytes = obj.optString("file_size", "").toLongOrNull()
                                ?: obj.optLong("file_size", -1L).takeIf { it > 0L },
                            updatedEpochSec = obj.optLong("time_updated", 0L).takeIf { it > 0L },
                            description = cleanBbCodeAndHtml(rawDesc),
                            tags = tags
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun resolveSteamId64(rawInput: String): String = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            throw IOException("Ange ditt SteamID64 eller din Steam-profil-URL.")
        }

        extractSteamId64FromInput(trimmed)?.let { return@withContext it }

        val vanity = extractVanityFromInput(trimmed)
            ?: throw IOException("Ogiltigt format. Ange SteamID64 (17 siffror som börjar på 7656119) eller länk som steamcommunity.com/id/dittnamn.")

        val encodedVanity = URLEncoder.encode(vanity, "UTF-8")
        val xmlOutcome = executeSteamGet("https://steamcommunity.com/id/$encodedVanity/?xml=1")
        if (xmlOutcome.body.contains("The specified profile could not be found", ignoreCase = true)) {
            throw IOException("Hittade ingen Steam-profil för \"$vanity\". Kontrollera länken eller SteamID64.")
        }
        parseSteamId64FromProfileResponse(xmlOutcome.body)?.let { return@withContext it }

        val htmlOutcome = executeSteamGet("https://steamcommunity.com/id/$encodedVanity/")
        if (htmlOutcome.body.contains("The specified profile could not be found", ignoreCase = true)) {
            throw IOException("Hittade ingen Steam-profil för \"$vanity\". Kontrollera länken eller SteamID64.")
        }
        if (isPrivateSteamProfileHtml(htmlOutcome.body)) {
            throw SteamProfilePrivateException(PRIVATE_PROFILE_ERROR_MSG)
        }
        parseSteamId64FromProfileResponse(htmlOutcome.body)?.let { return@withContext it }

        throw IOException("Kunde inte läsa SteamID64 för \"$vanity\". Kontrollera att profilen är offentlig eller ange SteamID64 direkt.")
    }

    suspend fun fetchSubscribedWorkshopMods(
        rawProfileInput: String,
        sessionCookie: String? = null
    ): SteamSubscribedSyncResult = withContext(Dispatchers.IO) {
        val sessionSteamId = sessionCookie?.let { extractSteamId64FromCookie(it) }
        val useAuthenticatedSubscriptions = !sessionCookie.isNullOrBlank() && sessionSteamId != null

        val steamId64 = if (useAuthenticatedSubscriptions && rawProfileInput.isBlank()) {
            sessionSteamId!!
        } else if (rawProfileInput.isNotBlank()) {
            val resolved = runCatching { resolveSteamId64(rawProfileInput) }.getOrNull()
            resolved ?: sessionSteamId ?: resolveSteamId64(rawProfileInput)
        } else {
            resolveSteamId64(rawProfileInput)
        }

        val effectiveCookie = if (useAuthenticatedSubscriptions) {
            buildAuthenticatedCookieHeader(sessionCookie!!)
        } else {
            STEAM_AGE_BYPASS_COOKIE
        }
        val maxPages = if (useAuthenticatedSubscriptions) 10 else 3

        val collectedRaw = linkedMapOf<String, WorkshopModItem>()
        var firstUrl = if (useAuthenticatedSubscriptions) {
            buildSubscriptionsWorkshopUrl(steamId64, 1)
        } else {
            buildFavoriteWorkshopUrl(steamId64, 1)
        }
        var firstStatusCode = 200

        for (page in 1..maxPages) {
            val url = if (useAuthenticatedSubscriptions) {
                buildSubscriptionsWorkshopUrl(steamId64, page)
            } else {
                buildFavoriteWorkshopUrl(steamId64, page)
            }
            val outcome = executeSteamGet(url, effectiveCookie)
            if (page == 1) {
                firstUrl = outcome.requestedUrl
                firstStatusCode = outcome.statusCode
            }

            if (useAuthenticatedSubscriptions) {
                val finalPath = runCatching { java.net.URI(outcome.finalUrl).path }.getOrNull().orEmpty()
                if (finalPath.startsWith("/login", ignoreCase = true)) {
                    throw SteamSessionExpiredException(
                        "Din Steam-inloggning har gått ut. Logga in med Steam igen för att synka prenumerationer."
                    )
                }
            }

            if (isPrivateSteamProfileHtml(outcome.body)) {
                throw SteamProfilePrivateException(PRIVATE_PROFILE_ERROR_MSG)
            }
            val parsed = parseWorkshopResponseBody(outcome.body, appId = 0)
            if (parsed.isEmpty()) break

            var newItemsOnPage = 0
            parsed.forEach { item ->
                if (!collectedRaw.containsKey(item.publishedFileId)) {
                    collectedRaw[item.publishedFileId] = item
                    newItemsOnPage++
                }
            }
            if (newItemsOnPage == 0) break

            val hasNextPage = outcome.body.contains("p=${page + 1}", ignoreCase = true) ||
                outcome.body.contains("ShowPage( ${page + 1}", ignoreCase = true) ||
                parsed.size >= 24
            if (!hasNextPage) break
        }

        if (collectedRaw.isEmpty()) {
            return@withContext SteamSubscribedSyncResult(
                steamId64 = steamId64,
                mods = emptyList(),
                queriedUrl = firstUrl,
                httpStatusCode = firstStatusCode,
                isAuthenticatedSubscriptions = useAuthenticatedSubscriptions
            )
        }

        val allIds = collectedRaw.keys.toList()
        val detailsMap = mutableMapOf<String, WorkshopModItem>()
        allIds.chunked(50).forEach { chunk ->
            detailsMap.putAll(fetchPublishedFileDetailsSafe(chunk))
        }

        val enrichedMods = collectedRaw.values.map { raw ->
            val detail = detailsMap[raw.publishedFileId]
            if (detail != null) {
                raw.copy(
                    appId = detail.appId.takeIf { it > 0 } ?: raw.appId,
                    title = detail.title.ifBlank { raw.title },
                    previewUrl = detail.previewUrl.ifBlank { raw.previewUrl },
                    subscriptions = detail.subscriptions ?: raw.subscriptions,
                    updatedEpochSec = detail.updatedEpochSec ?: raw.updatedEpochSec
                )
            } else {
                raw
            }
        }

        val distinctAppIds = enrichedMods.map { it.appId }.filter { it > 0 }.distinct()
        val appNames = fetchAppNamesMap(distinctAppIds)

        val firestoreMods = enrichedMods.map { item ->
            val gameName = appNames[item.appId].orEmpty()
                .ifBlank { if (item.appId > 0) "Steam App #${item.appId}" else "Steam Workshop" }
            FollowedWorkshopMod(
                id = item.publishedFileId,
                publishedFileId = item.publishedFileId,
                appId = item.appId,
                gameName = gameName,
                title = item.title,
                author = item.author,
                previewUrl = item.previewUrl,
                itemUrl = item.itemUrl,
                source = WorkshopFollowSource.STEAM_SYNC,
                updatedEpochSec = item.updatedEpochSec ?: 0L,
                subscriptions = item.subscriptions ?: 0L
            )
        }.sortedByDescending { it.updatedEpochSec }

        SteamSubscribedSyncResult(
            steamId64 = steamId64,
            mods = firestoreMods,
            queriedUrl = firstUrl,
            httpStatusCode = firstStatusCode,
            isAuthenticatedSubscriptions = useAuthenticatedSubscriptions
        )
    }

    suspend fun fetchLatestModsForGames(
        games: List<FollowedWorkshopGame>
    ): List<FollowedFeedModItem> = coroutineScope {
        if (games.isEmpty()) return@coroutineScope emptyList()
        val targetGames = games.take(6)
        val deferred = targetGames.map { game ->
            async(Dispatchers.IO) {
                try {
                    val result = fetchWorkshopMods(
                        appId = game.appId,
                        sortOrder = WorkshopSortOrder.MOST_RECENT,
                        searchQuery = "",
                        page = 1
                    )
                    result.mods.take(5).map { mod ->
                        FollowedFeedModItem(
                            mod = mod,
                            gameName = game.gameName.ifBlank { "App #${game.appId}" }
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        deferred.awaitAll()
            .flatten()
            .distinctBy { it.mod.publishedFileId }
            .sortedByDescending { it.mod.updatedEpochSec ?: 0L }
    }

    private suspend fun fetchAppNamesMap(appIds: List<Int>): Map<Int, String> = coroutineScope {
        if (appIds.isEmpty()) return@coroutineScope emptyMap()
        val result = mutableMapOf<Int, String>()
        val missing = mutableListOf<Int>()
        appIds.forEach { id ->
            val cached = appNameCache[id]
            if (cached != null) {
                result[id] = cached
            } else {
                missing.add(id)
            }
        }

        missing.take(20).map { appId ->
            async(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder()
                        .url("https://store.steampowered.com/api/appdetails?appids=$appId&filters=basic")
                        .header("User-Agent", DESKTOP_USER_AGENT)
                        .header("Accept", "application/json")
                        .get()
                        .build()
                    val body = httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                    }
                    if (body.isNotBlank()) {
                        val root = JSONObject(body)
                        val appObj = root.optJSONObject(appId.toString())
                        if (appObj?.optBoolean("success", false) == true) {
                            val name = appObj.optJSONObject("data")?.optString("name", "").orEmpty().trim()
                            if (name.isNotBlank()) {
                                appNameCache[appId] = name
                                return@runCatching appId to name
                            }
                        }
                    }
                    null
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().forEach { (id, name) ->
            result[id] = name
        }

        result
    }

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val STEAM_AGE_BYPASS_COOKIE =
            "birthtime=0; lastagecheckage=1-0-1990; wants_mature_content=1"
        private const val PRIVATE_PROFILE_ERROR_MSG =
            "Din Steam-profil eller dina Speldetaljer (Game Details) är privata. Ställ in både Profil och Speldetaljer till Offentlig på Steam för att kunna synka dina publika favoriter."

        val KNOWN_WORKSHOP_APP_IDS: Set<Int> = setOf(
            4000,    // Garry's Mod
            730,     // Counter-Strike 2
            570,     // Dota 2
            440,     // Team Fortress 2
            294100,  // RimWorld
            431960,  // Wallpaper Engine
            550,     // Left 4 Dead 2
            255710,  // Cities: Skylines
            394360,  // Hearts of Iron IV
            281990,  // Stellaris
            289070,  // Sid Meier's Civilization VI
            107410,  // Arma 3
            221100,  // DayZ
            108600,  // Project Zomboid
            1281930, // tModLoader
            105600,  // Terraria
            227300,  // Euro Truck Simulator 2
            270880,  // American Truck Simulator
            346110,  // ARK: Survival Evolved
            322330,  // Don't Starve Together
            311210,  // Call of Duty: Black Ops III
            261550,  // Mount & Blade II: Bannerlord
            1142710, // Total War: WARHAMMER III
            244850,  // Space Engineers
            602960,  // Barotrauma
            233860,  // Kenshi
            284160,  // BeamNG.drive
            1118200, // People Playground
            1167630, // Teardown
            262060,  // Darkest Dungeon
            268500,  // XCOM 2
            304930,  // Unturned
            218620,  // PAYDAY 2
            252490,  // Rust
            236850,  // Europa Universalis IV
            1158310, // Crusader Kings III
            529340,  // Victoria 3
            813780,  // Age of Empires II: Definitive Edition
            646570,  // Slay the Spire
            632360,  // Risk of Rain 2
            636480,  // Ravenfield
            573090,  // Stormworks: Build and Rescue
            489830,  // Skyrim Special Edition
            72850,   // Skyrim
            286160,  // Tabletop Simulator
            892970,  // Valheim
            1794680, // Vampire Survivors
            1091500, // Cyberpunk 2077
            413150,  // Stardew Valley
            1643320, // S.T.A.L.K.E.R. 2: Heart of Chornobyl
            1086940, // Baldur's Gate 3
            1144200, // Ready or Not
            362890,  // Black Mesa
            435150,  // Divinity: Original Sin 2
            976730   // Halo: The Master Chief Collection
        )

        private val STEAM_ID64_REGEX = Regex("""\b7656119\d{10}\b""")
        private val STEAM_LOGIN_SECURE_REGEX = Regex("""(?:^|;\s*)steamLoginSecure=([^;]+)""", RegexOption.IGNORE_CASE)
        private val PROFILE_ID64_URL_REGEX = Regex("""steamcommunity\.com/profiles/(\d{17})""", RegexOption.IGNORE_CASE)
        private val VANITY_URL_REGEX = Regex("""steamcommunity\.com/id/([^/?#]+)""", RegexOption.IGNORE_CASE)
        private val XML_STEAM_ID64_REGEX = Regex("""<steamID64>(\d{17})</steamID64>""", RegexOption.IGNORE_CASE)
        private val JSON_STEAM_ID64_REGEX = Regex(""""steamid"\s*:\s*"(\d{17})"""", RegexOption.IGNORE_CASE)

        private val WORKSHOP_CONTAINER_SPLIT_REGEX = Regex(
            """<(?:div|tr|li|article)\b[^>]*class\s*=\s*['"][^'"]*\b(?:workshopItem|workshopBrowseRow|workshopItemSubscription)\b[^'"]*['"][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val FILE_ID_REGEX = Regex("""data-publishedfileid\s*=\s*['"](\d+)['"]""", RegexOption.IGNORE_CASE)
        private val SHAREDFILE_DOM_ID_REGEX = Regex("""\bid\s*=\s*['"](?:sharedfile|subscription)_(\d+)['"]""", RegexOption.IGNORE_CASE)
        private val LINK_ID_REGEX = Regex("""sharedfiles/filedetails/\?(?:[^"'\s>]*&)?id=(\d+)""", RegexOption.IGNORE_CASE)
        private val BIND_HOVER_REGEX = Regex(
            """SharedFileBindMouseHover\s*\(\s*['"]sharedfile_(\d+)['"]\s*,\s*(?:true|false)\s*,\s*(\{.*?\})\s*\)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val TITLE_REGEX = Regex(
            """<(div|span|a|h[1-6])\b[^>]*class\s*=\s*['"][^'"]*\b(?:workshopItemTitle|workshopBrowseRowTitle|workshopItemSubscriptionName)\b[^'"]*['"][^>]*>(.*?)</\1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val AUTHOR_REGEX = Regex(
            """<(div|span)\b[^>]*class\s*=\s*['"][^'"]*\b(?:workshopItemAuthor(?:Name)?|workshopBrowseRowAuthor)\b[^'"]*['"][^>]*>(.*?)</\1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val ANCHOR_TEXT_REGEX = Regex("""<a\b[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val PREVIEW_IMG_TAG_REGEX = Regex(
            """<img\b[^>]*class\s*=\s*['"][^'"]*\b(?:workshopItemPreviewImage|workshopItemPreviewImageRow)\b[^'"]*['"][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val RATING_IMG_TAG_REGEX = Regex(
            """<img\b[^>]*class\s*=\s*['"][^'"]*\bfileRating\b[^'"]*['"][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val IMG_SRC_ATTR_REGEX = Regex(
            """\b(?:src|data-src|data-delayed-src)\s*=\s*['"]([^'"]+)['"]""",
            RegexOption.IGNORE_CASE
        )
        private val IMG_ALT_OR_TITLE_REGEX = Regex(
            """\b(?:alt|title)\s*=\s*['"]([^'"]+)['"]""",
            RegexOption.IGNORE_CASE
        )
        private val BG_STYLE_URL_REGEX = Regex(
            """background-image\s*:\s*url\(\s*['"]?([^'")\s]+)['"]?\s*\)""",
            RegexOption.IGNORE_CASE
        )
        private val HUB_LINK_FALLBACK_REGEX = Regex(
            """<a\b[^>]*href\s*=\s*['"][^'"]*sharedfiles/filedetails/\?(?:[^'"\s>]*&)?id=(\d+)[^'"]*['"][^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val ANY_IMG_TAG_REGEX = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val DETAIL_DESC_REGEX = Regex("""id="highlightContent"[^>]*>(.*?)</div>\s*</div>""", RegexOption.DOT_MATCHES_ALL)
        private val SUBSCRIBERS_STAT_REGEX = Regex("""<tr>\s*<td>([\d, ]+)</td>\s*<td>\s*Current Subscribers""", RegexOption.IGNORE_CASE)

        fun buildSubscriptionsWorkshopUrl(steamId64: String, page: Int = 1): String {
            return "https://steamcommunity.com/profiles/$steamId64/myworkshopfiles/?browsefilter=mysubscriptions&p=$page&numperpage=30"
        }

        fun buildFavoriteWorkshopUrl(steamId64: String, page: Int = 1): String {
            return "https://steamcommunity.com/profiles/$steamId64/myworkshopfiles/?browsefilter=myfavorites&p=$page&numperpage=30"
        }

        fun buildSubscribedWorkshopUrl(steamId64: String, page: Int = 1): String =
            buildFavoriteWorkshopUrl(steamId64, page)

        fun extractSteamId64FromCookie(cookieString: String): String? {
            if (cookieString.isBlank()) return null
            val rawValue = STEAM_LOGIN_SECURE_REGEX.find(cookieString)?.groupValues?.getOrNull(1)
                ?.trim()
                ?.removeSurrounding("\"")
                ?: return null
            val prefix = rawValue
                .replace("%7C", "|", ignoreCase = true)
                .substringBefore("||")
                .trim()
            return if (STEAM_ID64_REGEX.matches(prefix)) prefix else null
        }

        fun buildAuthenticatedCookieHeader(sessionCookie: String): String {
            val trimmed = sessionCookie.trim().trimEnd(';')
            if (trimmed.isBlank()) return STEAM_AGE_BYPASS_COOKIE
            val extras = buildList {
                if (!trimmed.contains("birthtime=", ignoreCase = true)) add("birthtime=0")
                if (!trimmed.contains("lastagecheckage=", ignoreCase = true)) add("lastagecheckage=1-0-1990")
                if (!trimmed.contains("wants_mature_content=", ignoreCase = true)) add("wants_mature_content=1")
            }
            return if (extras.isEmpty()) trimmed else "$trimmed; ${extras.joinToString("; ")}"
        }

        fun extractSteamId64FromInput(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return null
            STEAM_ID64_REGEX.find(trimmed)?.value?.let { return it }
            PROFILE_ID64_URL_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }

        fun extractVanityFromInput(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return null
            VANITY_URL_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            if (!trimmed.contains('/') && !trimmed.contains('.') && !trimmed.all { it.isDigit() }) {
                return trimmed
            }
            return null
        }

        fun parseSteamId64FromProfileResponse(body: String): String? {
            if (body.isBlank()) return null
            XML_STEAM_ID64_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { return it }
            JSON_STEAM_ID64_REGEX.find(body)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }

        fun isPrivateSteamProfileHtml(body: String): Boolean {
            if (body.isBlank()) return false
            return body.contains("This profile is private", ignoreCase = true) ||
                body.contains("profile_private_info", ignoreCase = true)
        }

        fun buildBrowseCandidateUrls(
            appId: Int,
            sortOrder: WorkshopSortOrder,
            searchQuery: String,
            page: Int
        ): List<String> {
            val encodedQuery = URLEncoder.encode(searchQuery.trim(), "UTF-8")
            fun makeUrl(section: String?, ajax: Boolean = false): String {
                return buildString {
                    append("https://steamcommunity.com/workshop/browse/?appid=").append(appId)
                    append("&browsesort=").append(sortOrder.browseSort)
                    if (!section.isNullOrBlank()) {
                        append("&section=").append(section)
                    }
                    append("&actualsort=").append(sortOrder.browseSort)
                    append("&days=").append(sortOrder.daysParam)
                    append("&p=").append(page)
                    if (ajax) {
                        append("&ajax=1")
                    }
                    if (encodedQuery.isNotEmpty()) {
                        append("&searchtext=").append(encodedQuery)
                    }
                }
            }

            return buildList {
                // 1. Standard mod/item browse section (canonical default)
                add(makeUrl(section = "readytouseitems"))
                // 2. Browse without forced section (covers mixed/curated workshops)
                add(makeUrl(section = null))
                // 3. AJAX/JSON browse fallback
                add(makeUrl(section = "readytouseitems", ajax = true))
                // 4. MTX/skins/curated items section fallback (e.g., CS2 / Dota 2 / TF2 / Rust)
                add(makeUrl(section = "mtxitems"))
                // 5. Game workshop hub fallback for page 1 without search query
                if (page == 1 && encodedQuery.isEmpty()) {
                    add("https://steamcommunity.com/app/$appId/workshop/")
                }
            }
        }

        fun parseSteamSpyTop100(
            jsonString: String,
            mode: WorkshopLeaderboardMode
        ): List<WorkshopGameSummary> {
            if (jsonString.isBlank()) return emptyList()
            val root = JSONObject(jsonString)
            val parsed = buildList {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key) ?: continue
                    val appId = obj.optInt("appid", key.toIntOrNull() ?: 0)
                    if (appId <= 0) continue
                    val name = obj.optString("name", "").ifBlank { "App #$appId" }
                    add(
                        WorkshopGameSummary(
                            rank = 0,
                            appId = appId,
                            name = name,
                            developer = obj.optString("developer", "Okänd utvecklare"),
                            publisher = obj.optString("publisher", ""),
                            ccu = obj.optInt("ccu", 0),
                            ownersRange = obj.optString("owners", "—"),
                            positiveReviews = obj.optInt("positive", 0),
                            negativeReviews = obj.optInt("negative", 0),
                            averagePlaytimeMinutes = obj.optInt("average_forever", 0),
                            priceCents = obj.optString("price", "0").toIntOrNull() ?: obj.optInt("price", 0),
                            discountPercent = obj.optString("discount", "0").toIntOrNull() ?: obj.optInt("discount", 0),
                            isKnownWorkshopPowerhouse = KNOWN_WORKSHOP_APP_IDS.contains(appId)
                        )
                    )
                }
            }

            val ordered = when (mode) {
                WorkshopLeaderboardMode.TWO_WEEKS -> parsed.sortedByDescending { it.ccu }
                WorkshopLeaderboardMode.ALL_TIME -> parsed.sortedByDescending { it.positiveReviews + it.negativeReviews }
                WorkshopLeaderboardMode.MOST_OWNED -> parsed
            }

            return ordered.take(100).mapIndexed { idx, item ->
                item.copy(rank = idx + 1)
            }
        }

        fun buildStoreSearchUrl(query: String): String {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            return "https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US"
        }

        fun parseSteamStoreSearchJson(jsonString: String): List<WorkshopGameSummary> {
            if (jsonString.isBlank()) return emptyList()
            val root = JSONObject(jsonString)
            val items = root.optJSONArray("items") ?: return emptyList()
            return buildList {
                for (i in 0 until items.length()) {
                    val obj = items.optJSONObject(i) ?: continue
                    val appId = obj.optInt("id", 0)
                    if (appId <= 0) continue
                    val name = obj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val tinyImage = obj.optString("tiny_image", "").trim()
                    val priceObj = obj.optJSONObject("price")
                    val priceCents = priceObj?.optInt("final", 0) ?: 0
                    val discountPercent = priceObj?.optInt("discount_percent", 0) ?: 0

                    add(
                        WorkshopGameSummary(
                            rank = 0,
                            appId = appId,
                            name = name,
                            developer = "Steam AppID $appId",
                            publisher = "",
                            ccu = 0,
                            ownersRange = "—",
                            positiveReviews = 0,
                            negativeReviews = 0,
                            averagePlaytimeMinutes = 0,
                            priceCents = priceCents,
                            discountPercent = discountPercent,
                            isKnownWorkshopPowerhouse = KNOWN_WORKSHOP_APP_IDS.contains(appId),
                            customImageUrl = tinyImage
                        )
                    )
                }
            }.distinctBy { it.appId }
        }

        fun parseWorkshopResponseBody(body: String, appId: Int): List<WorkshopModItem> {
            val trimmed = body.trimStart()
            if (trimmed.isBlank()) return emptyList()
            if (trimmed.startsWith("{")) {
                val fromJson = runCatching {
                    val json = JSONObject(trimmed)
                    val resultsHtml = json.optString("results_html", "")
                        .ifBlank { json.optString("html", "") }
                    if (resultsHtml.isNotBlank()) {
                        val parsedHtml = parseWorkshopBrowseHtml(resultsHtml, appId)
                        if (parsedHtml.isNotEmpty()) return@runCatching parsedHtml
                    }

                    val array = json.optJSONArray("publishedfiledetails")
                        ?: json.optJSONArray("items")
                        ?: json.optJSONObject("response")?.optJSONArray("publishedfiledetails")
                        ?: JSONArray()

                    buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val id = obj.optString("publishedfileid", "")
                                .ifBlank { obj.optString("id", "") }
                            if (id.isBlank()) continue
                            add(
                                WorkshopModItem(
                                    publishedFileId = id,
                                    appId = obj.optInt("consumer_app_id", appId).takeIf { it > 0 } ?: appId,
                                    title = cleanHtmlText(obj.optString("title", "")).ifBlank { "Workshop Item #$id" },
                                    author = obj.optString("author", "").ifBlank { "Steam-skapare" },
                                    previewUrl = obj.optString("preview_url", ""),
                                    itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=$id",
                                    subscriptions = obj.optLong("subscriptions", -1L).takeIf { it >= 0L },
                                    favorites = obj.optLong("favorited", -1L).takeIf { it >= 0L },
                                    views = obj.optLong("views", -1L).takeIf { it >= 0L },
                                    description = cleanBbCodeAndHtml(obj.optString("description", ""))
                                )
                            )
                        }
                    }
                }.getOrDefault(emptyList())

                if (fromJson.isNotEmpty()) return fromJson
            }
            return parseWorkshopBrowseHtml(body, appId)
        }

        fun parseWorkshopBrowseHtml(html: String, appId: Int): List<WorkshopModItem> {
            if (html.isBlank()) return emptyList()

            val byId = linkedMapOf<String, WorkshopModItem>()

            fun mergeItem(
                id: String,
                title: String = "",
                author: String = "",
                previewUrl: String = "",
                ratingUrl: String = "",
                description: String = ""
            ) {
                if (id.isBlank()) return
                val cleanTitle = title.trim()
                val cleanAuthor = author.trim()
                val cleanPreview = previewUrl.trim()
                val cleanRating = ratingUrl.trim()
                val cleanDesc = description.trim()

                val existing = byId[id]
                if (existing == null) {
                    byId[id] = WorkshopModItem(
                        publishedFileId = id,
                        appId = appId,
                        title = cleanTitle.ifBlank { "Workshop Item #$id" },
                        author = cleanAuthor.ifBlank { "Steam-skapare" },
                        previewUrl = cleanPreview,
                        itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=$id",
                        ratingStarsUrl = cleanRating,
                        description = cleanDesc
                    )
                } else {
                    val resolvedTitle = when {
                        cleanTitle.isNotBlank() && existing.title.startsWith("Workshop Item #") -> cleanTitle
                        existing.title.startsWith("Workshop Item #") -> existing.title
                        existing.title.isNotBlank() -> existing.title
                        else -> cleanTitle.ifBlank { "Workshop Item #$id" }
                    }
                    val resolvedAuthor = when {
                        cleanAuthor.isNotBlank() && existing.author == "Steam-skapare" -> cleanAuthor
                        else -> existing.author.ifBlank { cleanAuthor.ifBlank { "Steam-skapare" } }
                    }
                    byId[id] = existing.copy(
                        title = resolvedTitle,
                        author = resolvedAuthor,
                        previewUrl = existing.previewUrl.ifBlank { cleanPreview },
                        ratingStarsUrl = existing.ratingStarsUrl.ifBlank { cleanRating },
                        description = existing.description.ifBlank { cleanDesc }
                    )
                }
            }

            // 1. Container-based parser: supports BOTH grid cards (.workshopItem) AND list rows (.workshopBrowseRow)
            // Slice using match indices so opening tag attributes (e.g. id="sharedfile_...", data-publishedfileid) are preserved.
            val containerMatches = WORKSHOP_CONTAINER_SPLIT_REGEX.findAll(html).toList()
            if (containerMatches.isNotEmpty()) {
                containerMatches.forEachIndexed { index, match ->
                    val start = match.range.first
                    val end = containerMatches.getOrNull(index + 1)?.range?.first
                        ?: minOf(html.length, start + 4500)
                    val window = html.substring(start, minOf(end, start + 4500))
                    val id = FILE_ID_REGEX.find(window)?.groupValues?.getOrNull(1)
                        ?: SHAREDFILE_DOM_ID_REGEX.find(window)?.groupValues?.getOrNull(1)
                        ?: LINK_ID_REGEX.find(window)?.groupValues?.getOrNull(1)
                        ?: return@forEachIndexed

                    val rawTitle = TITLE_REGEX.find(window)?.groupValues?.getOrNull(2)
                        ?: HUB_LINK_FALLBACK_REGEX.findAll(window)
                            .map { cleanHtmlText(it.groupValues[2]) }
                            .firstOrNull { it.isNotBlank() }
                        ?: IMG_ALT_OR_TITLE_REGEX.find(
                            PREVIEW_IMG_TAG_REGEX.find(window)?.value.orEmpty()
                        )?.groupValues?.getOrNull(1)
                        ?: ""
                    val title = cleanHtmlText(rawTitle)

                    val rawAuthorBlock = AUTHOR_REGEX.find(window)?.groupValues?.getOrNull(2).orEmpty()
                    val authorInner = ANCHOR_TEXT_REGEX.find(rawAuthorBlock)?.groupValues?.getOrNull(1)
                        ?: rawAuthorBlock.replace(Regex("""^\s*(?:by|av)\s+""", RegexOption.IGNORE_CASE), "")
                    val author = cleanHtmlText(authorInner)

                    val previewUrl = (
                        PREVIEW_IMG_TAG_REGEX.find(window)?.value
                            ?.let { imgTag -> IMG_SRC_ATTR_REGEX.find(imgTag)?.groupValues?.getOrNull(1) }
                            ?: ANY_IMG_TAG_REGEX.findAll(window)
                                .mapNotNull { IMG_SRC_ATTR_REGEX.find(it.value)?.groupValues?.getOrNull(1) }
                                .firstOrNull { src ->
                                    !src.contains("fileRating", ignoreCase = true) &&
                                        !src.contains("star", ignoreCase = true) &&
                                        !src.contains("avatar", ignoreCase = true)
                                }
                            ?: BG_STYLE_URL_REGEX.find(window)?.groupValues?.getOrNull(1)
                        )
                        ?.replace("&amp;", "&")
                        .orEmpty()

                    val ratingUrl = RATING_IMG_TAG_REGEX.find(window)?.value
                        ?.let { imgTag -> IMG_SRC_ATTR_REGEX.find(imgTag)?.groupValues?.getOrNull(1) }
                        ?.replace("&amp;", "&")
                        .orEmpty()

                    mergeItem(
                        id = id,
                        title = title,
                        author = author,
                        previewUrl = previewUrl,
                        ratingUrl = ratingUrl
                    )
                }
            }

            // 2. SharedFileBindMouseHover JS metadata blocks (often emitted alongside dynamic grids or hub showcases)
            BIND_HOVER_REGEX.findAll(html).forEach { match ->
                val id = match.groupValues[1]
                val jsonRaw = match.groupValues[2]
                runCatching {
                    val obj = JSONObject(jsonRaw)
                    val hoverTitle = cleanHtmlText(obj.optString("title", ""))
                    val hoverDesc = cleanBbCodeAndHtml(obj.optString("description", ""))
                    val hoverAuthor = cleanHtmlText(obj.optString("author", ""))
                    mergeItem(
                        id = id,
                        title = hoverTitle,
                        author = hoverAuthor,
                        description = hoverDesc
                    )
                }.onFailure {
                    mergeItem(id = id)
                }
            }

            // 3. Direct sharedfiles/filedetails/?id=... anchor fallback (handles relative/absolute URLs, rows, and hub showcases)
            HUB_LINK_FALLBACK_REGEX.findAll(html).forEach { match ->
                val id = match.groupValues[1]
                val inner = match.groupValues[2]
                val titleFromDiv = TITLE_REGEX.find(inner)?.groupValues?.getOrNull(2)
                    ?.let { cleanHtmlText(it) }
                    .orEmpty()
                val textTitle = cleanHtmlText(inner)
                val imgTag = ANY_IMG_TAG_REGEX.find(inner)?.value.orEmpty()
                val imgUrl = IMG_SRC_ATTR_REGEX.find(imgTag)?.groupValues?.getOrNull(1)
                    ?.replace("&amp;", "&")
                    .orEmpty()
                val imgAltTitle = IMG_ALT_OR_TITLE_REGEX.find(imgTag)?.groupValues?.getOrNull(1)
                    ?.let { cleanHtmlText(it) }
                    .orEmpty()

                val resolvedTitle = titleFromDiv.ifBlank { textTitle }.ifBlank { imgAltTitle }
                if (resolvedTitle.isNotBlank() || imgUrl.isNotBlank() || byId.containsKey(id)) {
                    mergeItem(
                        id = id,
                        title = resolvedTitle,
                        previewUrl = imgUrl
                    )
                }
            }

            // 4. Last-resort ID scan if page only exposed filedetails/?id=... or data-publishedfileid without wrapped title/img
            if (byId.isEmpty()) {
                FILE_ID_REGEX.findAll(html).forEach { mergeItem(id = it.groupValues[1]) }
                LINK_ID_REGEX.findAll(html).forEach { mergeItem(id = it.groupValues[1]) }
            }

            return byId.values.toList()
        }

        private fun cleanHtmlText(raw: String): String {
            return Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace('\u00A0', ' ')
                .trim()
        }

        private fun cleanBbCodeAndHtml(raw: String): String {
            if (raw.isBlank()) return ""
            val strippedBb = raw
                .replace(Regex("""\[/?[a-zA-Z0-9*=._:/\- ]+\]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            return cleanHtmlText(strippedBb)
        }
    }
}
