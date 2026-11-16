package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SteamWorkshopRepository
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextMuted
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary

private const val STEAM_LOGIN_URL = "https://steamcommunity.com/login/home/"
private const val STEAM_COMMUNITY_ORIGIN = "https://steamcommunity.com"

fun clearSteamWebViewCookies() {
    runCatching {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SteamWebViewLoginDialog(
    onDismiss: () -> Unit,
    onSteamLoginCaptured: (steamId64: String, cookieString: String, userAgentString: String) -> Unit
) {
    var loadProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(true) }
    var sessionCaptured by remember { mutableStateOf(false) }

    fun inspectAndCaptureCookies(url: String?, webView: WebView?) {
        if (sessionCaptured) return
        val cm = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val communityCookie = runCatching { cm.getCookie(STEAM_COMMUNITY_ORIGIN) }.getOrNull().orEmpty()

        val hasSecure = communityCookie.contains("steamLoginSecure=", ignoreCase = true)
        val hasSessionId = communityCookie.contains("sessionid=", ignoreCase = true)
        val steamId64 = SteamWorkshopRepository.extractSteamId64FromCookie(communityCookie)

        if (hasSecure && hasSessionId && !steamId64.isNullOrBlank()) {
            sessionCaptured = true
            runCatching { cm.flush() }
            val userAgent = webView?.settings?.userAgentString.orEmpty()
            onSteamLoginCaptured(steamId64, communityCookie, userAgent)
            return
        }

        val urlLower = url?.lowercase().orEmpty()
        val isLoginOrStoreFinished = urlLower.contains("login.steampowered.com") ||
            urlLower.contains("store.steampowered.com") ||
            urlLower.contains("steamcommunity.com")

        val anyCookie = if (!url.isNullOrBlank()) runCatching { cm.getCookie(url) }.getOrNull().orEmpty() else ""
        val hasAnySecure = anyCookie.contains("steamLoginSecure=", ignoreCase = true) || communityCookie.contains("steamLoginSecure=", ignoreCase = true)

        if (!hasSecure && hasAnySecure && isLoginOrStoreFinished) {
            webView?.let { wv ->
                val currentUrl = wv.url.orEmpty().lowercase()
                if (!currentUrl.contains("steamcommunity.com")) {
                    wv.loadUrl("https://steamcommunity.com/my/home")
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SteamSurface,
            border = BorderStroke(1.dp, SteamBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .testTag("steam_webview_login_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = SteamNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Logga in på Steam",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SteamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Sessionscookie sparas endast lokalt i enheten",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = SteamTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_steam_webview_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Stäng Steam-inloggning",
                            tint = SteamTextSecondary
                        )
                    }
                }

                if (isPageLoading && loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        color = SteamNeon,
                        trackColor = SteamBorder,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    HorizontalDivider(color = SteamBorder)
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        isPageLoading = newProgress < 100
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isPageLoading = true
                                        inspectAndCaptureCookies(url, view)
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        inspectAndCaptureCookies(url, view)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        inspectAndCaptureCookies(request?.url?.toString(), view)
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isPageLoading = false
                                        inspectAndCaptureCookies(url, view)
                                    }
                                }

                                loadUrl(STEAM_LOGIN_URL)
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.destroy()
                        }
                    )
                }
            }
        }
    }
}
