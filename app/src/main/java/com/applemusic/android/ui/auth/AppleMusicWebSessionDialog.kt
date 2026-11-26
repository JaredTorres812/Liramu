package com.applemusic.android.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

private class WebSessionBridge(
    private val onCaptured: (userToken: String, devToken: String) -> Unit
) {
    private val delivered = AtomicBoolean(false)

    @JavascriptInterface
    fun onSessionReady(userToken: String, devToken: String) {
        if (userToken.isNotBlank() && delivered.compareAndSet(false, true)) {
            onCaptured(userToken.trim(), devToken.trim())
        }
    }

    fun tryDeliverFromCookie(cookieString: String?) {
        if (cookieString.isNullOrBlank() || delivered.get()) return
        val match = COOKIE_REGEX.find(cookieString) ?: return
        val token = runCatching { URLDecoder.decode(match.groupValues[1].trim(), "UTF-8") }
            .getOrDefault(match.groupValues[1].trim())
        if (token.isNotBlank() && delivered.compareAndSet(false, true)) {
            onCaptured(token, "")
        }
    }

    private companion object {
        val COOKIE_REGEX = Regex("""(?:^|;\s*)media-user-token=([^;]+)""")
    }
}

/**
 * Inicio de sesión en un solo paso con Apple Music:
 * - Abre directamente la pantalla de inicio de sesión (`/login`) en lugar del portal web completo.
 * - Captura al instante `media-user-token` + `developerToken` (cookies, localStorage o MusicKit)
 *   sin mostrar pantallas intermedias ni pedir una segunda autorización.
 * - Muestra una transición limpia "Conectando tu cuenta…" y cierra el diálogo de inmediato.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleMusicWebSessionDialog(
    storefront: String,
    onDismiss: () -> Unit,
    onSessionCaptured: (userToken: String, devToken: String) -> Unit
) {
    val startUrl = "https://music.apple.com/${storefront.ifBlank { "mx" }}/login"
    var progress by remember { mutableIntStateOf(0) }
    var isConnectingSession by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = {
            webViewRef?.destroy()
            webViewRef = null
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isConnectingSession) "Conectando tu cuenta…" else "Iniciar sesión en Apple Music",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                webViewRef?.destroy()
                                webViewRef = null
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val bridge = WebSessionBridge { userToken, devToken ->
                                post {
                                    isConnectingSession = true
                                    CookieManager.getInstance().flush()
                                    stopLoading()
                                    destroy()
                                    webViewRef = null
                                    onSessionCaptured(userToken, devToken)
                                }
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = true
                                userAgentString = userAgentString.replace("; wv", "")
                            }
                            val webView = this
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(webView, true)
                            }
                            addJavascriptInterface(bridge, "WebAuthBridge")

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    bridge.tryDeliverFromCookie(
                                        CookieManager.getInstance().getCookie("https://music.apple.com")
                                    )
                                    return false
                                }

                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    bridge.tryDeliverFromCookie(
                                        CookieManager.getInstance().getCookie("https://music.apple.com")
                                    )
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    CookieManager.getInstance().flush()
                                    bridge.tryDeliverFromCookie(
                                        CookieManager.getInstance().getCookie("https://music.apple.com")
                                    )
                                    if (url?.contains("apple.com") == true) {
                                        view.evaluateJavascript(SESSION_WATCHER_JS, null)
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            loadUrl(startUrl)
                            webViewRef = this
                        }
                    },
                    onRelease = { it.destroy() }
                )

                if (progress in 1..99 && !isConnectingSession) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }

                if (isConnectingSession) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Conectando tu cuenta…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SESSION_WATCHER_JS = """
    (function () {
      if (window.__amWatcherStarted) return;
      window.__amWatcherStarted = true;
      var tries = 0;
      var autoClickedSignIn = false;
      function readCookieToken() {
        try {
          var m = document.cookie.match(/(?:^|;\s*)media-user-token=([^;]+)/);
          if (m && m[1]) return decodeURIComponent(m[1]);
        } catch (e) {}
        return '';
      }
      function check() {
        tries++;
        try {
          var mk = window.MusicKit && MusicKit.getInstance && MusicKit.getInstance();
          var dev = (mk && mk.developerToken) || '';
          if (mk && mk.musicUserToken) {
            WebAuthBridge.onSessionReady(mk.musicUserToken, dev);
            return;
          }
          var lsToken = window.localStorage && window.localStorage.getItem('music.ampwebplay.media-user-token');
          if (lsToken) {
            WebAuthBridge.onSessionReady(lsToken, dev);
            return;
          }
          var ckToken = readCookieToken();
          if (ckToken) {
            WebAuthBridge.onSessionReady(ckToken, dev);
            return;
          }
          // Si Apple redirige al portal web sin abrir el modal de login, pulsar Iniciar sesión automáticamente una vez.
          if (!autoClickedSignIn && location.hostname === 'music.apple.com' && tries >= 4) {
            var btn = document.querySelector('button[data-testid="sign-in-button"], button.web-navigation__auth-button, a[href*="/login"]');
            if (btn) {
              autoClickedSignIn = true;
              btn.click();
            }
          }
        } catch (e) {}
        if (tries < 1200) setTimeout(check, 250);
      }
      check();
    })();
""".trimIndent()
