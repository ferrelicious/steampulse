package com.example.data

import android.text.Html
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopLeaderboardMode
import com.example.model.WorkshopModItem
import com.example.model.WorkshopSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SteamWorkshopRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

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

    suspend fun fetchWorkshopMods(
        appId: Int,
        sortOrder: WorkshopSortOrder,
        searchQuery: String,
        page: Int
    ): Pair<List<WorkshopModItem>, Boolean> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(searchQuery.trim(), "UTF-8")
        val urlBuilder = StringBuilder(
            "https://steamcommunity.com/workshop/browse/?appid=$appId" +
                "&browsesort=${sortOrder.browseSort}" +
                "&section=readytouseitems" +
                "&actualsort=${sortOrder.browseSort}" +
                "&days=${sortOrder.daysParam}" +
                "&p=$page"
        )
        if (encodedQuery.isNotEmpty()) {
            urlBuilder.append("&searchtext=").append(encodedQuery)
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9,sv;q=0.8")
            .get()
            .build()

        val html = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Steam Workshop svarade med HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw IOException("Kunde inte ladda Steam Workshop: ${e.localizedMessage ?: "nätverksfel"}", e)
        }

        val rawItems = parseWorkshopBrowseHtml(html, appId)
        val hasNextPage = html.contains("p=${page + 1}", ignoreCase = true) ||
            rawItems.size >= 24

        if (rawItems.isEmpty()) {
            return@withContext emptyList<WorkshopModItem>() to false
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

        enriched to hasNextPage
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
                .header("Accept-Language", "en-US,en;q=0.9")
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

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

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
            1222140  // Detroit / etc. fallback
        )

        private val WORKSHOP_ITEM_SPLIT_REGEX = Regex("""<div class="workshopItem"[^>]*>""")
        private val FILE_ID_REGEX = Regex("""data-publishedfileid="(\d+)"""")
        private val LINK_ID_REGEX = Regex("""sharedfiles/filedetails/\?id=(\d+)""")
        private val TITLE_REGEX = Regex("""<div class="workshopItemTitle[^"]*">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        private val AUTHOR_REGEX = Regex("""<div class="workshopItemAuthorName[^"]*">.*?<a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        private val PREVIEW_IMG_REGEX = Regex("""class="workshopItemPreviewImage[^"]*"[^>]*src="([^"]+)"""")
        private val RATING_IMG_REGEX = Regex("""class="fileRating"[^>]*src="([^"]+)"""")
        private val DETAIL_DESC_REGEX = Regex("""id="highlightContent"[^>]*>(.*?)</div>\s*</div>""", RegexOption.DOT_MATCHES_ALL)
        private val SUBSCRIBERS_STAT_REGEX = Regex("""<tr>\s*<td>([\d, ]+)</td>\s*<td>\s*Current Subscribers""", RegexOption.IGNORE_CASE)

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

        fun parseWorkshopBrowseHtml(html: String, appId: Int): List<WorkshopModItem> {
            if (html.isBlank()) return emptyList()
            val chunks = WORKSHOP_ITEM_SPLIT_REGEX.split(html)
            if (chunks.size <= 1) return emptyList()

            return chunks.drop(1).mapNotNull { block ->
                // Limit scanning window to the card markup
                val window = block.take(2500)
                val id = FILE_ID_REGEX.find(window)?.groupValues?.getOrNull(1)
                    ?: LINK_ID_REGEX.find(window)?.groupValues?.getOrNull(1)
                    ?: return@mapNotNull null

                val rawTitle = TITLE_REGEX.find(window)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
                val title = cleanHtmlText(rawTitle)
                if (title.isBlank()) return@mapNotNull null

                val author = AUTHOR_REGEX.find(window)?.groupValues?.getOrNull(1)
                    ?.let { cleanHtmlText(it) }
                    ?.ifBlank { "Steam-skapare" }
                    ?: "Steam-skapare"

                val previewUrl = PREVIEW_IMG_REGEX.find(window)?.groupValues?.getOrNull(1)
                    ?.replace("&amp;", "&")
                    .orEmpty()

                val ratingUrl = RATING_IMG_REGEX.find(window)?.groupValues?.getOrNull(1)
                    ?.replace("&amp;", "&")
                    .orEmpty()

                WorkshopModItem(
                    publishedFileId = id,
                    appId = appId,
                    title = title,
                    author = author,
                    previewUrl = previewUrl,
                    itemUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=$id",
                    ratingStarsUrl = ratingUrl
                )
            }
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
