package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.FollowedWorkshopGame
import com.example.model.FollowedWorkshopMod
import com.example.model.SteamWorkshopSyncProfile
import com.example.model.WorkshopFollowSource
import com.example.model.WorkshopGameSummary
import com.example.model.WorkshopModItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class WorkshopFollowRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Du måste vara inloggad med Google för att spara och följa moddar.")
    }

    fun observeFollowedGames(userId: String): Flow<List<FollowedWorkshopGame>> = flow {
        if (userId == "guest") {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/followed_games"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("followed_games")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FollowedWorkshopGame::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeFollowedMods(userId: String): Flow<List<FollowedWorkshopMod>> = flow {
        if (userId == "guest") {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/followed_mods"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("followed_mods")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FollowedWorkshopMod::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        maxOf(it.updatedEpochSec, it.updatedAt?.seconds ?: 0L)
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeSyncProfile(userId: String): Flow<SteamWorkshopSyncProfile?> = flow {
        if (userId == "guest") {
            emit(null)
            return@flow
        }
        val path = "users/$userId/workshop_sync/$SYNC_DOC_ID"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("workshop_sync")
                .document(SYNC_DOC_ID)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        null
                    } else {
                        snapshot.toObject(
                            SteamWorkshopSyncProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, userId)
                }
        )
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        userId: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    private suspend fun safeGetDocument(docRef: DocumentReference): DocumentSnapshot? {
        val defaultSnap = try {
            withTimeoutOrNull(FIRESTORE_READ_TIMEOUT_MS) {
                docRef.get(Source.DEFAULT).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isPermissionDenied(e)) throw e
            Log.w(TAG, "Firestore DEFAULT get failed for ${docRef.path}, falling back to CACHE", e)
            null
        }
        if (defaultSnap != null) return defaultSnap

        return try {
            docRef.get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firestore CACHE get missed for ${docRef.path}", e)
            null
        }
    }

    private suspend fun safeGetCollection(colRef: CollectionReference): QuerySnapshot? {
        val defaultSnap = try {
            withTimeoutOrNull(FIRESTORE_READ_TIMEOUT_MS) {
                colRef.get(Source.DEFAULT).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isPermissionDenied(e)) throw e
            Log.w(TAG, "Firestore DEFAULT collection get failed for ${colRef.path}, falling back to CACHE", e)
            null
        }
        if (defaultSnap != null) return defaultSnap

        return try {
            colRef.get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firestore CACHE collection get missed for ${colRef.path}", e)
            null
        }
    }

    private fun isPermissionDenied(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .any { it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED }
    }

    private fun isOfflineOrUnavailable(error: Throwable): Boolean {
        val fsEx = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsEx != null) {
            return fsEx.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                fsEx.message?.contains("offline", ignoreCase = true) == true
        }
        return error.message?.contains("offline", ignoreCase = true) == true
    }

    suspend fun followGame(game: WorkshopGameSummary): Result<Unit> {
        val uid = requireUserId()
        val safeAppId = game.appId.coerceIn(1, 100_000_000)
        val docRef = db.collection("users")
            .document(uid)
            .collection("followed_games")
            .document(safeAppId.toString())

        val safeName = game.name.trim().ifBlank { "Steam App #$safeAppId" }.take(160)
        val safeDev = game.developer.trim().take(120)
        val safeHeader = game.customImageUrl.ifBlank { game.headerImageUrl }.trim().take(500)

        return try {
            val existing = safeGetDocument(docRef)
            val task = if (existing?.exists() == true) {
                docRef.update(
                    mapOf(
                        "gameName" to safeName,
                        "developer" to safeDev,
                        "headerImageUrl" to safeHeader,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "appId" to safeAppId,
                        "gameName" to safeName,
                        "developer" to safeDev,
                        "headerImageUrl" to safeHeader,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                task.await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineOrUnavailable(e)) {
                Log.w(TAG, "followGame queued/deferred while offline for ${docRef.path}", e)
                Result.success(Unit)
            } else {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
                Result.failure(e)
            }
        }
    }

    suspend fun unfollowGame(appId: Int): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("followed_games")
            .document(appId.toString())

        return try {
            withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                docRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineOrUnavailable(e)) {
                Log.w(TAG, "unfollowGame queued/deferred while offline for ${docRef.path}", e)
                Result.success(Unit)
            } else {
                handleFirestoreError(e, OperationType.DELETE, docRef.path)
                Result.failure(e)
            }
        }
    }

    /**
     * Toggles user bookmark on a mod.
     * - If not saved -> creates with BOOKMARK.
     * - If STEAM_SYNC -> upgrades to BOTH.
     * - If BOTH -> downgrades to STEAM_SYNC (keeps Steam subscription record).
     * - If BOOKMARK -> deletes document.
     * Returns true if the mod is now bookmarked, false if unbookmarked.
     */
    suspend fun toggleBookmarkMod(
        mod: WorkshopModItem,
        gameName: String,
        fallbackExistingSource: String? = null
    ): Result<Boolean> {
        val uid = requireUserId()
        val safeId = sanitizeDocId(mod.publishedFileId)
        val docRef = db.collection("users")
            .document(uid)
            .collection("followed_mods")
            .document(safeId)

        val safeAppId = mod.appId.coerceIn(0, 100_000_000)
        val safeGameName = gameName.trim().take(160)
        val safeTitle = mod.title.trim().ifBlank { "Workshop Item #$safeId" }.take(240)
        val safeAuthor = mod.author.trim().take(120)
        val safePreview = mod.previewUrl.trim().take(600)
        val safeItemUrl = mod.itemUrl.trim()
            .ifBlank { "https://steamcommunity.com/sharedfiles/filedetails/?id=$safeId" }
            .take(400)
        val safeUpdatedEpoch = (mod.updatedEpochSec ?: 0L).coerceAtLeast(0L)
        val safeSubs = (mod.subscriptions ?: 0L).coerceAtLeast(0L)

        var targetBookmarkedState = true
        return try {
            val existingSnap = safeGetDocument(docRef)
            if (existingSnap != null && existingSnap.exists()) {
                val existingSource = existingSnap.getString("source") ?: WorkshopFollowSource.BOOKMARK
                when (existingSource) {
                    WorkshopFollowSource.STEAM_SYNC -> {
                        targetBookmarkedState = true
                        withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                            docRef.update(
                                mapOf(
                                    "appId" to safeAppId,
                                    "gameName" to safeGameName.ifBlank { existingSnap.getString("gameName").orEmpty() },
                                    "title" to safeTitle,
                                    "author" to safeAuthor.ifBlank { existingSnap.getString("author").orEmpty() },
                                    "previewUrl" to safePreview.ifBlank { existingSnap.getString("previewUrl").orEmpty() },
                                    "itemUrl" to safeItemUrl,
                                    "source" to WorkshopFollowSource.BOTH,
                                    "updatedEpochSec" to safeUpdatedEpoch,
                                    "subscriptions" to safeSubs,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        Result.success(true)
                    }

                    WorkshopFollowSource.BOTH -> {
                        targetBookmarkedState = false
                        withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                            docRef.update(
                                mapOf(
                                    "source" to WorkshopFollowSource.STEAM_SYNC,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        Result.success(false)
                    }

                    else -> {
                        targetBookmarkedState = false
                        withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                            docRef.delete().await()
                        }
                        Result.success(false)
                    }
                }
            } else {
                when (fallbackExistingSource) {
                    WorkshopFollowSource.BOOKMARK -> {
                        targetBookmarkedState = false
                        withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                            docRef.delete().await()
                        }
                        Result.success(false)
                    }

                    WorkshopFollowSource.BOTH -> {
                        targetBookmarkedState = false
                        withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                            docRef.set(
                                mapOf(
                                    "userId" to uid,
                                    "publishedFileId" to safeId,
                                    "appId" to safeAppId,
                                    "gameName" to safeGameName,
                                    "title" to safeTitle,
                                    "author" to safeAuthor,
                                    "previewUrl" to safePreview,
                                    "itemUrl" to safeItemUrl,
                                    "source" to WorkshopFollowSource.STEAM_SYNC,
                                    "updatedEpochSec" to safeUpdatedEpoch,
                                    "subscriptions" to safeSubs,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        Result.success(false)
                    }

                    else -> {
                        targetBookmarkedState = true
                        val targetSource = if (fallbackExistingSource == WorkshopFollowSource.STEAM_SYNC) {
                            WorkshopFollowSource.BOTH
                        } else {
                            WorkshopFollowSource.BOOKMARK
                        }
                        withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                            docRef.set(
                                mapOf(
                                    "userId" to uid,
                                    "publishedFileId" to safeId,
                                    "appId" to safeAppId,
                                    "gameName" to safeGameName,
                                    "title" to safeTitle,
                                    "author" to safeAuthor,
                                    "previewUrl" to safePreview,
                                    "itemUrl" to safeItemUrl,
                                    "source" to targetSource,
                                    "updatedEpochSec" to safeUpdatedEpoch,
                                    "subscriptions" to safeSubs,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        Result.success(true)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineOrUnavailable(e)) {
                Log.w(TAG, "toggleBookmarkMod queued/deferred while offline for ${docRef.path}", e)
                Result.success(targetBookmarkedState)
            } else {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
                Result.failure(e)
            }
        }
    }

    suspend fun removeFollowedMod(publishedFileId: String): Result<Unit> {
        val uid = requireUserId()
        val safeId = sanitizeDocId(publishedFileId)
        val docRef = db.collection("users")
            .document(uid)
            .collection("followed_mods")
            .document(safeId)

        return try {
            withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                docRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineOrUnavailable(e)) {
                Log.w(TAG, "removeFollowedMod queued/deferred while offline for ${docRef.path}", e)
                Result.success(Unit)
            } else {
                handleFirestoreError(e, OperationType.DELETE, docRef.path)
                Result.failure(e)
            }
        }
    }

    suspend fun syncSubscribedMods(
        steamId64: String,
        profileInput: String,
        syncedMods: List<FollowedWorkshopMod>
    ): Result<Int> {
        val uid = requireUserId()
        val modsCollection = db.collection("users").document(uid).collection("followed_mods")
        val syncDocRef = db.collection("users").document(uid).collection("workshop_sync").document(SYNC_DOC_ID)

        val safeSteamId = steamId64.trim().take(32)
        val safeProfileInput = profileInput.trim().ifBlank { safeSteamId }.take(240)

        val deduplicated = syncedMods
            .mapNotNull { mod ->
                val safeId = sanitizeDocIdOrNull(mod.publishedFileId) ?: return@mapNotNull null
                mod.copy(publishedFileId = safeId)
            }
            .distinctBy { it.publishedFileId }

        return try {
            val existingDocs = safeGetCollection(modsCollection)
                ?.documents
                ?.associate { it.id to (it.getString("source") ?: WorkshopFollowSource.BOOKMARK) }
                .orEmpty()

            deduplicated.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { mod ->
                    val docRef = modsCollection.document(mod.publishedFileId)
                    val existingSource = existingDocs[mod.publishedFileId]
                    val targetSource = if (existingSource == WorkshopFollowSource.BOOKMARK || existingSource == WorkshopFollowSource.BOTH) {
                        WorkshopFollowSource.BOTH
                    } else {
                        WorkshopFollowSource.STEAM_SYNC
                    }

                    val safeAppId = mod.appId.coerceIn(0, 100_000_000)
                    val safeGameName = mod.gameName.trim().take(160)
                    val safeTitle = mod.title.trim().ifBlank { "Workshop Item #${mod.publishedFileId}" }.take(240)
                    val safeAuthor = mod.author.trim().take(120)
                    val safePreview = mod.previewUrl.trim().take(600)
                    val safeItemUrl = mod.itemUrl.trim()
                        .ifBlank { "https://steamcommunity.com/sharedfiles/filedetails/?id=${mod.publishedFileId}" }
                        .take(400)
                    val safeUpdated = mod.updatedEpochSec.coerceAtLeast(0L)
                    val safeSubs = mod.subscriptions.coerceAtLeast(0L)

                    if (existingSource != null) {
                        batch.update(
                            docRef,
                            mapOf(
                                "appId" to safeAppId,
                                "gameName" to safeGameName,
                                "title" to safeTitle,
                                "author" to safeAuthor,
                                "previewUrl" to safePreview,
                                "itemUrl" to safeItemUrl,
                                "source" to targetSource,
                                "updatedEpochSec" to safeUpdated,
                                "subscriptions" to safeSubs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        batch.set(
                            docRef,
                            mapOf(
                                "userId" to uid,
                                "publishedFileId" to mod.publishedFileId,
                                "appId" to safeAppId,
                                "gameName" to safeGameName,
                                "title" to safeTitle,
                                "author" to safeAuthor,
                                "previewUrl" to safePreview,
                                "itemUrl" to safeItemUrl,
                                "source" to targetSource,
                                "updatedEpochSec" to safeUpdated,
                                "subscriptions" to safeSubs,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                    batch.commit().await()
                }
            }

            val existingSync = safeGetDocument(syncDocRef)
            val syncTask = if (existingSync?.exists() == true) {
                syncDocRef.update(
                    mapOf(
                        "steamId64" to safeSteamId,
                        "profileInput" to safeProfileInput,
                        "syncedCount" to deduplicated.size,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                syncDocRef.set(
                    mapOf(
                        "userId" to uid,
                        "steamId64" to safeSteamId,
                        "profileInput" to safeProfileInput,
                        "syncedCount" to deduplicated.size,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                syncTask.await()
            }

            Result.success(deduplicated.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOfflineOrUnavailable(e)) {
                Log.w(TAG, "syncSubscribedMods queued/deferred while offline for ${modsCollection.path}", e)
                Result.success(deduplicated.size)
            } else {
                handleFirestoreError(e, OperationType.WRITE, modsCollection.path)
                Result.failure(e)
            }
        }
    }

    private fun sanitizeDocId(raw: String): String {
        return sanitizeDocIdOrNull(raw)
            ?: throw IllegalArgumentException("Ogiltigt Workshop-ID: $raw")
    }

    private fun sanitizeDocIdOrNull(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").take(64)
        return cleaned.ifBlank { null }
    }

    companion object {
        private const val TAG = "WorkshopFollowRepo"
        private const val FIRESTORE_READ_TIMEOUT_MS = 3_500L
        private const val FIRESTORE_WRITE_TIMEOUT_MS = 3_500L
        const val SYNC_DOC_ID = "steam"
    }
}
