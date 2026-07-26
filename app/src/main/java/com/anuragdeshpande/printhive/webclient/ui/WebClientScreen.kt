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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
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
 * 100% Full-Screen Immersive WebView Mode for PrintHive.
 * Removes top app bar completely for an edge-to-edge native web application feel.
 * Includes a subtle floating server setup button for quick access anytime.
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
        // 100% Edge-to-Edge WebView container
        PrintHiveWebView(
            url = currentUrl,
            onPageStarted = { isLoading = true },
            onPageFinished = { isLoading = false },
            onScanNfcRequested = onScanNfcRequested,
            onFilePathCallback = onFilePathCallback,
            onWebViewCreated = { activeWebView = it },
            modifier = Modifier.fillMaxSize()
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

        // Subtle floating gear button at top-right for changing Server IP
        FloatingActionButton(
            onClick = onOpenServerConfig,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = "Server Configuration"
            )
        }
    }
}
