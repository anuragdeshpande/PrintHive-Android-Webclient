package com.anuragdeshpande.printhive.webclient.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkHelper {

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(2000, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Checks if the device is currently connected to any Wi-Fi network.
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Attempts to read the currently connected Wi-Fi SSID.
     * Returns null or empty string if not on Wi-Fi or if permission is restricted by OS.
     */
    fun getCurrentWifiSsid(context: Context): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val network = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(network) ?: return null

            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = caps.transportInfo as? WifiInfo
                val ssid = wifiInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")
                if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                    return ssid
                }
            }

            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ssid = wm?.connectionInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                return ssid
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Asynchronously pings a server URL (/api/v1/health or base URL) to verify reachability.
     */
    fun checkUrlReachable(
        url: String,
        timeoutMs: Long = 2000,
        onResult: (isReachable: Boolean, responseTimeMs: Long) -> Unit
    ) {
        val normalized = url.trim().removeSuffix("/")
        if (normalized.isBlank()) {
            mainHandler.post { onResult(false, 0) }
            return
        }

        val targetUrl = "$normalized/api/v1/printers"
        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .build()

        val customClient = probeClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val startTime = System.currentTimeMillis()

        customClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onResult(false, System.currentTimeMillis() - startTime)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val elapsed = System.currentTimeMillis() - startTime
                val success = response.isSuccessful || response.code in 200..499
                response.close()
                mainHandler.post {
                    onResult(success, elapsed)
                }
            }
        })
    }
}
