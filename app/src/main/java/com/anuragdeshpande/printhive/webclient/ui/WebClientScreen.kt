package com.anuragdeshpande.printhive.webclient.ui

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anuragdeshpande.printhive.webclient.ui.components.PrintHiveWebView

/**
 * Full-Screen Immersive WebView Mode for PrintHive.
 * Applies statusBarsPadding() to ensure the web header, hamburger menu, and logo
 * render safely below the status bar cutout and notch area.
 */
@Composable
fun WebClientScreen(
    currentUrl: String,
    onOpenServerConfig: () -> Unit,
    onScanNfcRequested: () -> Unit,
    onFilePathCallback: (ValueCallback<Array<Uri>>?) -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Safe WebView container respecting Android system status bar insets
        PrintHiveWebView(
            url = currentUrl,
            onPageStarted = { isLoading = true },
            onPageFinished = { isLoading = false },
            onScanNfcRequested = onScanNfcRequested,
            onFilePathCallback = onFilePathCallback,
            onConnectionError = onOpenServerConfig,
            onWebViewCreated = { activeWebView = it },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        )

        // Subtle loading indicator pinned to top safe area
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}
