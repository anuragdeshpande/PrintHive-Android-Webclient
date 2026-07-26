package com.anuragdeshpande.printhive.webclient.data

import android.content.Context
import android.content.SharedPreferences

class ServerPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("printhive_webclient_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            val normalized = value.trim().removeSuffix("/")
            prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value.trim()).apply()
        }

    val webSocketUrl: String
        get() {
            val url = serverUrl
            return if (url.startsWith("https://")) {
                url.replace("https://", "wss://") + "/api/v1/ws"
            } else {
                url.replace("http://", "ws://") + "/api/v1/ws"
            }
        }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        const val DEFAULT_SERVER_URL = "http://192.168.1.102:8000"
    }
}
