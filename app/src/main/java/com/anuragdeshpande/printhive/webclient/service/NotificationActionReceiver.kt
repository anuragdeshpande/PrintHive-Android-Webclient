package com.anuragdeshpande.printhive.webclient.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.anuragdeshpande.printhive.webclient.data.ServerPreferences
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class NotificationActionReceiver : BroadcastReceiver() {

    private val client = OkHttpClient()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val printerId = intent.getIntExtra(EXTRA_PRINTER_ID, 1)
        val prefs = ServerPreferences(context)
        val baseUrl = prefs.serverUrl

        Log.d(TAG, "Notification Action Triggered: $action for Printer #$printerId")

        when (action) {
            ACTION_PAUSE_PRINT -> {
                Toast.makeText(context, "Sending Pause command...", Toast.LENGTH_SHORT).show()
                sendPrinterControlCommand(context, baseUrl, printerId, "pause")
            }
            ACTION_STOP_PRINT -> {
                Toast.makeText(context, "Sending Stop command...", Toast.LENGTH_SHORT).show()
                sendPrinterControlCommand(context, baseUrl, printerId, "stop")
            }
            ACTION_RESUME_PRINT -> {
                Toast.makeText(context, "Sending Resume command...", Toast.LENGTH_SHORT).show()
                sendPrinterControlCommand(context, baseUrl, printerId, "resume")
            }
        }
    }

    private fun sendPrinterControlCommand(context: Context, baseUrl: String, printerId: Int, command: String) {
        val prefs = ServerPreferences(context)
        val url = "$baseUrl/api/v1/printers/$printerId/$command"
        val requestBuilder = Request.Builder()
            .url(url)
            .post("{}".toRequestBody("application/json".toMediaType()))

        val token = prefs.authToken
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send $command command: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "$command command response: ${response.code}")
                response.close()
            }
        })
    }

    companion object {
        private const val TAG = "NotifActionReceiver"
        const val ACTION_PAUSE_PRINT = "com.anuragdeshpande.printhive.webclient.ACTION_PAUSE_PRINT"
        const val ACTION_STOP_PRINT = "com.anuragdeshpande.printhive.webclient.ACTION_STOP_PRINT"
        const val ACTION_RESUME_PRINT = "com.anuragdeshpande.printhive.webclient.ACTION_RESUME_PRINT"
        const val EXTRA_PRINTER_ID = "extra_printer_id"
    }
}
