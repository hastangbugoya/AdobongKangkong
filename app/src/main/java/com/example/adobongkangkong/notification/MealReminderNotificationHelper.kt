package com.example.adobongkangkong.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.adobongkangkong.MainActivity
import com.example.adobongkangkong.R
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.domain.settings.MealReminderIntensity

object MealReminderNotificationHelper {

    const val CHANNEL_ID = "meal_reminders_gentle_channel"

    private const val SILENT_CHANNEL_ID = "meal_reminders_silent_channel"
    private const val GENTLE_CHANNEL_ID = "meal_reminders_gentle_channel"
    private const val NORMAL_CHANNEL_ID = "meal_reminders_normal_channel"

    private const val CHANNEL_DESCRIPTION = "Reminders to log your meals"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            MeowLog.d("MealReminderNotificationHelper> Skipping channel creation (SDK < O)")
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val silent = NotificationChannel(
            SILENT_CHANNEL_ID,
            "Meal Reminders - Silent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(false)
            setSound(null, null)
        }

        val gentle = NotificationChannel(
            GENTLE_CHANNEL_ID,
            "Meal Reminders - Gentle",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 100)
            setSound(null, null)
        }

        val normal = NotificationChannel(
            NORMAL_CHANNEL_ID,
            "Meal Reminders - Normal",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }

        manager.createNotificationChannel(silent)
        manager.createNotificationChannel(gentle)
        manager.createNotificationChannel(normal)

        MeowLog.d("MealReminderNotificationHelper> Notification channels created")
    }

    /**
     * Backward-compatible wrapper.
     *
     * Existing callers can keep using this while newer reminder code should prefer
     * [createNotificationChannels] because intensity uses separate immutable channels.
     */
    fun createNotificationChannel(context: Context) {
        createNotificationChannels(context)
    }

    fun buildNotification(
        context: Context,
        intensity: MealReminderIntensity = MealReminderIntensity.GENTLE
    ): Notification {
        MeowLog.d("MealReminderNotificationHelper> Building notification intensity=$intensity")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = channelIdFor(intensity)

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AK Reminder")
            .setContentText("Have any meals to log?")
            .setPriority(priorityFor(intensity))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    fun showNotification(
        context: Context,
        intensity: MealReminderIntensity = MealReminderIntensity.GENTLE
    ) {
        MeowLog.d("MealReminderNotificationHelper> Showing notification intensity=$intensity")

        createNotificationChannels(context)

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = buildNotification(context, intensity)
        val notificationId = System.currentTimeMillis().toInt()

        manager.notify(notificationId, notification)

        MeowLog.d("MealReminderNotificationHelper> Notification shown id=$notificationId intensity=$intensity")
    }

    private fun channelIdFor(intensity: MealReminderIntensity): String {
        return when (intensity) {
            MealReminderIntensity.SILENT -> SILENT_CHANNEL_ID
            MealReminderIntensity.GENTLE -> GENTLE_CHANNEL_ID
            MealReminderIntensity.NORMAL -> NORMAL_CHANNEL_ID
        }
    }

    private fun priorityFor(intensity: MealReminderIntensity): Int {
        return when (intensity) {
            MealReminderIntensity.SILENT -> NotificationCompat.PRIORITY_LOW
            MealReminderIntensity.GENTLE -> NotificationCompat.PRIORITY_LOW
            MealReminderIntensity.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
        }
    }
}