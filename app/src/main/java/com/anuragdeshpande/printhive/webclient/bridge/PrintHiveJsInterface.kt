package com.anuragdeshpande.printhive.webclient.bridge

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.anuragdeshpande.printhive.webclient.data.ServerPreferences
import org.json.JSONObject

class PrintHiveJsInterface(
    private val context: Context,
    private val onScanNfcRequested: () -> Unit
) {
    private val prefs = ServerPreferences(context)

    @JavascriptInterface
    fun getDeviceInfo(): String {
        val info = JSONObject().apply {
            put("platform", "android")
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("appVersion", "1.0.0-webclient")
        }
        return info.toString()
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun triggerVibration(durationMs: Long = 100) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    @JavascriptInterface
    fun scanNfc() {
        onScanNfcRequested()
    }

    @JavascriptInterface
    fun getServerUrl(): String {
        return prefs.serverUrl
    }

    @JavascriptInterface
    fun setAuthToken(token: String?) {
        prefs.authToken = token
    }

    @JavascriptInterface
    fun getAuthToken(): String {
        return prefs.authToken ?: ""
    }
}
