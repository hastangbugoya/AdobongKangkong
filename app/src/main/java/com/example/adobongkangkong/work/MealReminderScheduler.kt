package com.example.adobongkangkong.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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

    fun reschedule(
        context: Context,
        enabled: Boolean,
        startMinutes: Int,
        intervalMinutes: Int,
        endMinutes: Int
    ) {
        cancel(context)

        if (!enabled) return
        if (intervalMinutes <= 0) return
        if (endMinutes <= startMinutes) return

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
                    KEY_END_MINUTES to endMinutes
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
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
        val hour = minutes / 60
        val minute = minutes % 60
        return LocalTime.of(hour, minute)
    }
}