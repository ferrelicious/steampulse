package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WatchlistRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndObserveWatchlist_authenticatedOwner_emitsSavedItem() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WatchlistRepository(firestore, auth)
        val gameId = "cs2-${UUID.randomUUID().toString().take(8)}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertWatchlistItem(
                gameId = gameId,
                gameTitle = "Counter-Strike 2",
                targetPriceEur = 11.50,
                preferredCurrency = "SEK"
            )
        }
        assertTrue(result.isSuccess)

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWatchlist(uid).first { list -> list.any { it.id == gameId } }
        }
        val saved = items.first { it.id == gameId }
        assertEquals("Counter-Strike 2", saved.gameTitle)
        assertEquals(11.50, saved.targetPriceEur, 0.01)

        // Update existing item
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertWatchlistItem(
                gameId = gameId,
                gameTitle = "Counter-Strike 2",
                targetPriceEur = 9.99,
                preferredCurrency = "EUR"
            )
        }
        assertTrue(updateResult.isSuccess)

        val updatedItems = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWatchlist(uid).first { list ->
                list.any { it.id == gameId && it.preferredCurrency == "EUR" }
            }
        }
        assertTrue(updatedItems.any { it.id == gameId && it.targetPriceEur == 9.99 })

        // Delete item
        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteWatchlistItem(gameId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun observeWatchlist_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WatchlistRepository(firestore, auth)
        val gameId = "rust-${UUID.randomUUID().toString().take(8)}"

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertWatchlistItem(
                gameId = gameId,
                gameTitle = "Rust",
                targetPriceEur = 18.0,
                preferredCurrency = "SEK"
            )
        }
        assertTrue(createRes.isSuccess)

        // Switch to Bob and try observing Alice's watchlist
        signInTestUser(BOB_EMAIL)
        val bobRepo = WatchlistRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeWatchlist(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeWatchlist_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = WatchlistRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeWatchlist("some_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun upsertWatchlistItem_invalidCurrency_failsRuleValidation() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = WatchlistRepository(firestore, auth)
        val gameId = "bad-${UUID.randomUUID().toString().take(8)}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertWatchlistItem(
                gameId = gameId,
                gameTitle = "Rust",
                targetPriceEur = 15.0,
                preferredCurrency = "USD"
            )
        }
        assertFalse(result.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_steampulse@test.com"
        const val BOB_EMAIL = "bob_steampulse@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
