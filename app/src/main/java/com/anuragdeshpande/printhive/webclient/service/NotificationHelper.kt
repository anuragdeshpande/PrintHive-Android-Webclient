package com.anuragdeshpande.printhive.webclient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.anuragdeshpande.printhive.webclient.MainActivity

object NotificationHelper {
    const val CHANNEL_LIVE_PRINTS = "printhive_live_prints"
    const val CHANNEL_ALERTS = "printhive_alerts"
    const val NOTIFICATION_ID_LIVE_PRINT = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val liveChannel = NotificationChannel(
                CHANNEL_LIVE_PRINTS,
                "Live Print Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing progress indicators for active 3D prints"
                setShowBadge(true)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Print Alerts & Completion",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for print completions, failures, and filament warnings"
                enableVibration(true)
            }

            manager.createNotificationChannel(liveChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    fun buildLivePrintNotification(
        context: Context,
        printerName: String,
        jobName: String,
        progressPercent: Int,
        timeRemainingText: String,
        layerInfo: String = ""
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = "🖨️ $printerName • $progressPercent%"
        val contentText = buildString {
            append(jobName)
            if (timeRemainingText.isNotBlank()) append(" • ").append(timeRemainingText)
            if (layerInfo.isNotBlank()) append(" (").append(layerInfo).append(")")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE_PRINTS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progressPercent.coerceIn(0, 100), false)

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    fun showPrintAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        isSuccess: Boolean
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    fun cancelLivePrintNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_LIVE_PRINT)
    }
}
