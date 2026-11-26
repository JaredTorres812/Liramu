package com.applemusic.android.ui.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Home de Apple Music tal cual la web (music.apple.com/{storefront}/home) dentro de un WebView.
 * No depende de la API: muestra TODO lo que muestra la web (Conciertos, Tus amigos, Replay,
 * videos, etc.). La sesión de Apple ID se guarda en las cookies del WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebHomeScreen(
    storefront: String,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 88.dp
) {
    val startUrl = "https://music.apple.com/${storefront.ifBlank { "mx" }}/home"
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        setSupportMultipleWindows(false) // el login de Apple ID abre en la misma vista
                        javaScriptCanOpenWindowsAutomatically = true
                        // Quitar la marca "; wv" para que Apple sirva el reproductor web normal
                        userAgentString = userAgentString.replace("; wv", "")
                    }
                    val webViewRef = this
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webViewRef, true) // necesarias para iniciar sesión
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            canGoBack = view.canGoBack()
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            CookieManager.getInstance().flush()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }

                        // DRM (Widevine) para poder reproducir canciones completas
                        override fun onPermissionRequest(request: PermissionRequest) {
                            val allowed = request.resources.filter {
                                it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                            }
                            if (allowed.isNotEmpty()) request.grant(allowed.toTypedArray()) else request.deny()
                        }
                    }
                    loadUrl(startUrl)
                    webView = this
                }
            },
            onRelease = { it.destroy() }
        )

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
    }
}
