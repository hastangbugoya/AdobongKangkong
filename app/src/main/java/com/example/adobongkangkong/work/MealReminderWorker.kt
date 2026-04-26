package com.example.adobongkangkong.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.adobongkangkong.notification.MealReminderNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MealReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        MealReminderNotificationHelper.showNotification(applicationContext)

        MealReminderScheduler.reschedule(
            context = applicationContext,
            enabled = inputData.getBoolean(MealReminderScheduler.KEY_ENABLED, false),
            startMinutes = inputData.getInt(MealReminderScheduler.KEY_START_MINUTES, 8 * 60),
            intervalMinutes = inputData.getInt(MealReminderScheduler.KEY_INTERVAL_MINUTES, 3 * 60),
            endMinutes = inputData.getInt(MealReminderScheduler.KEY_END_MINUTES, 21 * 60)
        )

        return Result.success()
    }
}