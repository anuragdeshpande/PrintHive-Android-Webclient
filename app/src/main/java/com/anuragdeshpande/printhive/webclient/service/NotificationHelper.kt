package com.anuragdeshpande.printhive.webclient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import com.anuragdeshpande.printhive.webclient.MainActivity

object NotificationHelper {
    const val CHANNEL_SERVICE = "printhive_service"
    const val CHANNEL_LIVE_PRINTS = "printhive_live_prints"
    const val CHANNEL_ALERTS = "printhive_alerts"
    const val NOTIFICATION_ID_SERVICE = 1000
    const val NOTIFICATION_ID_LIVE_PRINT = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "PrintHive Background Sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps print status synchronized in the background"
                setShowBadge(false)
            }

            val liveChannel = NotificationChannel(
                CHANNEL_LIVE_PRINTS,
                "Live Print Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Android 16+ Prominent Live Updates & Ongoing Print Status"
                setShowBadge(true)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Print Alerts & Completion",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for print completions, failures, filament warnings, and pauses"
                enableVibration(true)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(liveChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    /**
     * Minimal, silent notification required to keep the Android Foreground Service alive.
     */
    fun buildForegroundServiceNotification(context: Context): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("PrintHive Sync Active")
            .setContentText("Monitoring printer telemetry in background")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    /**
     * Clears the live print notification when printing stops or printer becomes idle.
     */
    fun dismissLivePrintNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_LIVE_PRINT)
    }

    /**
     * Checks if the ongoing live activity notification is currently visible.
     */
    fun isLiveActivityActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return manager.activeNotifications.any { it.id == NOTIFICATION_ID_LIVE_PRINT }
        }
        return false
    }

    /**
     * Builds an Android 16+ Prominent Live Update / Ongoing Interactive Notification
     * styled after modern lock-screen live activity widgets.
     */
    fun buildLivePrintNotification(
        context: Context,
        printerName: String,
        jobName: String,
        progressPercent: Int,
        timeRemainingText: String,
        layerInfo: String = "",
        printerId: Int = 1,
        isPaused: Boolean = false,
        pauseReason: String? = null,
        coverBitmap: Bitmap? = null
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause / Resume Broadcast Intent
        val pauseActionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = if (isPaused) NotificationActionReceiver.ACTION_RESUME_PRINT else NotificationActionReceiver.ACTION_PAUSE_PRINT
            putExtra(NotificationActionReceiver.EXTRA_PRINTER_ID, printerId)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            pauseActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Broadcast Intent
        val stopActionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP_PRINT
            putExtra(NotificationActionReceiver.EXTRA_PRINTER_ID, printerId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Content Formatting - Preserves the exact original layout & styling
        val titleText = if (isPaused) {
            "🖨️ $printerName • Paused ($progressPercent%)"
        } else {
            "🖨️ $printerName • $progressPercent%"
        }

        val subText = if (isPaused) {
            if (!pauseReason.isNullOrBlank()) "Paused: $pauseReason" else "Paused"
        } else {
            if (timeRemainingText.isNotBlank()) "$timeRemainingText remaining" else "Active Print"
        }

        val expandedText = buildString {
            append("Job: ").append(jobName)
            if (layerInfo.isNotBlank()) append("\nLayer: ").append(layerInfo)
            if (isPaused && !pauseReason.isNullOrBlank()) append("\nReason: ").append(pauseReason)
            if (timeRemainingText.isNotBlank() && !isPaused) append(" • ETA ").append(timeRemainingText)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE_PRINTS)
            .setSmallIcon(if (isPaused) android.R.drawable.ic_media_pause else android.R.drawable.stat_sys_upload)
            .setContentTitle(titleText)
            .setContentText("$jobName • $subText")
            .setSubText(subText)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open App",
                openPendingIntent
            )

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

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

    /**
     * Shows a dedicated high-priority alert notification when a print is paused
     * and no live activity is currently active. Includes reason and camera snapshot if available.
     */
    fun showPrintPausedNotification(
        context: Context,
        notificationId: Int,
        printerId: Int,
        printerName: String,
        jobName: String,
        pauseReason: String,
        snapshotBitmap: Bitmap? = null
    ) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Resume Broadcast Intent
        val resumeActionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_RESUME_PRINT
            putExtra(NotificationActionReceiver.EXTRA_PRINTER_ID, printerId)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 10,
            resumeActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Broadcast Intent
        val stopActionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP_PRINT
            putExtra(NotificationActionReceiver.EXTRA_PRINTER_ID, printerId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 20,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⏸️ Print Paused - $printerName"
        val message = if (pauseReason.isNotBlank()) {
            "$jobName paused: $pauseReason"
        } else {
            "$jobName has been paused."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open App",
                openPendingIntent
            )

        if (snapshotBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(snapshotBitmap)
                    .setSummaryText(message)
            )
            builder.setLargeIcon(snapshotBitmap)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }
}
