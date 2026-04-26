package com.example.adobongkangkong

import android.app.Application
import android.os.Build
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.notification.MealReminderNotificationHelper
import com.example.adobongkangkong.work.OrphanFoodMediaCleanupScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AdobongKangkongApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        MeowLog.init(this)



        MealReminderNotificationHelper.createNotificationChannel(this)

        OrphanFoodMediaCleanupScheduler.schedule(this)
    }
}