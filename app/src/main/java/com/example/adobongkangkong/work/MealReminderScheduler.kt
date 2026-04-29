package com.example.adobongkangkong.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.domain.settings.MealReminderIntensity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

object MealReminderScheduler {

    private const val WORK_NAME = "meal_reminders"

    const val KEY_ENABLED = "enabled"
    const val KEY_START_MINUTES = "start_minutes"
    const val KEY_INTERVAL_MINUTES = "interval_minutes"
    const val KEY_END_MINUTES = "end_minutes"
    const val KEY_INTENSITY = "intensity"

    fun reschedule(
        context: Context,
        enabled: Boolean,
        startMinutes: Int,
        intervalMinutes: Int,
        endMinutes: Int,
        intensity: MealReminderIntensity = MealReminderIntensity.GENTLE
    ) {
        MeowLog.d(
            "MealReminderScheduler> reschedule START " +
                    "enabled=$enabled start=$startMinutes interval=$intervalMinutes end=$endMinutes intensity=$intensity"
        )

        cancel(context)

        if (!enabled) {
            MeowLog.d("MealReminderScheduler> reschedule skipped disabled")
            return
        }
        if (intervalMinutes <= 0) {
            MeowLog.d("MealReminderScheduler> reschedule skipped invalid interval=$intervalMinutes")
            return
        }
        if (endMinutes <= startMinutes) {
            MeowLog.d("MealReminderScheduler> reschedule skipped invalid window start=$startMinutes end=$endMinutes")
            return
        }

        val delayMinutes = calculateDelayUntilNextReminder(
            startMinutes = startMinutes,
            intervalMinutes = intervalMinutes,
            endMinutes = endMinutes
        )

        val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    KEY_ENABLED to enabled,
                    KEY_START_MINUTES to startMinutes,
                    KEY_INTERVAL_MINUTES to intervalMinutes,
                    KEY_END_MINUTES to endMinutes,
                    KEY_INTENSITY to intensity.name
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        MeowLog.d("MealReminderScheduler> reschedule SUCCESS delayMinutes=$delayMinutes intensity=$intensity")
    }

    fun cancel(context: Context) {
        MeowLog.d("MealReminderScheduler> cancel")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun calculateDelayUntilNextReminder(
        startMinutes: Int,
        intervalMinutes: Int,
        endMinutes: Int
    ): Long {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        val startTime = minutesToLocalTime(startMinutes)
        val endTime = minutesToLocalTime(endMinutes)

        var candidate = LocalDateTime.of(today, startTime)
        val endToday = LocalDateTime.of(today, endTime)

        while (candidate.isBefore(now)) {
            candidate = candidate.plusMinutes(intervalMinutes.toLong())
        }

        if (candidate.isAfter(endToday)) {
            candidate = LocalDateTime.of(today.plusDays(1), startTime)
        }

        return ChronoUnit.MINUTES.between(now, candidate).coerceAtLeast(0)
    }

    private fun minutesToLocalTime(minutes: Int): LocalTime {
        val safeMinutes = minutes.coerceIn(0, 23 * 60 + 59)
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60
        return LocalTime.of(hour, minute)
    }
}