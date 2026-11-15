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
    fun buildBrowseCandidateUrls_startsWithoutForcedSectionAndIncludesFallbacks() {
        val urls = SteamWorkshopRepository.buildBrowseCandidateUrls(
            appId = 730,
            sortOrder = com.example.model.WorkshopSortOrder.MOST_SUBSCRIBED,
            searchQuery = "",
            page = 1
        )
        assertTrue(urls[0].contains("browsesort=totalsubscribers"))
        assertTrue(!urls[0].contains("section="))
        assertTrue(urls.any { it.contains("section=readytouseitems") })
        assertTrue(urls.any { it.contains("section=mtxitems") })
        assertTrue(urls.any { it.endsWith("/app/730/workshop/") })
    }
}
