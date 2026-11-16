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

        // Verify update notification copy & rescheduling with ExistingPeriodicWorkPolicy.UPDATE
        val updatePosted = WorkshopNotificationHelper.postNewModsNotifications(
            context = app,
            discoveries = listOf(
                NewModNotificationItem(
                    appId = 1643320,
                    gameName = "S.T.A.L.K.E.R. 2",
                    modTitle = "Better Ballistics",
                    publishedFileId = "9003",
                    isUpdate = true
                )
            )
        )
        assertTrue(updatePosted)
        val updatedTitles = shadowManager.allNotifications.mapNotNull {
            it.extras.getString(android.app.Notification.EXTRA_TITLE)
        }
        assertTrue(updatedTitles.any { it.contains("Modd uppdaterad i S.T.A.L.K.E.R. 2") })

        WorkshopNotificationHelper.schedulePeriodicCheck(
            context = app,
            intervalHours = 6L,
            existingPolicy = androidx.work.ExistingPeriodicWorkPolicy.UPDATE
        )
        val rescheduled = WorkManager.getInstance(app)
            .getWorkInfosForUniqueWork(WorkshopNotificationHelper.WORK_NAME)
            .get()
        assertEquals(1, rescheduled.size)
        assertEquals(WorkInfo.State.ENQUEUED, rescheduled.first().state)
    }

    @Test
    fun checkAndNotify_detectsSubscribedModUpdatesAndAvoidsDuplicateAlerts() = runBlocking {
        localStore.setActiveUserId("user_subs")
        localStore.setNewModNotificationsEnabled("user_subs", true)
        localStore.saveFollowedMods(
            "user_subs",
            listOf(
                com.example.model.FollowedWorkshopMod(
                    id = "3370001",
                    userId = "user_subs",
                    publishedFileId = "3370001",
                    appId = 294100,
                    gameName = "RimWorld",
                    title = "HugLib",
                    createdEpochSec = 1_760_000_000L,
                    updatedEpochSec = 1_764_000_000L,
                    source = com.example.model.WorkshopFollowSource.STEAM_SYNC
                )
            )
        )

        var remoteUpdatedEpoch = 1_764_000_000L
        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val json = """
                    {
                      "response": {
                        "publishedfiledetails": [
                          {
                            "publishedfileid": "3370001",
                            "result": 1,
                            "consumer_app_id": 294100,
                            "title": "HugLib",
                            "time_created": 1760000000,
                            "time_updated": $remoteUpdatedEpoch
                          }
                        ]
                      }
                    }
                """.trimIndent()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val repository = SteamWorkshopRepository(httpClient = fakeClient)
        val batches = mutableListOf<List<NewModNotificationItem>>()

        // 1. First run silently initializes baseline updatedEpochSec
        val r1 = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1_764_500_000L,
            notifyHandler = { batches.add(it); true }
        )
        assertEquals(ListenableWorker.Result.success(), r1)
        assertTrue(batches.isEmpty())
        assertEquals(1_764_000_000L, localStore.loadLastNotifiedModUpdatedMap("user_subs")["3370001"])

        // 2. Subscribed mod gets updated remotely -> emits update notification and updates local store
        remoteUpdatedEpoch = 1_765_200_000L
        val r2 = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1_765_201_000L,
            notifyHandler = { batches.add(it); true }
        )
        assertEquals(ListenableWorker.Result.success(), r2)
        assertEquals(1, batches.size)
        assertEquals(1, batches[0].size)
        assertTrue(batches[0][0].isUpdate)
        assertEquals("HugLib", batches[0][0].modTitle)
        assertEquals("RimWorld", batches[0][0].gameName)
        assertEquals(1_765_200_000L, localStore.loadLastNotifiedModUpdatedMap("user_subs")["3370001"])
        assertEquals(1_765_200_000L, localStore.loadSnapshot("user_subs").followedMods.first().updatedEpochSec)

        // 3. Subsequent run with same timestamp -> no duplicate notification
        val r3 = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1_765_208_000L,
            notifyHandler = { batches.add(it); true }
        )
        assertEquals(ListenableWorker.Result.success(), r3)
        assertEquals(1, batches.size)
    }

    @Test
    fun checkAndNotify_defersDuringQuietHours_andRespectsPerTypeToggles() = runBlocking {
        localStore.setActiveUserId("user_quiet")
        localStore.setNewModNotificationsEnabled("user_quiet", true)
        localStore.setQuietHoursEnabled("user_quiet", true)
        localStore.setQuietHoursStart("user_quiet", 22)
        localStore.setQuietHoursEnd("user_quiet", 8)

        localStore.saveFollowedGames(
            "user_quiet",
            listOf(
                FollowedWorkshopGame(
                    id = "1643320",
                    userId = "user_quiet",
                    appId = 1643320,
                    gameName = "S.T.A.L.K.E.R. 2"
                )
            )
        )
        localStore.saveLastNotifiedEpochSec("user_quiet", 1643320, 1_765_000_000L)

        localStore.saveFollowedMods(
            "user_quiet",
            listOf(
                com.example.model.FollowedWorkshopMod(
                    id = "3370001",
                    userId = "user_quiet",
                    publishedFileId = "3370001",
                    appId = 294100,
                    gameName = "RimWorld",
                    title = "HugLib",
                    createdEpochSec = 1_760_000_000L,
                    updatedEpochSec = 1_764_000_000L,
                    source = com.example.model.WorkshopFollowSource.STEAM_SYNC
                )
            )
        )
        localStore.saveLastNotifiedModUpdatedMap("user_quiet", mapOf("3370001" to 1_764_000_000L))

        val browseHtml = """
            <div class="workshopBrowseItems">
              <div class="workshopItem" data-publishedfileid="2002" data-time-created="1765008000">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=2002">
                  <div class="workshopItemTitle">Quiet Window New Mod</div>
                </a>
              </div>
            </div>
        """.trimIndent()
        val apiJson = """
            {
              "response": {
                "publishedfiledetails": [
                  {
                    "publishedfileid": "2002",
                    "result": 1,
                    "consumer_app_id": 1643320,
                    "title": "Quiet Window New Mod",
                    "time_created": 1765008000,
                    "time_updated": 1765008000
                  },
                  {
                    "publishedfileid": "3370001",
                    "result": 1,
                    "consumer_app_id": 294100,
                    "title": "HugLib",
                    "time_created": 1760000000,
                    "time_updated": 1765200000
                  }
                ]
              }
            }
        """.trimIndent()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val isApi = req.url.toString().contains("GetPublishedFileDetails")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        (if (isApi) apiJson else browseHtml)
                            .toResponseBody((if (isApi) "application/json" else "text/html").toMediaType())
                    )
                    .build()
            }
            .build()

        val repository = SteamWorkshopRepository(httpClient = fakeClient)
        val batches = mutableListOf<List<NewModNotificationItem>>()

        // 1. Inside quiet hours (23:00 in 22:00..08:00): nothing posted and baselines preserved
        val quietResult = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1_765_205_000L,
            currentHourProvider = { 23 },
            notifyHandler = { batches.add(it); true }
        )
        assertEquals(ListenableWorker.Result.success(), quietResult)
        assertTrue(batches.isEmpty())
        assertEquals(1_765_000_000L, localStore.getLastNotifiedEpochSec("user_quiet", 1643320))
        assertEquals(1_764_000_000L, localStore.loadLastNotifiedModUpdatedMap("user_quiet")["3370001"])

        // 2. Outside quiet hours (09:00), with notifyNewMods disabled -> only subscribed update fires
        localStore.setNotifyNewModsEnabled("user_quiet", false)
        localStore.setNotifyModUpdatesEnabled("user_quiet", true)
        val awakeUpdateOnly = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1_765_210_000L,
            currentHourProvider = { 9 },
            notifyHandler = { batches.add(it); true }
        )
        assertEquals(ListenableWorker.Result.success(), awakeUpdateOnly)
        assertEquals(1, batches.size)
        assertEquals(1, batches[0].size)
        assertTrue(batches[0][0].isUpdate)
        assertEquals("HugLib", batches[0][0].modTitle)

        // 3. Re-enable notifyNewMods outside quiet hours -> deferred new mod fires!
        localStore.setNotifyNewModsEnabled("user_quiet", true)
        val awakeNewMod = WorkshopCheckWorker.checkAndNotify(
            context = app,
            localStore = localStore,
            repository = repository,
            nowEpochSec = 1_765_215_000L,
            currentHourProvider = { 10 },
            notifyHandler = { batches.add(it); true }
        )
        assertEquals(ListenableWorker.Result.success(), awakeNewMod)
        assertEquals(2, batches.size)
        assertEquals(1, batches[1].size)
        assertFalse(batches[1][0].isUpdate)
        assertEquals("Quiet Window New Mod", batches[1][0].modTitle)
    }
}
