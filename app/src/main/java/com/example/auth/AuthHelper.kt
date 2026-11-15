package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val TAG = "Auth"
private const val FALLBACK_WEB_CLIENT_ID =
    "116329660329-3pk93eilel8km7r1sh0u2q6lu9risl2h.apps.googleusercontent.com"
private const val LOCAL_PORT = 8765
private const val REDIRECT_URI = "http://localhost:$LOCAL_PORT"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun resolveWebClientId(context: Context): String {
    return try {
        context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
            ?: FALLBACK_WEB_CLIENT_ID
    } catch (_: Exception) {
        FALLBACK_WEB_CLIENT_ID
    }
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
    val clientId = resolveWebClientId(context)

    scope.launch {
        try {
            val activity = context.findActivity()
                ?: throw IllegalStateException("Inloggning kräver en aktiv Activity-kontext.")

            val nonce = UUID.randomUUID().toString()
            val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
                .buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "id_token")
                .appendQueryParameter("scope", "openid email profile")
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("prompt", "select_account")
                .build()

            val serverSocket = withContext(Dispatchers.IO) {
                ServerSocket(LOCAL_PORT, 1, InetAddress.getByName("127.0.0.1")).apply {
                    reuseAddress = true
                    soTimeout = 180_000
                }
            }

            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                activity.startActivity(browserIntent)

                val idToken = withContext(Dispatchers.IO) {
                    receiveIdTokenFromLocalhost(serverSocket, activity.packageName)
                }

                if (idToken.isNullOrBlank()) {
                    onAuthCancelled()
                    return@launch
                }

                val credential = GoogleAuthProvider.getCredential(idToken, null)
                Firebase.auth.signInWithCredential(credential).await()
                onAuthSuccess()
            } finally {
                withContext(Dispatchers.IO) {
                    runCatching { serverSocket.close() }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "OAuth localhost timeout", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Browser OAuth Sign-In failed", e)
            onAuthError("Inloggningen misslyckades: ${e.localizedMessage ?: "Okänt fel"}")
        }
    }
}

private fun receiveIdTokenFromLocalhost(serverSocket: ServerSocket, packageName: String): String? {
    while (true) {
        val client = serverSocket.accept()
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return@use
            while (true) {
                val line = reader.readLine()
                if (line.isNullOrEmpty()) break
            }

            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            val out = socket.getOutputStream()

            if (path.startsWith("/token")) {
                val uri = Uri.parse("http://localhost$path")
                val error = uri.getQueryParameter("error")
                val idToken = uri.getQueryParameter("id_token")

                val doneHtml = """
                    <!DOCTYPE html>
                    <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>SteamPulse</title></head>
                    <body style="background:#0d1117;color:#e6edf3;font-family:sans-serif;text-align:center;padding:48px 20px;">
                    <h2>Inloggning klar!</h2>
                    <p>Du kan stänga den här fliken och återgå till SteamPulse.</p>
                    <script>setTimeout(function(){ window.close(); }, 400);</script>
                    </body></html>
                """.trimIndent()

                val bytes = doneHtml.toByteArray(StandardCharsets.UTF_8)
                val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                out.write(headers.toByteArray(StandardCharsets.UTF_8))
                out.write(bytes)
                out.flush()

                if (!error.isNullOrBlank()) {
                    if (error == "access_denied") return null
                    throw IllegalStateException("Google OAuth-fel: $error")
                }
                return idToken
            } else {
                // Google skickar id_token i URL-fragmentet (#id_token=...) som bara webbläsarens JS kan läsa
                val bridgeHtml = """
                    <!DOCTYPE html>
                    <html><head><meta charset="utf-8"><title>Verifierar...</title></head>
                    <body style="background:#0d1117;color:#e6edf3;font-family:sans-serif;text-align:center;padding:48px 20px;">
                    <p>Slutför inloggning i SteamPulse...</p>
                    <script>
                      var hash = window.location.hash ? window.location.hash.substring(1) : "";
                      var query = window.location.search ? window.location.search.substring(1) : "";
                      var params = hash || query;
                      window.location.replace("/token?" + params);
                    </script>
                    </body></html>
                """.trimIndent()

                val bytes = bridgeHtml.toByteArray(StandardCharsets.UTF_8)
                val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                out.write(headers.toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
        }
    }
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
