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

            val snapshot = localStore.loadSnapshot(userKey)
            val followedGames = snapshot.followedGames
            val followedMods = snapshot.followedMods
            if (followedGames.isEmpty() && followedMods.isEmpty()) {
                return Result.success()
            }

            val discoveries = mutableListOf<NewModNotificationItem>()
            var networkFailures = 0
            var totalRequests = 0

            for (game in followedGames) {
                totalRequests++
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
                                publishedFileId = mod.publishedFileId,
                                isUpdate = false
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

            if (followedMods.isNotEmpty()) {
                totalRequests++
                val modIds = followedMods.map { it.publishedFileId }.distinct().take(100)
                val detailsMap = try {
                    repository.fetchPublishedFileDetails(modIds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed checking updates for subscribed mods", e)
                    networkFailures++
                    emptyMap()
                }

                if (detailsMap.isNotEmpty()) {
                    val notifiedUpdates = localStore.loadLastNotifiedModUpdatedMap(userKey).toMutableMap()
                    var updatesChanged = false
                    var localModsUpdated = false

                    val refreshedMods = followedMods.map { localMod ->
                        val remote = detailsMap[localMod.publishedFileId] ?: return@map localMod
                        val remoteUpdated = remote.updatedEpochSec ?: 0L
                        val previousNotified = notifiedUpdates[localMod.publishedFileId] ?: 0L

                        if (previousNotified == 0L) {
                            // First background check for this subscribed mod: initialize silently.
                            val baseline = maxOf(remoteUpdated, localMod.updatedEpochSec)
                            if (baseline > 0L) {
                                notifiedUpdates[localMod.publishedFileId] = baseline
                                updatesChanged = true
                            }
                        } else if (remoteUpdated > previousNotified) {
                            discoveries.add(
                                NewModNotificationItem(
                                    appId = localMod.appId.takeIf { it > 0 } ?: remote.appId,
                                    gameName = localMod.gameName.ifBlank { "Steam Workshop" },
                                    modTitle = remote.title.ifBlank { localMod.title },
                                    publishedFileId = localMod.publishedFileId,
                                    isUpdate = true
                                )
                            )
                            notifiedUpdates[localMod.publishedFileId] = remoteUpdated
                            updatesChanged = true
                        }

                        if (remoteUpdated > localMod.updatedEpochSec) {
                            localModsUpdated = true
                            localMod.copy(
                                updatedEpochSec = remoteUpdated,
                                createdEpochSec = localMod.createdEpochSec.takeIf { it > 0L }
                                    ?: (remote.createdEpochSec ?: 0L)
                            )
                        } else {
                            localMod
                        }
                    }

                    if (updatesChanged) {
                        localStore.saveLastNotifiedModUpdatedMap(userKey, notifiedUpdates)
                    }
                    if (localModsUpdated) {
                        localStore.saveFollowedMods(userKey, refreshedMods)
                    }
                }
            }

            if (discoveries.isNotEmpty()) {
                notifyHandler(discoveries)
            }

            return if (totalRequests > 0 && networkFailures == totalRequests && runAttemptCount < MAX_RETRY_ATTEMPTS) {
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
