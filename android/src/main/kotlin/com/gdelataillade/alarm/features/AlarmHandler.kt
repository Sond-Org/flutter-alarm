package com.gdelataillade.alarm.features

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.gdelataillade.alarm.alarm.AlarmReceiver
import com.gdelataillade.alarm.alarm.AlarmService
import com.gdelataillade.alarm.notification.BedtimeNotificationReceiver
import com.gdelataillade.alarm.utils.toBedtimeId
import com.gdelataillade.alarm.utils.toBundle
import com.gdelataillade.alarm.utils.toDateTimeString
import io.flutter.Log
import org.json.JSONObject

class AlarmHandler(private val context: Context) {
    private val storageHandler = StorageHandler(context)

    companion object {
        // Shared lock object for all instances of AlarmHandler
        private val lock = Any()
    }

    fun scheduleAlarm(alarm: JSONObject) {
        scheduleAlarm(toBundle(alarm))
    }

    fun scheduleAlarm(alarm: Bundle) {
        val id = alarm.getInt("id", -1)
        if (id == -1) {
            Log.d("flutter/AlarmHandler", "No id set")
            return
        }

        val scheduleTime = alarm.getLong("dateTime", -1L)
        if (scheduleTime == -1L) {
            Log.d("flutter/AlarmHandler", "No dateTime set")
            return
        }

        scheduleAlarm(scheduleTime, id, alarm)
    }

    fun scheduleAlarm(
        scheduleTime: Long,
        id: Int,
        alarm: Bundle,
    ) {
        val scheduleTimeFormatted = toDateTimeString(scheduleTime)
        Log.d("flutter/AlarmHandler", "Request to schedule alarm $id at $scheduleTimeFormatted")
        val delayInSeconds = ((scheduleTime - System.currentTimeMillis()) / 1000).toInt()
        val alarmIntent = createAlarmIntent(id, alarm)
        synchronized(lock) {
            if (delayInSeconds <= 5) {
                handleImmediateAlarm(alarmIntent, delayInSeconds)
            } else {
                handleDelayedAlarm(alarmIntent, scheduleTime, id)
            }
        }
        Log.d("flutter/AlarmHandler", "Alarm SCHEDULED at $scheduleTimeFormatted (in $delayInSeconds seconds)")
    }

