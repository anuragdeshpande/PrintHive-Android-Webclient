package com.anuragdeshpande.printhive.webclient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.anuragdeshpande.printhive.webclient.data.ServerPreferences
import com.anuragdeshpande.printhive.webclient.nfc.PrintHiveNfcHandler
import com.anuragdeshpande.printhive.webclient.service.PrintHiveWebSocketService
import com.anuragdeshpande.printhive.webclient.ui.ServerConfigDialog
import com.anuragdeshpande.printhive.webclient.ui.ServerSetupScreen
import com.anuragdeshpande.printhive.webclient.ui.WebClientScreen
import com.anuragdeshpande.printhive.webclient.ui.theme.PrintHiveTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: ServerPreferences
    private lateinit var nfcHandler: PrintHiveNfcHandler
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var onNetworkUrlChangedListener: ((String) -> Unit)? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == RESULT_OK && result.data != null) {
            val intentData = result.data
            if (intentData?.data != null) {
                arrayOf(intentData.data!!)
            } else {
                null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val postNotifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (postNotifGranted && prefs.isSetupCompleted) {
            PrintHiveWebSocketService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ServerPreferences(this)
        nfcHandler = PrintHiveNfcHandler(this)

        checkAndRequestPermissions()
        registerNetworkMonitoring()

        // Handle initial intent if app launched via NFC tag
        intent?.let { handleIncomingIntent(it) }

        setContent {
            PrintHiveTheme {
                var serverUrl by remember { mutableStateOf(prefs.getActiveServerUrl()) }
                var isSetupDone by remember { mutableStateOf(prefs.isSetupCompleted) }
                var showConfigDialog by remember { mutableStateOf(false) }

                onNetworkUrlChangedListener = { newUrl ->
                    if (serverUrl != newUrl) {
                        serverUrl = newUrl
                        PrintHiveWebSocketService.start(this)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isSetupDone) {
                        ServerSetupScreen(
                            initialLocalUrl = prefs.localServerUrl,
                            initialRemoteUrl = prefs.remoteServerUrl,
                            onConnect = { localUrl, remoteUrl ->
                                prefs.localServerUrl = localUrl
                                prefs.remoteServerUrl = remoteUrl
                                prefs.isSetupCompleted = true
                                serverUrl = prefs.getActiveServerUrl()
                                isSetupDone = true
                                PrintHiveWebSocketService.start(this)
                            }
                        )
                    } else {
                        WebClientScreen(
                            currentUrl = serverUrl,
                            onOpenServerConfig = { showConfigDialog = true },
                            onScanNfcRequested = {
                                if (!nfcHandler.isNfcAvailable) {
                                    Toast.makeText(this, "NFC is not enabled or supported on this device", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Tap an NFC tag against the back of your phone", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFilePathCallback = { callback ->
                                filePathCallback = callback
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                }
                                filePickerLauncher.launch(Intent.createChooser(intent, "Select File"))
                            }
                        )

                        if (showConfigDialog) {
                            ServerConfigDialog(
                                initialLocalUrl = prefs.localServerUrl,
                                initialRemoteUrl = prefs.remoteServerUrl,
                                initialSsid = prefs.homeWifiSsid,
                                initialMode = prefs.connectionMode,
                                onDismiss = { showConfigDialog = false },
                                onSaveConfig = { localUrl, remoteUrl, ssid, mode ->
                                    prefs.localServerUrl = localUrl
                                    prefs.remoteServerUrl = remoteUrl
                                    prefs.homeWifiSsid = ssid
                                    prefs.connectionMode = mode
                                    val resolvedUrl = prefs.getActiveServerUrl()
                                    serverUrl = resolvedUrl
                                    showConfigDialog = false
                                    PrintHiveWebSocketService.start(this)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun registerNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    evaluateNetworkSwitch()
                }

                override fun onLost(network: Network) {
                    evaluateNetworkSwitch()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    evaluateNetworkSwitch()
                }
            }

            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(request, it)
            }
        } catch (_: Exception) {
        }
    }

    private fun evaluateNetworkSwitch() {
        mainHandler.postDelayed({
            val resolvedUrl = prefs.getActiveServerUrl()
            onNetworkUrlChangedListener?.invoke(resolvedUrl)
        }, 500)
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else if (prefs.isSetupCompleted) {
            PrintHiveWebSocketService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        evaluateNetworkSwitch()
        nfcHandler.enableForegroundDispatch { nfcPayload ->
            Toast.makeText(this, "NFC Tag Scanned: $nfcPayload", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        nfcHandler.disableForegroundDispatch()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        nfcHandler.handleNfcIntent(intent) { nfcPayload ->
            Toast.makeText(this, "NFC Tag Discovered: $nfcPayload", Toast.LENGTH_LONG).show()
        }
    }
}
