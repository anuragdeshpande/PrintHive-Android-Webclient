package com.anuragdeshpande.printhive.webclient.data

import android.content.Context
import android.content.SharedPreferences

class ServerPreferences(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("printhive_webclient_prefs", Context.MODE_PRIVATE)

    var localServerUrl: String
        get() = prefs.getString(KEY_LOCAL_SERVER_URL, DEFAULT_LOCAL_SERVER_URL) ?: DEFAULT_LOCAL_SERVER_URL
        set(value) {
            val normalized = normalizeUrl(value)
            prefs.edit().putString(KEY_LOCAL_SERVER_URL, normalized).apply()
        }

    var remoteServerUrl: String
        get() = prefs.getString(KEY_REMOTE_SERVER_URL, DEFAULT_REMOTE_SERVER_URL) ?: DEFAULT_REMOTE_SERVER_URL
        set(value) {
            val normalized = normalizeUrl(value)
            prefs.edit().putString(KEY_REMOTE_SERVER_URL, normalized).apply()
        }

    var homeWifiSsid: String
        get() = prefs.getString(KEY_HOME_WIFI_SSID, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_HOME_WIFI_SSID, value.trim()).apply()
        }

    var connectionMode: String
        get() = prefs.getString(KEY_CONNECTION_MODE, MODE_AUTO) ?: MODE_AUTO
        set(value) {
            prefs.edit().putString(KEY_CONNECTION_MODE, value).apply()
        }

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()
        }

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(KEY_AUTH_TOKEN).apply()
            } else {
                prefs.edit().putString(KEY_AUTH_TOKEN, value.trim()).apply()
            }
        }

    /**
     * Resolves which URL to use based on connectionMode, Wi-Fi status, and SSID matching.
     */
    fun getActiveServerUrl(): String {
        return when (connectionMode) {
            MODE_LOCAL -> localServerUrl
            MODE_REMOTE -> remoteServerUrl
            MODE_AUTO -> {
                val isWifi = NetworkHelper.isWifiConnected(context)
                val currentSsid = NetworkHelper.getCurrentWifiSsid(context)
                val configuredSsid = homeWifiSsid.trim()

                if (configuredSsid.isNotBlank()) {
                    if (currentSsid != null && currentSsid.equals(configuredSsid, ignoreCase = true)) {
                        localServerUrl
                    } else if (isWifi && (currentSsid == null || currentSsid.isEmpty())) {
                        // On Wi-Fi but SSID hidden/restricted by OS without fine location
                        localServerUrl
                    } else {
                        remoteServerUrl
                    }
                } else {
                    // If no specific Home SSID configured: Wi-Fi -> Local, Cellular -> Remote
                    if (isWifi) localServerUrl else remoteServerUrl
                }
            }
            else -> localServerUrl
        }
    }

    /**
     * Compatibility getter and setter for legacy callers.
     * Getter resolves the currently active server URL.
     * Setter updates localServerUrl.
     */
    var serverUrl: String
        get() = getActiveServerUrl()
        set(value) {
            localServerUrl = value
        }

    val webSocketUrl: String
        get() = getWebSocketUrlFor(getActiveServerUrl())

    fun getWebSocketUrlFor(baseUrl: String): String {
        val url = normalizeUrl(baseUrl)
        return if (url.startsWith("https://")) {
            url.replace("https://", "wss://") + "/api/v1/ws"
        } else {
            url.replace("http://", "ws://") + "/api/v1/ws"
        }
    }

    private fun normalizeUrl(raw: String): String {
        var trimmed = raw.trim().removeSuffix("/")
        if (trimmed.isNotBlank() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        return trimmed
    }

    companion object {
        const val KEY_LOCAL_SERVER_URL = "local_server_url"
        const val KEY_REMOTE_SERVER_URL = "remote_server_url"
        const val KEY_HOME_WIFI_SSID = "home_wifi_ssid"
        const val KEY_CONNECTION_MODE = "connection_mode"
        const val KEY_SETUP_COMPLETED = "setup_completed"
        const val KEY_AUTH_TOKEN = "auth_token"

        const val MODE_AUTO = "AUTO"
        const val MODE_LOCAL = "LOCAL"
        const val MODE_REMOTE = "REMOTE"

        const val DEFAULT_LOCAL_SERVER_URL = "http://192.168.1.250:8000"
        const val DEFAULT_REMOTE_SERVER_URL = "http://100.65.78.92:8000"
    }
}
