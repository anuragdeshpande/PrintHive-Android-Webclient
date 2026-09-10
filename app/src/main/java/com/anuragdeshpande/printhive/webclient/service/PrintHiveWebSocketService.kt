package com.anuragdeshpande.printhive.webclient.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.anuragdeshpande.printhive.webclient.data.ServerPreferences
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
    private var lastPausedPrinterState: MutableMap<Int, Boolean> = mutableMapOf()
    private val coverBitmaps: MutableMap<Int, Bitmap> = mutableMapOf()
    private val coverJobNames: MutableMap<Int, String> = mutableMapOf()

    override fun onCreate() {
        super.onCreate()
        prefs = ServerPreferences(this)
        NotificationHelper.createNotificationChannels(this)
        startForegroundServiceNotification()
        connectWebSocket()
        startStatusPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECONNECT) {
            connectWebSocket()
            fetchLatestPrinterStatus()
        } else if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val serviceNotification = NotificationHelper.buildForegroundServiceNotification(this)
        startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, serviceNotification)
    }

    private fun connectWebSocket() {
        webSocket?.close(1000, "Reconnecting")
        val token = prefs.authToken
        val baseUrl = prefs.serverUrl

        if (!token.isNullOrBlank()) {
            val wsTokenReq = Request.Builder()
                .url("$baseUrl/api/v1/auth/ws-token")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(wsTokenReq).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "WS token request failed, connecting directly: ${e.message}")
                    openWebSocketConnection(prefs.webSocketUrl)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    response.close()
                    val wsToken = try {
                        JSONObject(body).optString("token", "")
                    } catch (e: Exception) {
                        ""
                    }
                    val finalWsUrl = if (wsToken.isNotBlank()) {
                        "${prefs.webSocketUrl}?token=$wsToken"
                    } else {
                        prefs.webSocketUrl
                    }
                    openWebSocketConnection(finalWsUrl)
                }
            })
        } else {
            openWebSocketConnection(prefs.webSocketUrl)
        }
    }

    private fun attemptTokenRefresh() {
        val token = prefs.authToken
        if (token.isNullOrBlank()) return
        val baseUrl = prefs.serverUrl
        val refreshReq = Request.Builder()
            .url("$baseUrl/api/v1/auth/refresh")
            .addHeader("Authorization", "Bearer $token")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(refreshReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Token refresh request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                response.close()
                if (response.isSuccessful) {
                    try {
                        val newToken = JSONObject(body).optString("access_token", "")
                        if (newToken.isNotBlank()) {
                            prefs.authToken = newToken
                            Log.d(TAG, "Token auto-renewed successfully")
                            handler.post {
                                connectWebSocket()
                                fetchLatestPrinterStatus()
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "JSON parse error in token refresh", e)
                    }
                }
            }
        })
    }

    private fun openWebSocketConnection(wsUrl: String) {
        Log.d(TAG, "Connecting WebSocket to $wsUrl")
        val requestBuilder = Request.Builder().url(wsUrl)
        val token = prefs.authToken
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
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
                handler.postDelayed(this, 8000)
            }
        }
        handler.postDelayed(statusPollingRunnable!!, 1000)
    }

    private fun fetchLatestPrinterStatus() {
        val url = "${prefs.serverUrl}/api/v1/printers"
        val requestBuilder = Request.Builder().url(url).get()
        val token = prefs.authToken
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Status poll skipped or failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val body = response.body?.string() ?: ""
                response.close()

                if (code == 401) {
                    attemptTokenRefresh()
                    return
                }

                if (body.isNotBlank()) {
                    try {
                        var anyActive = false
                        if (body.trim().startsWith("[")) {
                            val array = JSONArray(body)
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                if (processPrinterStatusJson(item)) {
                                    anyActive = true
                                }
                            }
                        } else if (body.trim().startsWith("{")) {
                            val json = JSONObject(body)
                            if (processPrinterStatusJson(json)) {
                                anyActive = true
                            }
                        }
                        if (!anyActive) {
                            handler.post {
                                NotificationHelper.dismissLivePrintNotification(this@PrintHiveWebSocketService)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "JSON parse error in status poll", e)
                    }
                }
            }
        })
    }

    private fun processPrinterStatusJson(dataObj: JSONObject): Boolean {
        val rawState = dataObj.optString("state", dataObj.optString("gcode_state", "IDLE")).uppercase()
        val isPrinting = rawState == "RUNNING" || rawState == "PRINTING"
        val isPaused = rawState == "PAUSE" || rawState == "PAUSED"

        if (!isPrinting && !isPaused) {
            return false
        }

        val printerId = dataObj.optInt("id", dataObj.optInt("printer_id", 1))
        val printerName = dataObj.optString("name", dataObj.optString("printer_name", "Printer #$printerId"))
        val jobName = dataObj.optString("subtask_name", dataObj.optString("current_print", dataObj.optString("gcode_file", "Active Print")))
        val progress = dataObj.optInt("progress", dataObj.optInt("mc_percent", 0))
        val remainingMinutes = dataObj.optInt("remaining_time", dataObj.optInt("mc_remaining_time", 0))
        val timeRemaining = if (remainingMinutes > 0) {
            "${remainingMinutes / 60}h ${remainingMinutes % 60}m"
        } else {
            dataObj.optString("time_remaining", "")
        }
        val layerNum = dataObj.optInt("layer_num", 0)
        val totalLayers = dataObj.optInt("total_layers", 0)
        val layerInfo = if (totalLayers > 0) "$layerNum/$totalLayers" else ""

        handlePrinterStatus(printerId, printerName, jobName, progress, timeRemaining, layerInfo, rawState, dataObj)
        return true
    }

    private fun handleWebSocketMessage(jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val eventType = json.optString("type", "")

            when (eventType) {
                "printer_status" -> {
                    val printerId = json.optInt("printer_id", 1)
                    val dataObj = json.optJSONObject("data") ?: JSONObject()
                    val rawState = dataObj.optString("state", dataObj.optString("gcode_state", "IDLE")).uppercase()
                    val isPrinting = rawState == "RUNNING" || rawState == "PRINTING"
                    val isPaused = rawState == "PAUSE" || rawState == "PAUSED"

                    if (isPrinting || isPaused) {
                        val printerName = dataObj.optString("name", dataObj.optString("printer_name", "Printer #$printerId"))
                        val jobName = dataObj.optString("subtask_name", dataObj.optString("current_print", dataObj.optString("gcode_file", "Active Print")))
                        val progress = dataObj.optInt("progress", dataObj.optInt("mc_percent", 0))
                        val remainingMinutes = dataObj.optInt("remaining_time", dataObj.optInt("mc_remaining_time", 0))
                        val timeRemaining = if (remainingMinutes > 0) "${remainingMinutes / 60}h ${remainingMinutes % 60}m" else dataObj.optString("time_remaining", "")
                        val layerNum = dataObj.optInt("layer_num", 0)
                        val totalLayers = dataObj.optInt("total_layers", 0)
                        val layerInfo = if (totalLayers > 0) "$layerNum/$totalLayers" else ""
                        handlePrinterStatus(printerId, printerName, jobName, progress, timeRemaining, layerInfo, rawState, dataObj)
                    } else {
                        handler.post {
                            NotificationHelper.dismissLivePrintNotification(this@PrintHiveWebSocketService)
                        }
                    }
                }
                "print_progress", "printer_status_update" -> {
                    val printerId = json.optInt("printer_id", 1)
                    val rawState = json.optString("state", "RUNNING").uppercase()
                    val printerName = json.optString("printer_name", json.optString("name", "Printer #$printerId"))
                    val jobName = json.optString("job_name", json.optString("subtask_name", "Active Print"))
                    val progress = json.optInt("progress", 0)
                    val timeRemaining = json.optString("time_remaining", "")
                    val layerInfo = json.optString("layer_info", "")

                    handlePrinterStatus(printerId, printerName, jobName, progress, timeRemaining, layerInfo, rawState, json)
                }
                "print_paused" -> {
                    val printerId = json.optInt("printer_id", 1)
                    val printerName = json.optString("printer_name", json.optString("name", "Printer #$printerId"))
                    val jobName = json.optString("job_name", json.optString("subtask_name", "Active Print"))
                    val progress = json.optInt("progress", 0)
                    val timeRemaining = json.optString("time_remaining", "")
                    val layerInfo = json.optString("layer_info", "")

                    handlePrinterStatus(printerId, printerName, jobName, progress, timeRemaining, layerInfo, "PAUSED", json)
                }
                "print_completed" -> {
                    handler.post {
                        NotificationHelper.dismissLivePrintNotification(this@PrintHiveWebSocketService)
                    }
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
                    handler.post {
                        NotificationHelper.dismissLivePrintNotification(this@PrintHiveWebSocketService)
                    }
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

    private fun handlePrinterStatus(
        printerId: Int,
        printerName: String,
        jobName: String,
        progress: Int,
        timeRemaining: String,
        layerInfo: String,
        state: String,
        dataJson: JSONObject
    ) {
        val isPaused = state.equals("PAUSE", ignoreCase = true) || state.equals("PAUSED", ignoreCase = true)
        lastPausedPrinterState[printerId] = isPaused

        ensureCoverImageLoaded(printerId, jobName) { loadedCover ->
            val currentCover = loadedCover ?: coverBitmaps[printerId]

            if (isPaused) {
                val pauseReason = extractPauseReason(dataJson)
                val isLiveActive = NotificationHelper.isLiveActivityActive(this)

                fetchCameraSnapshot(printerId) { snapshotBitmap ->
                    val imageToUse = snapshotBitmap ?: currentCover
                    handler.post {
                        if (isLiveActive) {
                            updateLiveNotification(
                                printerName = printerName,
                                jobName = jobName,
                                progressPercent = progress,
                                timeRemainingText = timeRemaining,
                                layerInfo = layerInfo,
                                printerId = printerId,
                                isPaused = true,
                                pauseReason = pauseReason,
                                coverBitmap = imageToUse
                            )
                        } else {
                            NotificationHelper.showPrintPausedNotification(
                                context = this,
                                notificationId = NotificationHelper.NOTIFICATION_ID_LIVE_PRINT + printerId,
                                printerId = printerId,
                                printerName = printerName,
                                jobName = jobName,
                                pauseReason = pauseReason,
                                snapshotBitmap = imageToUse
                            )
                        }
                    }
                }
            } else {
                handler.post {
                    updateLiveNotification(
                        printerName = printerName,
                        jobName = jobName,
                        progressPercent = progress,
                        timeRemainingText = timeRemaining,
                        layerInfo = layerInfo,
                        printerId = printerId,
                        isPaused = false,
                        pauseReason = null,
                        coverBitmap = currentCover
                    )
                }
            }
        }
    }

    private fun ensureCoverImageLoaded(printerId: Int, jobName: String, onLoaded: (Bitmap?) -> Unit) {
        val existing = coverBitmaps[printerId]
        val existingJob = coverJobNames[printerId]
        if (existing != null && existingJob == jobName) {
            onLoaded(existing)
            return
        }

        fetchCoverFallback(printerId) { bitmap ->
            if (bitmap != null) {
                coverBitmaps[printerId] = bitmap
                coverJobNames[printerId] = jobName
            }
            onLoaded(bitmap ?: existing)
        }
    }

    private fun extractPauseReason(dataJson: JSONObject): String {
        val directReason = dataJson.optString("reason", "").ifBlank {
            dataJson.optString("pause_reason", "")
        }
        if (directReason.isNotBlank()) return directReason

        val hmsErrors = dataJson.optJSONArray("hms_errors")
        if (hmsErrors != null && hmsErrors.length() > 0) {
            val errorReasons = mutableListOf<String>()
            for (i in 0 until hmsErrors.length()) {
                val err = hmsErrors.optJSONObject(i) ?: continue
                val actions = err.optString("actions", "")
                val fullCode = err.optString("full_code", "")
                val code = err.optString("code", "")
                val desc = when {
                    actions.isNotBlank() -> actions
                    fullCode.isNotBlank() -> "HMS Error: $fullCode"
                    code.isNotBlank() -> "Error: $code"
                    else -> null
                }
                if (desc != null && !errorReasons.contains(desc)) {
                    errorReasons.add(desc)
                }
            }
            if (errorReasons.isNotEmpty()) {
                return errorReasons.joinToString("; ")
            }
        }

        if (dataJson.has("expected_tray") && !dataJson.isNull("expected_tray")) {
            val tray = dataJson.optInt("expected_tray", -1)
            if (tray >= 0) {
                return "Filament runout: slot #${tray + 1} required"
            }
        }

        val stgName = dataJson.optString("stg_cur_name", "")
        if (stgName.isNotBlank() && !stgName.equals("Paused", ignoreCase = true) && !stgName.equals("Pause", ignoreCase = true)) {
            return stgName
        }

        if (dataJson.optBoolean("door_open", false)) {
            return "Chamber door opened"
        }

        return "Print paused by printer or user"
    }

    private fun fetchCameraSnapshot(printerId: Int, onResult: (Bitmap?) -> Unit) {
        val snapshotUrl = "${prefs.serverUrl}/api/v1/printers/$printerId/camera/snapshot"
        val requestBuilder = Request.Builder().url(snapshotUrl).get()
        val token = prefs.authToken
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Snapshot fetch failed, using cover fallback: ${e.message}")
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val stream = response.body?.byteStream()
                    val bitmap = stream?.use { BitmapFactory.decodeStream(it) }
                    response.close()
                    onResult(bitmap)
                } else {
                    response.close()
                    onResult(null)
                }
            }
        })
    }

    private fun fetchCoverFallback(printerId: Int, onResult: (Bitmap?) -> Unit) {
        val coverUrl = "${prefs.serverUrl}/api/v1/printers/$printerId/cover"
        val requestBuilder = Request.Builder().url(coverUrl).get()
        val token = prefs.authToken
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Cover fetch fallback failed: ${e.message}")
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val stream = response.body?.byteStream()
                    val bitmap = stream?.use { BitmapFactory.decodeStream(it) }
                    response.close()
                    onResult(bitmap)
                } else {
                    response.close()
                    onResult(null)
                }
            }
        })
    }

    private fun updateLiveNotification(
        printerName: String,
        jobName: String,
        progressPercent: Int,
        timeRemainingText: String,
        layerInfo: String,
        printerId: Int = 1,
        isPaused: Boolean = false,
        pauseReason: String? = null,
        coverBitmap: Bitmap? = null
    ) {
        val updatedNotification = NotificationHelper.buildLivePrintNotification(
            context = this,
            printerName = printerName,
            jobName = jobName,
            progressPercent = progressPercent,
            timeRemainingText = timeRemainingText,
            layerInfo = layerInfo,
            printerId = printerId,
            isPaused = isPaused,
            pauseReason = pauseReason,
            coverBitmap = coverBitmap
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

        fun reconnect(context: Context) {
            val intent = Intent(context, PrintHiveWebSocketService::class.java).apply {
                action = ACTION_RECONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
