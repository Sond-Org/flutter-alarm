package com.gdelataillade.alarm.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BedtimeNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val notificationHandler = NotificationHandler(context)
        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title")!!
        val body = intent.getStringExtra("body")!!
        val autoDismissSeconds = intent.getIntExtra("autoDismissSeconds", 120 * 60) ?: 120 * 60 // default to 2 hours
        val deeplinkUrl = intent.getStringExtra("deeplinkUri")

        val notification =
            notificationHandler.buildBedtimeNotification(
                title,
                body,
                autoDismissSeconds,
                deeplinkUrl,
            )
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)
    }
}
