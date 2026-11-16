package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SteamWorkshopRepository
import com.example.data.WorkshopLocalStore
import com.example.model.WorkshopModItem
import com.example.model.WorkshopSortOrder
import com.example.notifications.NewModNotificationItem
import com.example.notifications.WorkshopNotificationHelper
import kotlinx.coroutines.CancellationException

class WorkshopCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val localStore = WorkshopLocalStore(applicationContext)
        val repository = SteamWorkshopRepository()
        return checkAndNotify(
            context = applicationContext,
            localStore = localStore,
            repository = repository,
            runAttemptCount = runAttemptCount
        )
    }

    companion object {
        private const val TAG = "WorkshopCheckWorker"
        private const val MAX_RETRY_ATTEMPTS = 3

        internal suspend fun checkAndNotify(
            context: Context,
            localStore: WorkshopLocalStore,
            repository: SteamWorkshopRepository,
            runAttemptCount: Int = 0,
            nowEpochSec: Long = System.currentTimeMillis() / 1000L,
            notifyHandler: (List<NewModNotificationItem>) -> Boolean = { items ->
                WorkshopNotificationHelper.postNewModsNotifications(context, items)
            }
        ): Result {
            val userKey = localStore.getActiveUserId()
            if (!localStore.isNewModNotificationsEnabled(userKey)) {
                return Result.success()
            }

            val followedGames = localStore.loadSnapshot(userKey).followedGames
            if (followedGames.isEmpty()) {
                return Result.success()
            }

            val discoveries = mutableListOf<NewModNotificationItem>()
            var networkFailures = 0

            for (game in followedGames) {
                val mods = try {
                    repository.fetchWorkshopMods(
                        appId = game.appId,
                        sortOrder = WorkshopSortOrder.MOST_RECENT,
                        searchQuery = "",
                        page = 1
                    ).mods
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed checking new mods for appId=${game.appId}", e)
                    networkFailures++
                    continue
                }

                if (mods.isEmpty()) continue

                val lastNotifiedTimestamp = localStore.getLastNotifiedEpochSec(userKey, game.appId)
                val lastNotifiedIds = localStore.getLastNotifiedModIds(userKey, game.appId)
                val maxCreatedInBatch = mods.maxOfOrNull { effectiveModTimestamp(it) } ?: 0L
                val latestBatchIds = mods.map { it.publishedFileId }.toSet()

                if (lastNotifiedTimestamp == 0L && lastNotifiedIds.isEmpty()) {
                    // First background run for this game: silently initialize baseline without spamming old mods.
                    val baselineTs = maxCreatedInBatch.takeIf { it > 0L } ?: nowEpochSec
                    localStore.saveLastNotifiedEpochSec(userKey, game.appId, baselineTs)
                    localStore.saveLastNotifiedModIds(userKey, game.appId, latestBatchIds)
                    continue
                }

                val newMods = mods.filter { mod ->
                    val createdSec = mod.createdEpochSec?.takeIf { it > 0L } ?: 0L
                    createdSec > lastNotifiedTimestamp && mod.publishedFileId !in lastNotifiedIds
                }

                if (newMods.isNotEmpty()) {
                    val gameLabel = game.gameName.ifBlank { "Steam App #${game.appId}" }
                    newMods.forEach { mod ->
                        discoveries.add(
                            NewModNotificationItem(
                                appId = game.appId,
                                gameName = gameLabel,
                                modTitle = mod.title,
                                publishedFileId = mod.publishedFileId
                            )
                        )
                    }
                    val updatedMaxTs = maxOf(
                        lastNotifiedTimestamp,
                        newMods.maxOfOrNull { it.createdEpochSec ?: 0L } ?: 0L,
                        maxCreatedInBatch
                    )
                    localStore.saveLastNotifiedEpochSec(userKey, game.appId, updatedMaxTs)
                    localStore.saveLastNotifiedModIds(userKey, game.appId, latestBatchIds + lastNotifiedIds)
                } else if (maxCreatedInBatch > lastNotifiedTimestamp) {
                    localStore.saveLastNotifiedEpochSec(userKey, game.appId, maxCreatedInBatch)
                    localStore.saveLastNotifiedModIds(userKey, game.appId, latestBatchIds + lastNotifiedIds)
                }
            }

            if (discoveries.isNotEmpty()) {
                notifyHandler(discoveries)
            }

            return if (networkFailures == followedGames.size && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.success()
            }
        }

        private fun effectiveModTimestamp(mod: WorkshopModItem): Long {
            return mod.createdEpochSec?.takeIf { it > 0L }
                ?: mod.updatedEpochSec?.takeIf { it > 0L }
                ?: 0L
        }
    }
}
