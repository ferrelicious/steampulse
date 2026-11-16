package com.example.data

import com.example.model.WorkshopLeaderboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SteamWorkshopRepositoryTest {

    @Test
    fun parseSteamSpyTop100_ordersByCcuAndMarksWorkshopGames() {
        val sampleJson = """
            {
              "4000": {
                "appid": 4000,
                "name": "Garry's Mod",
                "developer": "Facepunch Studios",
                "publisher": "Valve",
                "positive": 900000,
                "negative": 30000,
                "owners": "20,000,000 .. 50,000,000",
                "average_forever": 12000,
                "price": "999",
                "discount": "0",
                "ccu": 35000
              },
              "730": {
                "appid": 730,
                "name": "Counter-Strike 2",
                "developer": "Valve",
                "publisher": "Valve",
                "positive": 7000000,
                "negative": 1000000,
                "owners": "100,000,000 .. 200,000,000",
                "average_forever": 25000,
                "price": "0",
                "discount": "0",
                "ccu": 1200000
              }
            }
        """.trimIndent()

        val result = SteamWorkshopRepository.parseSteamSpyTop100(
            sampleJson,
            WorkshopLeaderboardMode.TWO_WEEKS
        )

        assertEquals(2, result.size)
        assertEquals(730, result[0].appId)
        assertEquals(1, result[0].rank)
        assertEquals(4000, result[1].appId)
        assertEquals(2, result[1].rank)
        assertTrue(result[1].isKnownWorkshopPowerhouse)
    }

    @Test
    fun parseWorkshopBrowseHtml_extractsWorkshopItems() {
        val sampleHtml = """
            <div class="workshopBrowseItems">
              <div class="workshopItem">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=2009463077" data-publishedfileid="2009463077">
                  <div class="workshopItemPreviewHolder">
                    <img class="workshopItemPreviewImage" src="https://steamuserimages-a.akamaihd.net/ugc/123/preview.jpg"/>
                  </div>
                </a>
                <img class="fileRating" src="https://community.fastly.steamstatic.com/public/images/sharedfiles/5-star_large.png"/>
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=2009463077">
                  <div class="workshopItemTitle ellipsis">Harmony &amp; Mod Framework</div>
                </a>
                <div class="workshopItemAuthorName ellipsis">by <a href="https://steamcommunity.com/id/pardeike">Brrainz</a></div>
              </div>
            </div>
        """.trimIndent()

        val items = SteamWorkshopRepository.parseWorkshopBrowseHtml(sampleHtml, 294100)
        assertEquals(1, items.size)
        assertEquals("2009463077", items[0].publishedFileId)
        assertEquals("Harmony & Mod Framework", items[0].title)
        assertEquals("Brrainz", items[0].author)
        assertEquals("https://steamuserimages-a.akamaihd.net/ugc/123/preview.jpg", items[0].previewUrl)
    }

    @Test
    fun parseWorkshopBrowseHtml_handlesIdBeforeClassAndSrcBeforeClass() {
        val sampleHtml = """
            <div id="sharedfile_987654" class="workshopItem ">
              <div class="workshopItemPreviewHolder">
                <img src="https://images.steamusercontent.com/ugc/987/thumb.jpg" class="workshopItemPreviewImage aspectratio_16x9" />
              </div>
              <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=987654">
                <div class="workshopItemTitle">Mirage Smoke Practice</div>
              </a>
              <div class="workshopItemAuthor">By <a href="https://steamcommunity.com/id/mapper">Yprac</a></div>
            </div>
        """.trimIndent()

        val items = SteamWorkshopRepository.parseWorkshopBrowseHtml(sampleHtml, 730)
        assertEquals(1, items.size)
        assertEquals("987654", items[0].publishedFileId)
        assertEquals("Mirage Smoke Practice", items[0].title)
        assertEquals("Yprac", items[0].author)
        assertEquals("https://images.steamusercontent.com/ugc/987/thumb.jpg", items[0].previewUrl)
    }

    @Test
    fun buildBrowseCandidateUrls_usesAllTimeMostSubscribedSearchAndPaginationParams() {
        val defaultUrls = SteamWorkshopRepository.buildBrowseCandidateUrls(
            appId = 730,
            sortOrder = com.example.model.WorkshopSortOrder.MOST_SUBSCRIBED,
            searchQuery = "",
            page = 1
        )
        assertTrue(defaultUrls[0].contains("browsesort=totaluniquesubscribers"))
        assertTrue(defaultUrls[0].contains("actualsort=totaluniquesubscribers"))
        assertTrue(defaultUrls[0].contains("section=readytouseitems"))
        assertTrue(defaultUrls[0].contains("days=-1"))
        assertTrue(defaultUrls[0].contains("p=1"))
        assertTrue(defaultUrls.any { !it.contains("section=") })
        assertTrue(defaultUrls.any { it.contains("section=mtxitems") })
        assertTrue(defaultUrls.any { it.endsWith("/app/730/workshop/") })

        val pagedSearchUrls = SteamWorkshopRepository.buildBrowseCandidateUrls(
            appId = 294100,
            sortOrder = com.example.model.WorkshopSortOrder.TOP_RATED,
            searchQuery = "combat extended",
            page = 2
        )
        assertTrue(pagedSearchUrls[0].contains("browsesort=toprated"))
        assertTrue(pagedSearchUrls[0].contains("actualsort=toprated"))
        assertTrue(pagedSearchUrls[0].contains("days=-1"))
        assertTrue(pagedSearchUrls[0].contains("p=2"))
        assertTrue(pagedSearchUrls[0].contains("searchtext=combat+extended"))

        assertEquals(
            "birthtime=0; lastagecheckage=1-0-1990; wants_mature_content=1",
            SteamWorkshopRepository.STEAM_AGE_BYPASS_COOKIE
        )
        assertTrue(defaultUrls.any { it.contains("ajax=1") })
    }

    @Test
    fun parseWorkshopBrowseHtml_extractsBrowseRowAndSharedFileHoverAndRelativeLinks() {
        val rowAndHoverHtml = """
            <div class="workshopBrowsePaging">
              <div class='workshopBrowseRow' id='sharedfile_33112200'>
                <a href='/sharedfiles/filedetails/?searchtext=gun&id=33112200'>
                  <img class='workshopItemPreviewImageRow' data-src='https://steamuserimages-a.akamaihd.net/ugc/row_preview.jpg' />
                </a>
                <a href='/sharedfiles/filedetails/?id=33112200'><span class='workshopItemTitle'>Tactical Overhaul Mod</span></a>
                <span class='workshopItemAuthorName'>av <a href='/id/modauthor'>KalleMod</a></span>
              </div>
              <tr class="workshopBrowseRow">
                <td><a href="https://steamcommunity.com/sharedfiles/filedetails/?id=44556677">Ready or Not AI Fix</a></td>
              </tr>
              <script>
                SharedFileBindMouseHover( "sharedfile_88990011", true, { "id": "88990011", "title": "Better Maps Pack", "author": "MapMaker" } );
              </script>
              <div id="sharedfile_99887766" class="workshopBrowseRow">
                <div class="workshopBrowseRowTitle">Opening Tag ID Mod</div>
              </div>
            </div>
        """.trimIndent()

        val items = SteamWorkshopRepository.parseWorkshopBrowseHtml(rowAndHoverHtml, 1144200)
        assertEquals(4, items.size)

        assertEquals("33112200", items[0].publishedFileId)
        assertEquals("Tactical Overhaul Mod", items[0].title)
        assertEquals("KalleMod", items[0].author)
        assertEquals("https://steamuserimages-a.akamaihd.net/ugc/row_preview.jpg", items[0].previewUrl)

        assertEquals("44556677", items[1].publishedFileId)
        assertEquals("Ready or Not AI Fix", items[1].title)

        assertEquals("99887766", items[2].publishedFileId)
        assertEquals("Opening Tag ID Mod", items[2].title)

        assertEquals("88990011", items[3].publishedFileId)
        assertEquals("Better Maps Pack", items[3].title)
        assertEquals("MapMaker", items[3].author)
    }

    @Test
    fun parseWorkshopResponseBody_unwrapsAjaxJsonResultsHtml() {
        val ajaxJson = """
            {
              "total": 42,
              "results_html": "<div class=\"workshopItem\"><a href=\"https://steamcommunity.com/sharedfiles/filedetails/?id=55667788\"><div class=\"workshopItemTitle\">AJAX Loaded Mod</div></a></div>"
            }
        """.trimIndent()

        val items = SteamWorkshopRepository.parseWorkshopResponseBody(ajaxJson, 294100)
        assertEquals(1, items.size)
        assertEquals("55667788", items[0].publishedFileId)
        assertEquals("AJAX Loaded Mod", items[0].title)
    }

    @Test
    fun parseSteamStoreSearchJson_extractsGamesOutsideTop100WithThumbnails() {
        val url = SteamWorkshopRepository.buildStoreSearchUrl("S.T.A.L.K.E.R. 2")
        assertEquals(
            "https://store.steampowered.com/api/storesearch/?term=S.T.A.L.K.E.R.+2&l=english&cc=US",
            url
        )

        val storeJson = """
            {
              "total": 1,
              "items": [
                {
                  "type": "app",
                  "name": "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
                  "id": 1643320,
                  "tiny_image": "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1643320/capsule_231x87.jpg",
                  "price": {
                    "currency": "USD",
                    "initial": 5999,
                    "final": 5999
                  }
                }
              ]
            }
        """.trimIndent()

        val games = SteamWorkshopRepository.parseSteamStoreSearchJson(storeJson)
        assertEquals(1, games.size)
        assertEquals(1643320, games[0].appId)
        assertEquals("S.T.A.L.K.E.R. 2: Heart of Chornobyl", games[0].name)
        assertEquals(
            "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1643320/capsule_231x87.jpg",
            games[0].customImageUrl
        )
        assertEquals(0, games[0].rank)
    }

    @Test
    fun parseSubscribedWorkshopItemsAndResolveProfileHelpers_workCleanly() {
        assertEquals(
            "76561198012345678",
            SteamWorkshopRepository.extractSteamId64FromInput("76561198012345678")
        )
        assertEquals(
            "76561198012345678",
            SteamWorkshopRepository.extractSteamId64FromInput(
                "https://steamcommunity.com/profiles/76561198012345678/myworkshopfiles/?browsefilter=mysubscriptions"
            )
        )
        assertEquals(
            "https://steamcommunity.com/profiles/76561198012345678/myworkshopfiles/?browsefilter=myfavorites&p=1&numperpage=30",
            SteamWorkshopRepository.buildFavoriteWorkshopUrl("76561198012345678", 1)
        )
        assertEquals(
            "https://steamcommunity.com/profiles/76561198012345678/myworkshopfiles/?browsefilter=myfavorites&p=1&numperpage=30",
            SteamWorkshopRepository.buildSubscribedWorkshopUrl("76561198012345678", 1)
        )
        assertEquals(
            "stalkerfan",
            SteamWorkshopRepository.extractVanityFromInput("https://steamcommunity.com/id/stalkerfan/myworkshopfiles/")
        )
        assertEquals(
            "76561198012345678",
            SteamWorkshopRepository.parseSteamId64FromProfileResponse(
                "<profile><steamID64>76561198012345678</steamID64><steamID><![CDATA[Test]]></steamID></profile>"
            )
        )
        assertEquals(
            "76561198012345678",
            SteamWorkshopRepository.parseSteamId64FromProfileResponse(
                """g_rgProfileData = {"url":"https://steamcommunity.com/id/test/","steamid":"76561198012345678"};"""
            )
        )

        val subscribedHtml = """
            <div class="workshopItemSubscription" id="subscription_33991122">
              <div class="workshopItemPreviewHolder">
                <img class="workshopItemPreviewImage" src="https://steamuserimages-a.akamaihd.net/ugc/sub_thumb.jpg" />
              </div>
              <div class="workshopItemSubscriptionDetails">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=33991122">
                  <div class="workshopItemSubscriptionName">Zone Realism Overhaul</div>
                </a>
                <div class="workshopItemAuthorName">by <a href="https://steamcommunity.com/id/zonemod">ZoneModder</a></div>
              </div>
            </div>
        """.trimIndent()

        val subs = SteamWorkshopRepository.parseWorkshopBrowseHtml(subscribedHtml, 0)
        assertEquals(1, subs.size)
        assertEquals("33991122", subs[0].publishedFileId)
        assertEquals("Zone Realism Overhaul", subs[0].title)
        assertEquals("ZoneModder", subs[0].author)
        assertEquals("https://steamuserimages-a.akamaihd.net/ugc/sub_thumb.jpg", subs[0].previewUrl)
    }

    @Test
    fun isPrivateSteamProfileHtml_onlyDetectsExplicitPrivateProfileMarkers() {
        assertTrue(
            SteamWorkshopRepository.isPrivateSteamProfileHtml(
                """<div class="profile_private_info">Some text</div>"""
            )
        )
        assertTrue(
            SteamWorkshopRepository.isPrivateSteamProfileHtml(
                """<div class="error">This profile is private.</div>"""
            )
        )
        assertFalse(
            SteamWorkshopRepository.isPrivateSteamProfileHtml(
                """<form action="https://steamcommunity.com/login/home"></form><div class="no_workshop_items">No items found</div>"""
            )
        )
        assertFalse(
            SteamWorkshopRepository.isPrivateSteamProfileHtml(
                """<div class="workshopBrowseItems"><div class="no_workshop_items">No items found</div></div>"""
            )
        )
    }

    @Test
    fun parseWorkshopResponseBody_andHtml_extractCreatedEpochSec() {
        val json = """
            {
              "response": {
                "publishedfiledetails": [
                  {
                    "publishedfileid": "99112233",
                    "consumer_app_id": 1643320,
                    "title": "Zone Weather Overhaul",
                    "time_created": 1763200000,
                    "time_updated": 1763250000
                  }
                ]
              }
            }
        """.trimIndent()
        val jsonMods = SteamWorkshopRepository.parseWorkshopResponseBody(json, 1643320)
        assertEquals(1, jsonMods.size)
        assertEquals(1763200000L, jsonMods[0].createdEpochSec)
        assertEquals(1763250000L, jsonMods[0].updatedEpochSec)

        val htmlWithTimestamps = """
            <div class="workshopBrowseItems">
              <div class="workshopItem" data-publishedfileid="111222333" data-time-created="1763100000">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=111222333">
                  <div class="workshopItemTitle">Data Attr Mod</div>
                </a>
              </div>
              <div class="workshopBrowseRow" id="sharedfile_444555666">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=444555666">
                  <div class="workshopBrowseRowTitle">Date Div Mod</div>
                </a>
                <div class="workshopBrowseRowDate">15 Nov, 2026 @ 2:30pm</div>
              </div>
              <script>
                SharedFileBindMouseHover( "sharedfile_777888999", true, { "id": "777888999", "title": "Hover Timestamp Mod", "time_created": 1763150000 } );
              </script>
            </div>
        """.trimIndent()
        val htmlMods = SteamWorkshopRepository.parseWorkshopBrowseHtml(htmlWithTimestamps, 1643320)
        assertEquals(3, htmlMods.size)
        assertEquals(1763100000L, htmlMods[0].createdEpochSec)
        assertTrue((htmlMods[1].createdEpochSec ?: 0L) > 1_700_000_000L)
        assertEquals(1763150000L, htmlMods[2].createdEpochSec)
    }

    @Test
    fun parseSteamCommunityDateEpochSec_parsesExplicitAndYearlessDates() {
        val explicit = SteamWorkshopRepository.parseSteamCommunityDateEpochSec("15 Nov, 2026 @ 2:30pm")
        assertEquals(1794753000L, explicit)

        val refFeb2026 = 1_770_000_000L // Feb 2026 UTC
        val yearlessDec = SteamWorkshopRepository.parseSteamCommunityDateEpochSec("28 Dec @ 10:00am", refFeb2026)
        // Dec 28 in 2026 is > 2 days in the future relative to Feb 2026, so it rolls back to Dec 28, 2025
        val dec28_2025 = SteamWorkshopRepository.parseSteamCommunityDateEpochSec("28 Dec, 2025 @ 10:00am")
        assertEquals(dec28_2025, yearlessDec)

        val numeric = SteamWorkshopRepository.parseSteamCommunityDateEpochSec("1763200000")
        assertEquals(1763200000L, numeric)
    }

    @Test
    fun parseWorkshopBrowseHtml_extractsUpdatedTimestampsFromAttributesHoverAndDivs() {
        val htmlWithUpdates = """
            <div class="workshopBrowseItems">
              <div class="workshopItem" data-publishedfileid="211111111" data-time-updated="1765500000">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=211111111">
                  <div class="workshopItemTitle">Attr Updated Mod</div>
                </a>
              </div>
              <div class="workshopBrowseRow" id="sharedfile_222222222">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=222222222">
                  <div class="workshopBrowseRowTitle">Updated Label Mod</div>
                </a>
                <div class="workshopItemUpdated">Updated 15 Nov, 2026 @ 2:30pm</div>
              </div>
              <script>
                SharedFileBindMouseHover( "sharedfile_233333333", true, { "id": "233333333", "title": "Hover Updated Mod", "time_created": 1765100000, "time_updated": 1765600000 } );
              </script>
            </div>
        """.trimIndent()

        val items = SteamWorkshopRepository.parseWorkshopBrowseHtml(htmlWithUpdates, 1643320)
        assertEquals(3, items.size)
        assertEquals(1765500000L, items[0].updatedEpochSec)
        assertEquals(1794753000L, items[1].updatedEpochSec)
        assertEquals(1765100000L, items[2].createdEpochSec)
        assertEquals(1765600000L, items[2].updatedEpochSec)
    }
}

