package com.example.adobongkangkong

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.data.local.db.seed.SeedStoresUseCase
import com.example.adobongkangkong.work.OrphanFoodMediaCleanupScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AdobongKangkongApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // 🔥 Inject seeder
    @Inject lateinit var seedStoresUseCase: SeedStoresUseCase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        OrphanFoodMediaCleanupScheduler.schedule(this)
        MeowLog.init(this)

        // 🔥 Run store seeding (non-blocking)
        CoroutineScope(Dispatchers.IO).launch {
            seedStoresUseCase()
        }
    }
}