package com.gdelataillade.alarm.features

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.gdelataillade.alarm.alarm.AlarmReceiver
import com.gdelataillade.alarm.alarm.AlarmService
import com.gdelataillade.alarm.notification.NotificationOnKillService

class NotificationHandler(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "alarm_plugin_channel"
        private const val CHANNEL_NAME = "Alarm Notification"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    setSound(null, null)
                }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildAlarmNotification(
        id: Int,
        title: String,
        body: String,
        fullScreen: Boolean,
        snoozeEnabled: Boolean,
        extras: Bundle,
        snoozeLabel: String = "Snooze",
        dismissLabel: String = "Dismiss",
    ): Notification {
        val dismissIntent = Intent(context, AlarmReceiver::class.java)
        dismissIntent.action = AlarmService.STOP_ALARM_ACTION
        dismissIntent.putExtras(extras)
        val dismissPendingIntent =
            PendingIntent.getBroadcast(
                context,
                id,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val snoozeIntent = Intent(context, AlarmReceiver::class.java)
        snoozeIntent.action = AlarmService.SNOOZE_ALARM_ACTION
        snoozeIntent.putExtras(extras)
        val snoozePendingIntent =
            PendingIntent.getBroadcast(
                context,
                id,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notificationBuilder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(getNotificationIcon())
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setSound(null)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Add dismiss action
        notificationBuilder.addAction(
            0,
            dismissLabel,
            dismissPendingIntent,
        )

        if (snoozeEnabled) {
            // Add snooze action
            notificationBuilder.addAction(
                0,
                snoozeLabel,
                snoozePendingIntent,
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notificationBuilder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (fullScreen) {
            notificationBuilder.setFullScreenIntent(pendingIntent, true)
        }

        return notificationBuilder.build()
    }

    fun buildNotification(
        title: String,
        body: String,
        expirationDurationInSeconds: Int,
        deeplinkUrl: String?,
    ): Notification {
        val intent =
            if (deeplinkUrl != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse(deeplinkUrl)).apply {
                    `package` = context.packageName
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(context.packageName)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notificationBuilder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(getNotificationIcon())
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .setSound(null)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(expirationDurationInSeconds * 1000L)

        return notificationBuilder.build()
    }

    fun startNotificationOnKillService(
        title: String,
        body: String,
    ) {
        val serviceIntent = Intent(context, NotificationOnKillService::class.java)
        serviceIntent.putExtra("title", title)
        serviceIntent.putExtra("body", body)
        context.startService(serviceIntent)
    }

    fun stopNotificationOnKillService() {
        val serviceIntent = Intent(context, NotificationOnKillService::class.java)
        context.stopService(serviceIntent)
    }

    private fun getNotificationIcon(): Int {
        val resourceName =
            if (context.resources.getIdentifier("ic_notification", "mipmap", context.packageName) != 0) {
                "ic_notification"
            } else {
                "ic_launcher"
            }

        return context.resources.getIdentifier(resourceName, "mipmap", context.packageName)
    }
}
