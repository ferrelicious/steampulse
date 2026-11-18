package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SteamPulseRepositoryTest {

    @Test
    fun buildUrls_encodeSwedishLocaleAndRegion() {
        assertEquals(
            "https://store.steampowered.com/api/storesearch/?term=elden+ring&l=swedish&cc=SE",
            SteamPulseRepository.buildStoreSearchUrl("elden ring")
        )
        assertEquals(
            "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=730",
            SteamPulseRepository.buildCurrentPlayersUrl(730)
        )
        assertEquals(
            "https://store.steampowered.com/api/appdetails?appids=730&cc=se&l=swedish",
            SteamPulseRepository.buildAppDetailsUrl(730)
        )
    }

    @Test
    fun parseStoreSearchJson_parsesPaidDiscountedAndFreeGames() {
        val json = """
            {
              "total": 2,
              "items": [
                {
                  "id": 1245620,
                  "name": "ELDEN RING",
                  "price": {
                    "currency": "EUR",
                    "initial": 5999,
                    "final": 3599,
                    "discount_percent": 40
                  }
                },
                {
                  "id": 730,
                  "name": "Counter-Strike 2"
                }
              ]
            }
        """.trimIndent()

        val games = SteamPulseRepository.parseStoreSearchJson(json)
        assertEquals(2, games.size)

        val elden = games[0]
        assertEquals(1245620, elden.appId)
        assertEquals("ELDEN RING", elden.title)
        assertFalse(elden.isFreeToPlay)
        assertEquals(59.99, elden.steamPriceEur, 0.01)
        assertEquals(35.99, elden.finalPriceEur, 0.01)
        assertEquals(40, elden.steamDiscountPercent)
        assertEquals(40, SteamGamesCatalog.getDiscountPercent(elden))

        val cs2 = games[1]
        assertEquals(730, cs2.appId)
        assertTrue(cs2.isFreeToPlay)
        assertEquals(0.0, cs2.finalPriceEur, 0.001)
    }

    @Test
    fun parseCurrentPlayersJson_extractsPlayerCount() {
        val validJson = """{"response":{"player_count":812345,"result":1}}"""
        val invalidJson = """{"response":{"result":42}}"""

        assertEquals(812345, SteamPulseRepository.parseCurrentPlayersJson(validJson))
        assertNull(SteamPulseRepository.parseCurrentPlayersJson(invalidJson))
    }

    @Test
    fun parseAppDetailsJson_extractsDeveloperGenresAndSekPriceConversion() {
        val json = """
            {
              "294100": {
                "success": true,
                "data": {
                  "name": "RimWorld",
                  "is_free": false,
                  "short_description": "A sci-fi colony sim driven by an intelligent AI storyteller.",
                  "header_image": "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/294100/header.jpg",
                  "developers": ["Ludeon Studios"],
                  "genres": [
                    {"id": "23", "description": "Indie"},
                    {"id": "28", "description": "Simulering"}
                  ],
                  "release_date": {"coming_soon": false, "date": "17 okt, 2018"},
                  "price_overview": {
                    "currency": "SEK",
                    "initial": 34500,
                    "final": 27600,
                    "discount_percent": 20
                  }
                }
              }
            }
        """.trimIndent()

        val details = SteamPulseRepository.parseAppDetailsJson(json, 294100)
        assertNotNull(details)
        assertEquals("RimWorld", details?.title)
        assertEquals("Ludeon Studios", details?.developer)
        assertEquals(listOf("Indie", "Simulering"), details?.genres)
        assertEquals(20, details?.discountPercent)
        assertEquals(345.0 / SteamGamesCatalog.EXCHANGE_RATE_SEK, details?.initialPriceEur ?: 0.0, 0.01)
        assertEquals(276.0 / SteamGamesCatalog.EXCHANGE_RATE_SEK, details?.finalPriceEur ?: 0.0, 0.01)

        val base = SteamPulseRepository.createSkeletonGame(appId = 294100, rank = 5)
        val merged = SteamPulseRepository.mergeGameMetadata(base, details, 31400)
        assertEquals("RimWorld", merged.title)
        assertEquals(31400, merged.playersNow)
        assertEquals(20, SteamGamesCatalog.getDiscountPercent(merged))
        assertTrue(merged.isDetailsLoaded)
        assertTrue(merged.isPlayersLoaded)
    }

    @Test
    fun parseMostPlayedAndFeaturedCategoriesJson_parsesExpectedEntries() {
        val chartsJson = """
            {
              "response": {
                "ranks": [
                  {"rank": 1, "appid": 730, "concurrent_in_game": 950000, "peak_in_game": 1200000},
                  {"rank": 2, "appid": 570, "concurrent_in_game": 450000, "peak_in_game": 600000}
                ]
              }
            }
        """.trimIndent()

        val charts = SteamPulseRepository.parseMostPlayedChartsJson(chartsJson)
        assertEquals(2, charts.size)
        assertEquals(730, charts[0].appId)
        assertEquals(1, charts[0].rank)
        assertEquals(950000, charts[0].playersNow)

        val featuredJson = """
            {
              "top_sellers": {
                "items": [
                  {
                    "id": 1091500,
                    "name": "Cyberpunk 2077",
                    "currency": "EUR",
                    "original_price": 5999,
                    "final_price": 2999,
                    "discount_percent": 50,
                    "header_image": "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1091500/header.jpg"
                  }
                ]
              }
            }
        """.trimIndent()

        val topSellers = SteamPulseRepository.parseFeaturedCategoriesJson(featuredJson, "top_sellers")
        assertEquals(1, topSellers.size)
        assertEquals("Cyberpunk 2077", topSellers[0].title)
        assertEquals(50, topSellers[0].steamDiscountPercent)
        assertEquals(29.99, topSellers[0].finalPriceEur, 0.01)
    }
}
