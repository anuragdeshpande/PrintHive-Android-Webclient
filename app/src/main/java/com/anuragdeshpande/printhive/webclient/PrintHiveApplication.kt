package com.anuragdeshpande.printhive.webclient

import android.app.Application
import com.anuragdeshpande.printhive.webclient.service.NotificationHelper

class PrintHiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }
}
