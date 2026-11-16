package com.example.update

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.AppUpdateDialog
import com.example.ui.theme.SteamPulseTheme
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateManagerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("steampulse_app_updater", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun parseLatestReleaseJson_extractsTagBodyAndFirstApkAsset() {
        val json = """
            {
              "tag_name": "v1.0.14",
              "draft": false,
              "body": "## Vad är nytt\n- Snabbare Steam Workshop-sync",
              "assets": [
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://example.com/checksums.txt",
                  "size": 128
                },
                {
                  "name": "SteamPulse-v1.0.14.apk",
                  "browser_download_url": "https://github.com/ferrelicious/steampulse/releases/download/v1.0.14/SteamPulse-v1.0.14.apk",
                  "size": 24500123
                }
              ]
            }
        """.trimIndent()

        val parsed = AppUpdateManager.parseLatestReleaseJson(json)
        assertNotNull(parsed)
        assertEquals("v1.0.14", parsed!!.tagName)
        assertEquals("1.0.14", parsed.versionName)
        assertTrue(parsed.releaseNotes.contains("Snabbare Steam Workshop-sync"))
        assertEquals(
            "https://github.com/ferrelicious/steampulse/releases/download/v1.0.14/SteamPulse-v1.0.14.apk",
            parsed.apkDownloadUrl
        )
        assertEquals("SteamPulse-v1.0.14.apk", parsed.apkAssetName)
        assertEquals(24500123L, parsed.apkSizeBytes)

        // Draft or missing APK asset returns null
        assertNull(
            AppUpdateManager.parseLatestReleaseJson(
                """{"tag_name":"v1.0.15","draft":true,"assets":[{"name":"app.apk","browser_download_url":"https://x/a.apk"}]}"""
            )
        )
        assertNull(
            AppUpdateManager.parseLatestReleaseJson(
                """{"tag_name":"v1.0.15","draft":false,"assets":[{"name":"notes.txt","browser_download_url":"https://x/n.txt"}]}"""
            )
        )
    }

    @Test
    fun isRemoteVersionNewer_stripsLeadingVAndComparesSemver() {
        assertTrue(AppUpdateManager.isRemoteVersionNewer("v1.0.2", "1.0.0"))
        assertTrue(AppUpdateManager.isRemoteVersionNewer("V1.2.0", "1.0.9"))
        assertFalse(AppUpdateManager.isRemoteVersionNewer("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isRemoteVersionNewer("v0.9.8", "1.0.0"))
    }

    @Test
    fun checkForUpdate_respectsDismissedTagsUntilManualCheck() = runTest {
        val responseJson = """
            {
              "tag_name": "v2.1.0",
              "draft": false,
              "body": "- Ny prisbevakning",
              "assets": [
                {
                  "name": "SteamPulse-v2.1.0.apk",
                  "browser_download_url": "https://example.com/SteamPulse-v2.1.0.apk",
                  "size": 1000
                }
              ]
            }
        """.trimIndent()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val manager = AppUpdateManager(
            context = context,
            httpClient = fakeClient,
            currentVersionName = "1.0.0"
        )

        val firstOutcome = manager.checkForUpdate(ignoreDismissed = false)
        assertTrue(firstOutcome is AppUpdateCheckOutcome.Available)

        manager.dismissTag("v2.1.0")
        assertTrue(manager.isTagDismissed("v2.1.0"))

        val silentAfterDismiss = manager.checkForUpdate(ignoreDismissed = false)
        assertTrue(silentAfterDismiss is AppUpdateCheckOutcome.Dismissed)

        val manualAfterDismiss = manager.checkForUpdate(ignoreDismissed = true)
        assertTrue(manualAfterDismiss is AppUpdateCheckOutcome.Available)
    }

    @Test
    fun intents_configureUnknownSourcesAndFileProviderInstallUri() {
        val manager = AppUpdateManager(context = context, currentVersionName = "1.0.0")

        val settingsIntent = manager.buildUnknownSourcesSettingsIntent()
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settingsIntent.action)
        assertEquals("package:${context.packageName}", settingsIntent.dataString)

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        downloadsDir.mkdirs()
        val fakeApk = File(downloadsDir, "SteamPulse-v1.0.5.apk").apply {
            writeText("fake-apk-bytes")
        }

        val installIntent = manager.buildInstallApkIntent(fakeApk)
        assertEquals(Intent.ACTION_VIEW, installIntent.action)
        assertEquals(AppUpdateManager.APK_MIME_TYPE, installIntent.type)
        assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertEquals("content", installIntent.data?.scheme)
        assertTrue(installIntent.dataString!!.contains("fileprovider"))
    }

    @Test
    fun appUpdateDialog_displaysVersionChangelogAndButtons() {
        var updateClicked = false
        var laterClicked = false
        val release = AppUpdateRelease(
            tagName = "v1.2.0",
            versionName = "1.2.0",
            releaseNotes = "## Nyheter\n- Lade till målpris på Bevaka",
            apkDownloadUrl = "https://example.com/SteamPulse-v1.2.0.apk",
            apkAssetName = "SteamPulse-v1.2.0.apk",
            apkSizeBytes = 123456L
        )

        composeTestRule.setContent {
            SteamPulseTheme {
                AppUpdateDialog(
                    release = release,
                    currentVersionName = "1.0.0",
                    onUpdateNow = { updateClicked = true },
                    onLater = { laterClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("app_update_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("v1.0.0 → v1.2.0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_update_version_comparison").assertIsDisplayed()
        composeTestRule.onNodeWithText("Installerad: v1.0.0  →  Ny version: v1.2.0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_update_changelog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_now_button").assertIsDisplayed().performClick()
        assertTrue(updateClicked)

        composeTestRule.onNodeWithTag("update_later_button").assertIsDisplayed().performClick()
        assertTrue(laterClicked)
    }
}
