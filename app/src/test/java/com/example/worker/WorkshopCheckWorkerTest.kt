package com.example.worker

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.SteamWorkshopRepository
import com.example.data.WorkshopLocalStore
import com.example.model.FollowedWorkshopGame
import com.example.notifications.NewModNotificationItem
import com.example.notifications.WorkshopNotificationHelper
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkshopCheckWorkerTest {

    private lateinit var app: Application
    private lateinit var localStore: WorkshopLocalStore

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("steam_pulse_workshop_local_store", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        localStore = WorkshopLocalStore(app)

        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
    }

    @Test
    fun notificationChannel_andWorkManagerScheduling_configureExpectedParameters() {
        WorkshopNotificationHelper.createNotificationChannel(app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(WorkshopNotificationHelper.CHANNEL_ID)
            assertNotNull(channel)
            assertEquals("Nya Workshop-moddar", channel.name.toString())
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        }

        val launchIntent = WorkshopNotificationHelper.buildOpenWorkshopIntent(app)
        assertEquals(
            WorkshopNotificationHelper.TAB_WORKSHOP,
            launchIntent.getStringExtra(WorkshopNotificationHelper.EXTRA_OPEN_TAB)
        )
        assertEquals(
            WorkshopNotificationHelper.SECTION_FOLLOWED,
            launchIntent.getStringExtra(WorkshopNotificationHelper.EXTRA_OPEN_WORKSHOP_SECTION)
        )

        WorkshopNotificationHelper.schedulePeriodicCheck(app)
        val scheduled = WorkManager.getInstance(app)
            .getWorkInfosForUniqueWork(WorkshopNotificationHelper.WORK_NAME)
            .get()
        assertEquals(1, scheduled.size)
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.first().state)

        WorkshopNotificationHelper.cancelPeriodicCheck(app)
        val afterCancel = WorkManager.getInstance(app)
            .getWorkInfosForUniqueWork(WorkshopNotificationHelper.WORK_NAME)
            .get()
        assertTrue(afterCancel.isEmpty() || afterCancel.first().state == WorkInfo.State.CANCELLED)
    }

    @Test
    fun checkAndNotify_silentlyInitializesOnFirstRun_thenNotifiesOnlyOnNewerMods() = runBlocking {
        localStore.setActiveUserId("user_test")
        localStore.setNewModNotificationsEnabled("user_test", true)
        localStore.saveFollowedGames(
            "user_test",
            listOf(
                FollowedWorkshopGame(
                    id = "1643320",
                    userId = "user_test",
                    appId = 1643320,
                    gameName = "S.T.A.L.K.E.R. 2: Heart of Chornobyl"
                )
            )
        )

        var currentBrowseHtml = """
            <div class="workshopBrowseItems">
              <div class="workshopItem" data-publishedfileid="1001" data-time-created="1765000000">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=1001">
                  <div class="workshopItemTitle">Existing A-Life Balance</div>
                </a>
              </div>
            </div>
        """.trimIndent()
        var currentApiDetailsJson = """
            {
              "response": {
                "publishedfiledetails": [
                  { "publishedfileid": "1001", "title": "Existing A-Life Balance", "time_created": 1765000000 }
                ]
              }
            }
        """.trimIndent()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val bodyText = if (req.url.toString().contains("GetPublishedFileDetails")) {
                    currentApiDetailsJson
                } else {
                    currentBrowseHtml
                }
                val contentType = if (req.url.toString().contains("GetPublishedFileDetails")) {
                    "application/json".toMediaType()
                } else {
                    "text/html".toMediaType()
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyText.toResponseBody(contentType))
                    .build()
            }
            .build()

        val repository = SteamWorkshopRepository(httpClient = fakeClient)
        val notifiedBatches = mutableListOf<List<NewModNotificationItem>>()

        // 1. First background run: initializes timestamp silently without notifying
        val firstResult = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1765001000L,
            notifyHandler = { items -> notifiedBatches.add(items); true }
        )
        assertEquals(ListenableWorker.Result.success(), firstResult)
        assertTrue(notifiedBatches.isEmpty())
        assertEquals(1765000000L, localStore.getLastNotifiedEpochSec("user_test", 1643320))
        assertTrue(localStore.getLastNotifiedModIds("user_test", 1643320).contains("1001"))

        // 2. Second run with NO new mods -> no notification
        val secondResult = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1765007200L,
            notifyHandler = { items -> notifiedBatches.add(items); true }
        )
        assertEquals(ListenableWorker.Result.success(), secondResult)
        assertTrue(notifiedBatches.isEmpty())

        // 3. Third run with a newly uploaded mod -> notification emitted & timestamp updated
        currentBrowseHtml = """
            <div class="workshopBrowseItems">
              <div class="workshopItem" data-publishedfileid="1002" data-time-created="1765008000">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=1002">
                  <div class="workshopItemTitle">Zone Dynamic Weather v2</div>
                </a>
              </div>
              <div class="workshopItem" data-publishedfileid="1001" data-time-created="1765000000">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=1001">
                  <div class="workshopItemTitle">Existing A-Life Balance</div>
                </a>
              </div>
            </div>
        """.trimIndent()
        currentApiDetailsJson = """
            {
              "response": {
                "publishedfiledetails": [
                  { "publishedfileid": "1002", "title": "Zone Dynamic Weather v2", "time_created": 1765008000 },
                  { "publishedfileid": "1001", "title": "Existing A-Life Balance", "time_created": 1765000000 }
                ]
              }
            }
        """.trimIndent()

        val thirdResult = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1765009000L,
            notifyHandler = { items -> notifiedBatches.add(items); true }
        )
        assertEquals(ListenableWorker.Result.success(), thirdResult)
        assertEquals(1, notifiedBatches.size)
        assertEquals(1, notifiedBatches[0].size)
        assertEquals("S.T.A.L.K.E.R. 2: Heart of Chornobyl", notifiedBatches[0][0].gameName)
        assertEquals("Zone Dynamic Weather v2", notifiedBatches[0][0].modTitle)
        assertEquals(1765008000L, localStore.getLastNotifiedEpochSec("user_test", 1643320))

        // 4. Disabled notifications -> skipped cleanly
        localStore.setNewModNotificationsEnabled("user_test", false)
        currentBrowseHtml = """
            <div class="workshopBrowseItems">
              <div class="workshopItem" data-publishedfileid="1003" data-time-created="1765015000">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=1003">
                  <div class="workshopItemTitle">Ignored New Mod</div>
                </a>
              </div>
            </div>
        """.trimIndent()
        val disabledResult = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1765016000L,
            notifyHandler = { items -> notifiedBatches.add(items); true }
        )
        assertEquals(ListenableWorker.Result.success(), disabledResult)
        assertEquals(1, notifiedBatches.size)
        assertFalse(localStore.getLastNotifiedModIds("user_test", 1643320).contains("1003"))
    }

    @Test
    fun postNewModsNotifications_andTestNotification_publishToNotificationManager() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(manager)

        val testPosted = WorkshopNotificationHelper.postTestNotification(app)
        assertTrue(testPosted)
        assertTrue(shadowManager.allNotifications.any {
            it.extras.getString(android.app.Notification.EXTRA_TITLE) == "Workshop-notiser aktiva"
        })

        val newPosted = WorkshopNotificationHelper.postNewModsNotifications(
            context = app,
            discoveries = listOf(
                NewModNotificationItem(1643320, "S.T.A.L.K.E.R. 2", "Modular Weapon Scopes", "9001"),
                NewModNotificationItem(294100, "RimWorld", "Camera+ Fork", "9002")
            )
        )
        assertTrue(newPosted)
        val titles = shadowManager.allNotifications.mapNotNull {
            it.extras.getString(android.app.Notification.EXTRA_TITLE)
        }
        assertTrue(titles.any { it.contains("S.T.A.L.K.E.R. 2") })
        assertTrue(titles.any { it.contains("RimWorld") })
        assertTrue(titles.any { it.contains("2 nya moddar i 2 följda spel") })
    }
}
