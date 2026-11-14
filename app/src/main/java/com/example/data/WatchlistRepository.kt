package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.WatchlistItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WatchlistRepository(
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
            ?: throw IllegalStateException("Användaren måste vara inloggad med Google för att använda bevakningslistan.")
    }

    fun observeWatchlist(userId: String): Flow<List<WatchlistItem>> = flow {
        val path = "users/$userId/watchlist"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("watchlist")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchlistItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertWatchlistItem(
        gameId: String,
        gameTitle: String,
        targetPriceEur: Double,
        preferredCurrency: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("watchlist")
            .document(gameId)

        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "gameTitle" to gameTitle,
                        "targetPriceEur" to targetPriceEur,
                        "preferredCurrency" to preferredCurrency,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "gameTitle" to gameTitle,
                        "targetPriceEur" to targetPriceEur,
                        "preferredCurrency" to preferredCurrency,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                OperationType.WRITE,
                docRef.path
            )
            Result.failure(e)
        }
    }

    suspend fun deleteWatchlistItem(gameId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users")
            .document(uid)
            .collection("watchlist")
            .document(gameId)

        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
