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

object MealReminderNotificationHelper {

    const val CHANNEL_ID = "meal_reminders_channel_v2"
    private const val CHANNEL_NAME = "Meal Reminders"
    private const val CHANNEL_DESCRIPTION = "Reminders to log your meals"

    private val GENTLE_VIBRATION_PATTERN = longArrayOf(0L, 80L)

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            MeowLog.d("Creating notification channel: $CHANNEL_ID")

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = GENTLE_VIBRATION_PATTERN
            }

            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(channel)

            MeowLog.d("Notification channel created successfully")
        } else {
            MeowLog.d("Skipping channel creation (SDK < O)")
        }
    }

    fun buildNotification(context: Context): Notification {
        MeowLog.d("Building meal reminder notification")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        MeowLog.d("Intent created for MainActivity")

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        MeowLog.d("PendingIntent created")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AK Reminder")
            .setContentText("Have any meals to log?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(GENTLE_VIBRATION_PATTERN)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        MeowLog.d("Notification built successfully")

        return notification
    }

    fun showNotification(context: Context) {
        MeowLog.d("Showing meal reminder notification")

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = buildNotification(context)

        val notificationId = System.currentTimeMillis().toInt()

        manager.notify(notificationId, notification)

        MeowLog.d("Notification shown (id=$notificationId)")
    }
}