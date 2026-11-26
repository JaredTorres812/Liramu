package com.applemusic.android.ui.auth

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class MusicKitJsBridge(
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    @JavascriptInterface
    fun onTokenReceived(token: String) {
        if (token.isNotBlank()) {
            onSuccess(token)
        } else {
            onError("No se recibió el Music User Token.")
        }
    }

    @JavascriptInterface
    fun onAuthError(message: String) {
        onError(message.ifBlank { "Error al autorizar con Apple Music." })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppleMusicWebAuthDialog(
    developerToken: String,
    onDismiss: () -> Unit,
    onTokenReceived: (String) -> Unit,
    onError: (String) -> Unit
) {
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = {
            popupWebView?.destroy()
            popupWebView = null
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Iniciar sesión con Apple") },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                popupWebView?.destroy()
                                popupWebView = null
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
                // Host WebView runs MusicKit JS silently and launches Apple ID directly
                AndroidView(
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f),
                    factory = { context ->
                        createConfiguredWebView(context).apply {
                            addJavascriptInterface(
                                MusicKitJsBridge(
                                    onSuccess = { token ->
                                        post {
                                            popupWebView?.destroy()
                                            popupWebView = null
                                            onTokenReceived(token)
                                        }
                                    },
                                    onError = { msg ->
                                        post {
                                            popupWebView?.destroy()
                                            popupWebView = null
                                            onError(msg)
                                        }
                                    }
                                ),
                                "AndroidBridge"
                            )

                            webViewClient = WebViewClient()

                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val child = createConfiguredWebView(context).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean = false
                                        }
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onCloseWindow(window: WebView?) {
                                                popupWebView?.destroy()
                                                popupWebView = null
                                            }
                                        }
                                    }

                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = child
                                    resultMsg?.sendToTarget()
                                    popupWebView = child
                                    return true
                                }
                            }

                            loadDataWithBaseURL(
                                "https://music.apple.com/",
                                buildMusicKitHtml(developerToken),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                )

                val activePopup = popupWebView
                if (activePopup != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { activePopup }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ContainedLoadingIndicator()
                            Text(
                                text = "Abriendo Apple ID...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(context: android.content.Context): WebView {
    val webView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }
    return webView
}

private fun buildMusicKitHtml(developerToken: String): String {
    val safeToken = developerToken
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "")
        .replace("\r", "")

    return """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8" /></head>
        <body>
            <script>
                let initialized = false;

                async function initMusicKit() {
                    if (initialized || typeof MusicKit === 'undefined') return;
                    initialized = true;
                    try {
                        await MusicKit.configure({
                            developerToken: '$safeToken',
                            app: {
                                name: 'Apple Music Android',
                                build: '1.0.0'
                            }
                        });
                        const music = MusicKit.getInstance();
                        const mut = await music.authorize();
                        if (mut) {
                            AndroidBridge.onTokenReceived(mut);
                        } else {
                            AndroidBridge.onAuthError('Inicio de sesión cancelado.');
                        }
                    } catch (err) {
                        const msg = (err && err.message) ? err.message : String(err);
                        AndroidBridge.onAuthError(msg);
                    }
                }

                document.addEventListener('musickitloaded', initMusicKit);
            </script>
            <script src="https://js-cdn.music.apple.com/musickit/v3/musickit.js"
                    async
                    onload="initMusicKit()"
                    onerror="AndroidBridge.onAuthError('No se pudo conectar con Apple Music.')">
            </script>
        </body>
        </html>
    """.trimIndent()
}
