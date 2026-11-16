package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.worker.WorkshopCheckWorker
import java.util.concurrent.TimeUnit

data class NewModNotificationItem(
    val appId: Int,
    val gameName: String,
    val modTitle: String,
    val publishedFileId: String,
    val isUpdate: Boolean = false
)

object WorkshopNotificationHelper {
    const val CHANNEL_ID = "steam_workshop_new_mods"
    const val CHANNEL_NAME = "Nya Workshop-moddar"
    const val WORK_NAME = "steam_workshop_new_mods_check"
    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val TAB_WORKSHOP = "workshop"
    const val EXTRA_OPEN_WORKSHOP_SECTION = "extra_open_workshop_section"
    const val SECTION_FOLLOWED = "followed"

    private const val NOTIFICATION_GROUP_KEY = "com.example.STEAM_WORKSHOP_NEW_MODS"
    private const val SUMMARY_NOTIFICATION_ID = 71000
    private const val TEST_NOTIFICATION_ID = 71001
    private const val BASE_MOD_NOTIFICATION_ID = 72000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.workshop_notifications_channel_desc)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun schedulePeriodicCheck(
        context: Context,
        intervalHours: Long = 2L,
        existingPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        val safeHours = intervalHours.coerceIn(1L, 24L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WorkshopCheckWorker>(safeHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            existingPolicy,
            request
        )
    }

    fun cancelPeriodicCheck(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    fun buildOpenWorkshopIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, TAB_WORKSHOP)
            putExtra(EXTRA_OPEN_WORKSHOP_SECTION, SECTION_FOLLOWED)
        }
    }

    fun postNewModsNotifications(
        context: Context,
        discoveries: List<NewModNotificationItem>
    ): Boolean {
        if (discoveries.isEmpty()) return false
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            buildOpenWorkshopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = NotificationManagerCompat.from(context)
        val shown = discoveries.take(5)

        shown.forEach { item ->
            val notificationId = BASE_MOD_NOTIFICATION_ID +
                ("${item.publishedFileId}_${item.isUpdate}".hashCode() and 0x0FFF)
            val titlePrefix = if (item.isUpdate) "Modd uppdaterad i" else "Ny modd i"
            val bodyText = if (item.isUpdate) {
                "${item.modTitle} har uppdaterats för ${item.gameName} på Steam Workshop."
            } else {
                "${item.modTitle} laddades upp till ${item.gameName} på Steam Workshop."
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_workshop)
                .setContentTitle("$titlePrefix ${item.gameName}")
                .setContentText(item.modTitle)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(bodyText)
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .build()

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notificationManager.notify(notificationId, notification)
            }
        }

        if (discoveries.size > 1) {
            val newCount = discoveries.count { !it.isUpdate }
            val updateCount = discoveries.count { it.isUpdate }
            val gamesCount = discoveries.map { it.appId }.distinct().size
            val summaryText = when {
                updateCount > 0 && newCount > 0 ->
                    "$newCount nya & $updateCount uppdaterade Workshop-moddar"
                updateCount > 0 ->
                    "$updateCount uppdaterade Workshop-moddar"
                gamesCount > 1 ->
                    "${discoveries.size} nya moddar i $gamesCount följda spel"
                else ->
                    "${discoveries.size} nya moddar i ${discoveries.first().gameName}"
            }
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(summaryText)
            discoveries.take(5).forEach { item ->
                val tag = if (item.isUpdate) "Uppdaterad" else "Ny"
                inboxStyle.addLine("[$tag] ${item.gameName}: ${item.modTitle}")
            }

            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_workshop)
                .setContentTitle(summaryText)
                .setContentText("Tryck för att öppna Workshop")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .build()

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notificationManager.notify(SUMMARY_NOTIFICATION_ID, summary)
            }
        }

        return true
    }

    fun postTestNotification(context: Context): Boolean {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val pendingIntent = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            buildOpenWorkshopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_workshop)
            .setContentTitle("Workshop-notiser aktiva")
            .setContentText("SteamPulse bevakar dina följda spel var 2:a timme.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("SteamPulse kontrollerar dina följda spel var 2:a timme och meddelar dig så fort nya Workshop-moddar släpps.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            return true
        }
        return false
    }
}