    fun rescheduleAlarms() {
        synchronized(lock) {
            Log.d("flutter/AlarmHandler", "Rescheduling alarms")

            // Acquire a wakelock for 5 minutes, if possible
            val wakeLock =
                (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:AlarmWakelockTag")
                    ?.apply {
                        try {
                            acquire(5 * 60 * 1000L)
                        } catch (e: Exception) {
                            Log.d("flutter/AlarmHandler", "No activity in the foreground to acquire a wakelock")
                        }
                    }

            try {
                // Reschedule all alarms
                storageHandler.listAlarms().forEach { alarm ->
                    stopAlarm(alarm.getInt("id"))
                    scheduleAlarm(alarm)
                    scheduleBedtimeNotification(alarm)
                }
            } finally {
                // Ensure the wakelock is released even if an exception occurs
                wakeLock?.release()
            }
        }
    }

    private fun createAlarmIntent(
        id: Int,
        alarm: Bundle,
    ): Intent {
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.action = AlarmService.START_ALARM_ACTION
        intent.putExtras(alarm)
        return intent
    }

    private fun handleImmediateAlarm(
        intent: Intent,
        delayInSeconds: Int,
    ) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            context.sendBroadcast(intent)
        }, delayInSeconds * 1000L)
    }

    private fun handleDelayedAlarm(
        intent: Intent,
        scheduleTime: Long,
        id: Int,
    ) {
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val  = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingOpenAppIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val info = AlarmManager.AlarmClockInfo(scheduleTime, pendingOpenAppIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduleTime, pendingIntent)
        }
    }

    fun stopAlarm(id: Int) {
        synchronized(lock) {
            if (AlarmService.ringingAlarmIds.contains(id)) {
                // Stop active alarm
                Log.d("flutter/AlarmHandler", "Stopping ACTIVE alarm $id")
                val stopIntent = Intent(context, AlarmService::class.java)
                stopIntent.action = AlarmService.STOP_ALARM_ACTION
                val alarm = storageHandler.getAlarm(id)
                if (alarm == null) {
                    Log.d("flutter/AlarmHandler", "Alarm $id not found. Cannot schedule a recurring alarm!")
                    stopIntent.putExtra("id", id)
                } else {
                    val extras = toBundle(alarm!!)
                    stopIntent.putExtras(extras)
                }

                context.startService(stopIntent)
            }

            // Stop future alarm, if it is set
            Log.d("flutter/AlarmHandler", "Stopping FUTURE alarm $id, if it is set")
            cancelFutureAlarm(id)

            // Cancel future bedtime notification, if it is set
            cancelBedtimeNotification(id)
        }
    }

    fun snoozeAlarm(alarm: Bundle) {
        Log.d("flutter/AlarmHandler", "Snoozing alarm ${alarm.getInt("id")}")
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.action = AlarmService.SNOOZE_ALARM_ACTION
        intent.putExtras(alarm)
        context.sendBroadcast(intent)
    }

    private fun cancelFutureAlarm(id: Int) {
        val alarmIntent = Intent(context, AlarmReceiver::class.java)
        alarmIntent.action = AlarmService.START_ALARM_ACTION
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                id,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // Cancel alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        // Cancel pending intent
        pendingIntent.cancel()
    }

    fun scheduleBedtimeNotification(alarm: JSONObject) {
        scheduleBedtimeNotification(toBundle(alarm))
    }

    fun scheduleBedtimeNotification(alarm: Bundle) {
        val id = alarm.getInt("id")
        val bedtime = alarm.getLong("bedtime", -1L)
        if (bedtime == -1L) {
            Log.d("flutter/AlarmHandler", "No bedtime set")
            return
        }

        val title = alarm.getString("bedtimeNotificationTitle")
        val body = alarm.getString("bedtimeNotificationBody")
        if (title == null || body == null) {
            Log.w("flutter/AlarmHandler", "No bedtime notification title or body set")
            return
        }

        scheduleBedtimeNotification(
            id,
            bedtime,
            title,
            body,
            // default to 2 hours
            alarm.getInt("bedtimeAutoDismiss", 120 * 60),
            alarm.getString("bedtimeDeepLinkUri"),
        )
    }

    fun scheduleBedtimeNotification(
        id: Int,
        bedtime: Long,
        title: String,
        body: String,
        autoDismissSeconds: Int,
        deeplinkUri: String?,
    ) {
        val bedtimeId = toBedtimeId(id)
        val delayInSeconds = ((bedtime - System.currentTimeMillis()) / 1000).toInt()
        if (delayInSeconds < 0) {
            Log.d("flutter/AlarmHandler", "Bedtime notification $bedtimeId not set because the time already passed ($bedtime)")
            return
        }

        val intent = Intent(context, BedtimeNotificationReceiver::class.java)
        intent.putExtra("id", bedtimeId)
        intent.putExtra("title", title)
        intent.putExtra("body", body)
        intent.putExtra("autoDismissSeconds", autoDismissSeconds)
        intent.putExtra("deeplinkUri", deeplinkUri)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                bedtimeId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bedtime, pendingIntent)
        Log.d("flutter/AlarmHandler", "Bedtime notification $bedtimeId SCHEDULED at ${toDateTimeString(bedtime)}")
    }

    fun cancelBedtimeNotification(id: Int) {
        val bedtimeId = toBedtimeId(id)
        val intent = Intent(context, BedtimeNotificationReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                bedtimeId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // Cancel alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        // Cancel pending intent
        pendingIntent.cancel()

        // Cancel notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(bedtimeId)
        Log.d("flutter/AlarmHandler", "Bedtime notification $bedtimeId cancelled")
    }
}
