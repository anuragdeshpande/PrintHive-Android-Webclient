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

                    // Identify native client to web app so PWA install prompts are suppressed
                    val currentUa = userAgentString
                    if (!currentUa.contains("PrintHiveApp")) {
                        userAgentString = "$currentUa PrintHiveApp"
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
                        injectRedundancyCleanup(view)
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        injectRedundancyCleanup(view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished()
                        injectRedundancyCleanup(view)

                        val prefs = com.anuragdeshpande.printhive.webclient.data.ServerPreferences(context)
                        val savedToken = prefs.authToken

                        // If we have a saved token in Android prefs but localStorage is empty, inject it
                        // (Only if not on the login page, to avoid interfering with the login screen)
                        if (!savedToken.isNullOrBlank()) {
                            val injectJs = """
                                (function() {
                                    try {
                                        if (window.location.pathname !== '/login') {
                                            var current = localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
                                            if (!current) {
                                                localStorage.setItem('auth_token', '$savedToken');
                                                sessionStorage.setItem('auth_token', '$savedToken');
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
                            } else {
                                // If web client storage is empty and page is on /login, purge cached token
                                if (prefs.authToken != null && view.url?.contains("/login") == true) {
                                    prefs.authToken = null
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
                clearCache(true)
                try {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {}
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

/**
 * Injects CSS and a DOM MutationObserver to:
 * 1. Remove redundant UI elements (top-right Install button and top-left hamburger menu).
 * 2. Add native-feeling swipe-down to dismiss for the mobile bottom sheet (handle and pull-down).
 */
private fun injectRedundancyCleanup(view: WebView?) {
    val js = """
        (function() {
            try {
                window.isPrintHiveApp = true;

                function cleanupRedundancies() {
                    var styleId = 'printhive-native-redundancy-cleanup';
                    if (!document.getElementById(styleId)) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.textContent = [
                            'header button[aria-label="Open menu"] { display: none !important; }',
                            'header button:has(svg.lucide-menu) { display: none !important; }',
                            'header button[title*="Install" i] { display: none !important; }',
                            'header button:has(svg.lucide-download) { display: none !important; }',
                            'button[title*="Install App" i] { display: none !important; }',
                            'button[aria-label="Install App Guide" i] { display: none !important; }'
                        ].join('\n');
                        (document.head || document.documentElement).appendChild(style);
                    }

                    var headers = document.querySelectorAll('header');
                    headers.forEach(function(header) {
                        var buttons = header.querySelectorAll('button');
                        buttons.forEach(function(btn) {
                            var ariaLabel = (btn.getAttribute('aria-label') || '').toLowerCase();
                            var title = (btn.getAttribute('title') || '').toLowerCase();
                            var text = (btn.textContent || '').trim().toLowerCase();
                            var hasMenu = Boolean(btn.querySelector('svg.lucide-menu'));
                            var hasDownload = Boolean(btn.querySelector('svg.lucide-download'));

                            if (ariaLabel === 'open menu' || hasMenu) {
                                btn.style.setProperty('display', 'none', 'important');
                            }

                            if (title.includes('install') || text === 'install' || hasDownload) {
                                btn.style.setProperty('display', 'none', 'important');
                            }
                        });
                    });
                }

                function attachSheetSwipeDismiss() {
                    var sheet = document.querySelector('div.fixed.inset-x-0.bottom-0.z-50.rounded-t-3xl');
                    if (!sheet || sheet.__swipeDismissAttached) return;
                    sheet.__swipeDismissAttached = true;

                    var backdrop = document.querySelector('div.fixed.inset-0.bg-black\\/60');
                    var closeBtn = sheet.querySelector('button[aria-label="Close menu"]');
                    var scrollContainer = sheet.querySelector('.overflow-y-auto');

                    var startX = 0;
                    var startY = 0;
                    var currentDeltaY = 0;
                    var isDragging = false;
                    var canPullFromContent = false;
                    var startTimestamp = 0;

                    function closeSheet() {
                        if (closeBtn) {
                            closeBtn.click();
                        } else if (backdrop) {
                            backdrop.click();
                        }
                        try {
                            if (window.PrintHiveNative && window.PrintHiveNative.triggerVibration) {
                                window.PrintHiveNative.triggerVibration(20);
                            }
                        } catch(e) {}
                    }

                    function onTouchStart(e) {
                        if (e.touches.length !== 1) return;
                        startX = e.touches[0].clientX;
                        startY = e.touches[0].clientY;
                        currentDeltaY = 0;
                        startTimestamp = Date.now();
                        isDragging = false;

                        var target = e.target;
                        var isHeader = Boolean(target.closest('.border-b') || target.closest('[class*="w-12"]'));
                        var isAtScrollTop = scrollContainer ? (scrollContainer.scrollTop <= 0) : true;

                        if (isHeader) {
                            isDragging = true;
                            canPullFromContent = false;
                            sheet.style.transition = 'none';
                        } else if (isAtScrollTop) {
                            canPullFromContent = true;
                        } else {
                            canPullFromContent = false;
                        }
                    }

                    function onTouchMove(e) {
                        if (e.touches.length !== 1) return;
                        var x = e.touches[0].clientX;
                        var y = e.touches[0].clientY;
                        var deltaX = Math.abs(x - startX);
                        var deltaY = y - startY;

                        if (!isDragging && canPullFromContent) {
                            var isAtScrollTop = scrollContainer ? (scrollContainer.scrollTop <= 0) : true;
                            if (deltaY > 8 && deltaY > deltaX && isAtScrollTop) {
                                isDragging = true;
                                startY = y;
                                deltaY = 0;
                                sheet.style.transition = 'none';
                            }
                        }

                        if (isDragging) {
                            if (deltaY > 0) {
                                if (e.cancelable) e.preventDefault();
                                currentDeltaY = deltaY;
                                sheet.style.transform = 'translateY(' + deltaY + 'px)';
                                if (backdrop) {
                                    var opacity = Math.max(0.1, 1 - deltaY / 350);
                                    backdrop.style.opacity = String(opacity);
                                }
                            } else {
                                currentDeltaY = 0;
                                sheet.style.transform = 'translateY(0px)';
                                if (backdrop) backdrop.style.opacity = '';
                            }
                        }
                    }

                    function onTouchEnd() {
                        if (!isDragging) return;
                        isDragging = false;
                        canPullFromContent = false;

                        var elapsed = Date.now() - startTimestamp;
                        var velocity = currentDeltaY / Math.max(elapsed, 1);

                        // Dismiss if dragged down > 70px or quick flick downwards (> 0.4 px/ms)
                        if (currentDeltaY > 70 || (currentDeltaY > 30 && velocity > 0.4)) {
                            sheet.style.transition = 'transform 0.2s cubic-bezier(0.32, 1, 0.23, 1)';
                            sheet.style.transform = 'translateY(100%)';
                            if (backdrop) {
                                backdrop.style.transition = 'opacity 0.2s ease-out';
                                backdrop.style.opacity = '0';
                            }
                            setTimeout(function() {
                                closeSheet();
                                sheet.style.transform = '';
                                sheet.style.transition = '';
                                if (backdrop) {
                                    backdrop.style.opacity = '';
                                    backdrop.style.transition = '';
                                }
                            }, 180);
                        } else {
                            // Snap back
                            sheet.style.transition = 'transform 0.2s ease-out';
                            sheet.style.transform = 'translateY(0px)';
                            if (backdrop) {
                                backdrop.style.transition = 'opacity 0.2s ease-out';
                                backdrop.style.opacity = '';
                            }
                            setTimeout(function() {
                                sheet.style.transition = '';
                                sheet.style.transform = '';
                                if (backdrop) backdrop.style.transition = '';
                            }, 200);
                        }
                    }

                    sheet.addEventListener('touchstart', onTouchStart, { passive: true });
                    sheet.addEventListener('touchmove', onTouchMove, { passive: false });
                    sheet.addEventListener('touchend', onTouchEnd, { passive: true });
                    sheet.addEventListener('touchcancel', onTouchEnd, { passive: true });
                }

                function onDomChange() {
                    cleanupRedundancies();
                    attachSheetSwipeDismiss();
                }

                onDomChange();
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', onDomChange);
                }

                if (!window.__printhiveRedundancyObserver) {
                    window.__printhiveRedundancyObserver = new MutationObserver(function() {
                        onDomChange();
                    });
                    window.__printhiveRedundancyObserver.observe(document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                }
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

