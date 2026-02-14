package com.example.adobongkangkong

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.adobongkangkong.core.log.MeowLog
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
        OrphanFoodMediaCleanupScheduler.schedule(this)
        MeowLog.init(this)
    }
}
