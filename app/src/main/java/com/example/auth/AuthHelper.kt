package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG = "Auth"
private const val ANDROID_CLIENT_ID = "116329660329-2tiv9k8j1u3q0e7ruu9jbautllpseeu2.apps.googleusercontent.com"
private const val REDIRECT_URI = "com.ferrelicious.steampulse:/oauth2redirect"

private object OAuthSession {
    var pendingResult: CompletableDeferred<Uri?>? = null
}

class GoogleOAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OAuthSession.pendingResult?.complete(intent?.data)
        OAuthSession.pendingResult = null
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OAuthSession.pendingResult?.complete(intent.data)
        OAuthSession.pendingResult = null
        finish()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
    } else {
        onUnauthenticated()
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    scope.launch {
        try {
            val activity = context.findActivity()
                ?: throw IllegalStateException("Inloggning kräver en aktiv Activity-kontext.")

            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)

            val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
                .buildUpon()
                .appendQueryParameter("client_id", ANDROID_CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "openid email profile")
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("prompt", "select_account")
                .build()

            val deferred = CompletableDeferred<Uri?>()
            OAuthSession.pendingResult?.cancel()
            OAuthSession.pendingResult = deferred

            val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            activity.startActivity(browserIntent)

            val callbackUri = deferred.await()
            if (callbackUri == null) {
                onAuthCancelled()
                return@launch
            }

            val error = callbackUri.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                if (error == "access_denied") {
                    onAuthCancelled()
                } else {
                    onAuthError("Google OAuth-fel: $error")
                }
                return@launch
            }

            val authCode = callbackUri.getQueryParameter("code")
                ?: throw IllegalStateException("Ingen auktoriseringskod returnerades från Google.")

            val (idToken, accessToken) = withContext(Dispatchers.IO) {
                exchangeCodeForTokens(authCode, codeVerifier)
            }

            val credential = GoogleAuthProvider.getCredential(idToken, accessToken)
            Firebase.auth.signInWithCredential(credential).await()
            onAuthSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "PKCE OAuth Sign-In failed", e)
            onAuthError("Inloggningen misslyckades: ${e.localizedMessage ?: "Okänt fel"}")
        }
    }
}

private fun exchangeCodeForTokens(code: String, codeVerifier: String): Pair<String, String?> {
    val url = URL("https://oauth2.googleapis.com/token")
    val body = listOf(
        "client_id" to ANDROID_CLIENT_ID,
        "code" to code,
        "code_verifier" to codeVerifier,
        "grant_type" to "authorization_code",
        "redirect_uri" to REDIRECT_URI
    ).joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
    }

    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    }

    conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

    val responseCode = conn.responseCode
    val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
    val responseText = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    if (responseCode !in 200..299) {
        throw IllegalStateException("Token-utbyte misslyckades ($responseCode): $responseText")
    }

    val json = JSONObject(responseText)
    val idToken = json.optString("id_token").takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Svaret saknade id_token.")
    val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
    return idToken to accessToken
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
