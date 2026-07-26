package com.anuragdeshpande.printhive.webclient.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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

    override fun onCreate() {
        super.onCreate()
        prefs = ServerPreferences(this)
        startForegroundServiceNotification()
        connectWebSocket()
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
            printerName = "PrintHive",
            jobName = "Monitoring active printers",
            progressPercent = 0,
            timeRemainingText = "Ready"
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

    private fun handleWebSocketMessage(jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val eventType = json.optString("type", "")

            when (eventType) {
                "print_progress", "printer_status_update" -> {
                    val printerName = json.optString("printer_name", "3D Printer")
                    val jobName = json.optString("job_name", "Printing model")
                    val progress = json.optInt("progress", 0)
                    val timeRemaining = json.optString("time_remaining", "")
                    val layerInfo = json.optString("layer_info", "")

                    if (progress > 0 && progress < 100) {
                        val updatedNotification = NotificationHelper.buildLivePrintNotification(
                            context = this,
                            printerName = printerName,
                            jobName = jobName,
                            progressPercent = progress,
                            timeRemainingText = timeRemaining,
                            layerInfo = layerInfo
                        )
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        manager.notify(NotificationHelper.NOTIFICATION_ID_LIVE_PRINT, updatedNotification)
                    }
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

    override fun onDestroy() {
        super.onDestroy()
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
