package com.example.data

import com.example.model.WorkshopLeaderboardMode
import org.junit.Assert.assertEquals
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
}
