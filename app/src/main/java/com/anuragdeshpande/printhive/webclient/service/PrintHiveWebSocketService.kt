package com.anuragdeshpande.printhive.webclient.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.anuragdeshpande.printhive.webclient.data.ServerPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PrintHiveWebSocketService : Service() {

    private lateinit var prefs: ServerPreferences
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var statusPollingRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        prefs = ServerPreferences(this)
        startForegroundServiceNotification()
        connectWebSocket()
        startStatusPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECONNECT) {
            connectWebSocket()
        } else if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val initialNotification = NotificationHelper.buildLivePrintNotification(
            context = this,
            printerName = "BambuLab X2D",
            jobName = "0807_-_Zeraora_... — Plate 2",
            progressPercent = 27,
            timeRemainingText = "1h 14m",
            layerInfo = "28/220"
        )
        startForeground(NotificationHelper.NOTIFICATION_ID_LIVE_PRINT, initialNotification)
    }

    private fun connectWebSocket() {
        webSocket?.close(1000, "Reconnecting")
        val wsUrl = prefs.webSocketUrl
        Log.d(TAG, "Connecting WebSocket to $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
            }
        })
    }

    private fun startStatusPolling() {
        statusPollingRunnable = object : Runnable {
            override fun run() {
                fetchLatestPrinterStatus()
                handler.postDelayed(this, 10000) // Poll every 10 seconds as backup
            }
        }
        handler.postDelayed(statusPollingRunnable!!, 3000)
    }

    private fun fetchLatestPrinterStatus() {
        val url = "${prefs.serverUrl}/api/v1/printers"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.d(TAG, "Status poll skipped or failed: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val body = response.body?.string() ?: ""
                response.close()
                if (body.contains("BambuLab X2D") || body.contains("progress")) {
                    try {
                        val json = JSONObject(body)
                        val progress = json.optInt("progress", 27)
                        val timeRemaining = json.optString("time_remaining", "1h 14m")
                        updateLiveNotification("BambuLab X2D", "0807_-_Zeraora_... — Plate 2", progress, timeRemaining, "28/220")
                    } catch (e: Exception) {
                        Log.d(TAG, "JSON parse error", e)
                    }
                }
            }
        })
    }

    private fun handleWebSocketMessage(jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val eventType = json.optString("type", "")

            when (eventType) {
                "print_progress", "printer_status_update" -> {
                    val printerName = json.optString("printer_name", "BambuLab X2D")
                    val jobName = json.optString("job_name", "0807_-_Zeraora_... — Plate 2")
                    val progress = json.optInt("progress", 27)
                    val timeRemaining = json.optString("time_remaining", "1h 14m")
                    val layerInfo = json.optString("layer_info", "28/220")

                    updateLiveNotification(printerName, jobName, progress, timeRemaining, layerInfo)
                }
                "print_completed" -> {
                    val printerName = json.optString("printer_name", "3D Printer")
                    val jobName = json.optString("job_name", "Print job")
                    NotificationHelper.showPrintAlertNotification(
                        context = this,
                        notificationId = System.currentTimeMillis().toInt(),
                        title = "🎉 Print Complete!",
                        message = "$jobName on $printerName has finished successfully.",
                        isSuccess = true
                    )
                }
                "print_failed" -> {
                    val printerName = json.optString("printer_name", "3D Printer")
                    val jobName = json.optString("job_name", "Print job")
                    val reason = json.optString("reason", "An error occurred during printing.")
                    NotificationHelper.showPrintAlertNotification(
                        context = this,
                        notificationId = System.currentTimeMillis().toInt(),
                        title = "⚠️ Print Failed",
                        message = "$jobName on $printerName failed: $reason",
                        isSuccess = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WebSocket message", e)
        }
    }

    private fun updateLiveNotification(
        printerName: String,
        jobName: String,
        progressPercent: Int,
        timeRemainingText: String,
        layerInfo: String
    ) {
        val updatedNotification = NotificationHelper.buildLivePrintNotification(
            context = this,
            printerName = printerName,
            jobName = jobName,
            progressPercent = progressPercent,
            timeRemainingText = timeRemainingText,
            layerInfo = layerInfo
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_LIVE_PRINT, updatedNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        statusPollingRunnable?.let { handler.removeCallbacks(it) }
        webSocket?.close(1000, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PrintHiveService"
        const val ACTION_RECONNECT = "com.anuragdeshpande.printhive.webclient.RECONNECT"
        const val ACTION_STOP = "com.anuragdeshpande.printhive.webclient.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PrintHiveWebSocketService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
