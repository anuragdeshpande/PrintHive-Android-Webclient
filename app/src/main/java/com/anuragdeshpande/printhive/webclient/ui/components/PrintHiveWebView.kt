package com.anuragdeshpande.printhive.webclient.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.anuragdeshpande.printhive.webclient.bridge.PrintHiveJsInterface

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PrintHiveWebView(
    url: String,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onScanNfcRequested: () -> Unit,
    onFilePathCallback: (ValueCallback<Array<Uri>>?) -> Unit,
    modifier: Modifier = Modifier,
    onConnectionError: () -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                }

                // Attach JavaScript Bridge
                addJavascriptInterface(
                    PrintHiveJsInterface(context, onScanNfcRequested),
                    "PrintHiveNative"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished()

                        val prefs = com.anuragdeshpande.printhive.webclient.data.ServerPreferences(context)
                        val savedToken = prefs.authToken

                        // If we have a saved token in Android prefs but localStorage is empty, inject it
                        if (!savedToken.isNullOrBlank()) {
                            val injectJs = """
                                (function() {
                                    try {
                                        var current = localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
                                        if (!current) {
                                            localStorage.setItem('auth_token', '$savedToken');
                                            sessionStorage.setItem('auth_token', '$savedToken');
                                            if (window.location.pathname === '/login') {
                                                window.location.href = '/';
                                            }
                                        }
                                    } catch(e) {}
                                })()
                            """.trimIndent()
                            view?.evaluateJavascript(injectJs, null)
                        }

                        // Extract session/persistent auth_token from web client storage
                        view?.evaluateJavascript(
                            "(function(){ try { return localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token') || ''; } catch(e){return '';} })()"
                        ) { result ->
                            val token = result?.trim('"', ' ', '\\')
                            if (!token.isNullOrBlank() && token != "null") {
                                if (prefs.authToken != token) {
                                    prefs.authToken = token
                                    com.anuragdeshpande.printhive.webclient.service.PrintHiveWebSocketService.start(view.context)
                                }
                            }
                        }
                    }

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: android.webkit.SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        // Allow local / homelab self-signed certificates so the screen doesn't stay black
                        handler?.proceed()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        onPageFinished()
                        if (request?.isForMainFrame == true) {
                            onConnectionError()
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true) {
                            val code = errorResponse?.statusCode ?: 200
                            if (code >= 400) {
                                onConnectionError()
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return false // Keep all navigation inside the WebView
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        onFilePathCallback(filePathCallback)
                        return true
                    }
                }

                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            val currentWebUrl = webView.url
            if (currentWebUrl.isNullOrBlank()) {
                if (url.isNotBlank()) webView.loadUrl(url)
            } else {
                val currentUri = try { Uri.parse(currentWebUrl) } catch (_: Exception) { null }
                val targetUri = try { Uri.parse(url) } catch (_: Exception) { null }
                val currentHostPort = "${currentUri?.scheme}://${currentUri?.host}:${currentUri?.port}"
                val targetHostPort = "${targetUri?.scheme}://${targetUri?.host}:${targetUri?.port}"
                if (targetUri?.host != null && currentHostPort != targetHostPort && url.isNotBlank()) {
                    webView.loadUrl(url)
                }
            }
        }
    )
}
