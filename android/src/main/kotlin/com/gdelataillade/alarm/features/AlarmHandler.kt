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
import com.gdelataillade.alarm.utils.toDateTimeString
import com.gdelataillade.alarm.utils.toMap
import io.flutter.Log
import io.flutter.plugin.common.MethodCall
import org.json.JSONObject
import java.io.Serializable

class AlarmHandler(private val context: Context) {
    private val storageHandler = StorageHandler(context)

    companion object {
        // Shared lock object for all instances of AlarmHandler
        private val lock = Any()
    }

    fun scheduleAlarm(alarm: JSONObject) {
        val id = alarm.getInt("id")
        val scheduleTime = alarm.getLong("dateTime")
        val extras = createExtras(alarm, id)
        scheduleAlarm(scheduleTime, id, extras)
    }

    fun scheduleAlarm(
        call: MethodCall,
        id: Int,
    ) {
        val scheduleTime = call.argument<Long>("dateTime")!!
        val extras = createExtras(call, id)
        scheduleAlarm(scheduleTime, id, extras)
    }

    fun scheduleAlarm(
        scheduleTime: Long,
        id: Int,
        extras: Bundle,
    ) {
        Log.d("flutter/AlarmHandler", "Alarm scheduled at ${toDateTimeString(scheduleTime)}")
        val delayInSeconds = ((scheduleTime - System.currentTimeMillis()) / 1000).toInt()

        val alarmIntent = createAlarmIntent(id, extras)
        synchronized(lock) {
            if (delayInSeconds <= 5) {
                handleImmediateAlarm(alarmIntent, delayInSeconds)
            } else {
                handleDelayedAlarm(alarmIntent, delayInSeconds, id)
            }
        }
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
        call: MethodCall,
        id: Int,
    ): Intent {
        return createAlarmIntent(id, createExtras(call, id))
    }

    private fun createAlarmIntent(
        id: Int,
        extras: Bundle,
    ): Intent {
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.action = AlarmService.START_ALARM_ACTION
        intent.putExtras(extras)
        return intent
    }

    private fun createExtras(
        call: MethodCall,
        id: Int,
    ): Bundle {
        return Bundle().apply {
            putInt("id", id)
            putString("assetAudioPath", call.argument<String>("assetAudioPath"))
            putBoolean("loopAudio", call.argument<Boolean>("loopAudio") ?: true)
            putBoolean("vibrate", call.argument<Boolean>("vibrate") ?: false)
            putBoolean("volumeMax", call.argument<Boolean>("volumeMax") ?: false)
            putInt("fadeDuration", call.argument<Int>("fadeDuration") ?: 0)
            putString("notificationTitle", call.argument<String>("notificationTitle"))
            putString("notificationBody", call.argument<String>("notificationBody"))
            putBoolean("fullScreenIntent", call.argument<Boolean>("fullScreenIntent") ?: true)
            val originalHour = call.argument<Int>("originalHour")
            if (originalHour != null) {
                putInt("originalHour", originalHour!!)
            }
            val originalMinute = call.argument<Int>("originalMinute")
            if (originalMinute != null) {
                putInt("originalMinute", originalMinute!!)
            }
            putBoolean("recurring", call.argument<Boolean>("recurring") ?: false)
            val bedtime = call.argument<Long>("bedtime")
            if (bedtime != null) {
                putLong("bedtime", bedtime!!)
            }
            putInt("bedtimeAutoDismiss", call.argument<Int>("bedtimeAutoDismiss") ?: 120 * 60) // default to 2 hours
            putString("bedtimeNotificationTitle", call.argument<String>("bedtimeNotificationTitle"))
            putString("bedtimeNotificationBody", call.argument<String>("bedtimeNotificationBody"))
            putString("bedtimeDeepLinkUri", call.argument<String>("bedtimeDeepLinkUri"))
            putBoolean("snooze", call.argument<Boolean>("snooze") ?: false)
            putInt("snoozeDuration", call.argument<Int>("snoozeDuration") ?: 5 * 60) // default to 5 minutes
            putString("notificationActionSnoozeLabel", call.argument<String>("notificationActionSnoozeLabel"))
            putString("notificationActionDismissLabel", call.argument<String>("notificationActionDismissLabel"))
            putBoolean("enableNotificationOnKill", call.argument<Boolean>("enableNotificationOnKill") ?: false)
            putBoolean("stopOnNotificationOpen", call.argument<Boolean>("stopOnNotificationOpen") ?: false)
            val extra = call.argument<Map<String, Any>>("extra")
            if (extra != null) {
                putSerializable("extra", extra as Serializable)
            }
        }
    }

    private fun createExtras(
        json: JSONObject,
        id: Int,
    ): Bundle {
        return Bundle().apply {
            putInt("id", id)
            putString("assetAudioPath", json.optString("assetAudioPath"))
            putBoolean("loopAudio", json.optBoolean("loopAudio", true))
            putBoolean("vibrate", json.optBoolean("vibrate", false))
            putBoolean("volumeMax", json.optBoolean("volumeMax", false))
            putInt("fadeDuration", json.optInt("fadeDuration", 0))
            putString("notificationTitle", json.optString("notificationTitle"))
            putString("notificationBody", json.optString("notificationBody"))
            putBoolean("fullScreenIntent", json.optBoolean("fullScreenIntent", true))
            val originalHour = json.optInt("originalHour", -1)
            if (originalHour != -1) {
                putInt("originalHour", originalHour)
            }
            val originalMinute = json.optInt("originalMinute", -1)
            if (originalMinute != -1) {
                putInt("originalMinute", originalMinute)
            }
            putBoolean("recurring", json.optBoolean("recurring", false))
            val bedtime = json.optLong("bedtime", -1L)
            if (bedtime != -1L) {
                putLong("bedtime", bedtime)
            }
            putInt("bedtimeAutoDismiss", json.optInt("bedtimeAutoDismiss", 120 * 60)) // default to 2 hours
            putString("bedtimeNotificationTitle", json.optString("bedtimeNotificationTitle"))
            putString("bedtimeNotificationBody", json.optString("bedtimeNotificationBody"))
            putString("bedtimeDeepLinkUri", json.optString("bedtimeDeepLinkUri"))
            putBoolean("snooze", json.optBoolean("snooze", false))
            putInt("snoozeDuration", json.optInt("snoozeDuration", 5 * 60)) // default to 5 minutes
            putString("notificationActionSnoozeLabel", json.optString("notificationActionSnoozeLabel"))
            putString("notificationActionDismissLabel", json.optString("notificationActionDismissLabel"))
            putBoolean("enableNotificationOnKill", json.optBoolean("enableNotificationOnKill", false))
            putBoolean("stopOnNotificationOpen", json.optBoolean("stopOnNotificationOpen", false))
            val extra = json.optJSONObject("extra")
            if (extra != null) {
                putSerializable("extra", toMap(extra) as Serializable)
            }
        }
    }

    private fun handleImmediateAlarm(
        intent: Intent,
        delayInSeconds: Int,
    ) {
        Log.d("flutter/AlarmHandler", "Alarm set and will trigger in $delayInSeconds seconds")
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            context.sendBroadcast(intent)
        }, delayInSeconds * 1000L)
    }

    private fun handleDelayedAlarm(
        intent: Intent,
        delayInSeconds: Int,
        id: Int,
    ) {
        Log.d("flutter/AlarmHandler", "Alarm set and will trigger in $delayInSeconds seconds")
        val triggerTime = System.currentTimeMillis() + delayInSeconds * 1000
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val info = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
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
                    val extras = createExtras(alarm!!, id)
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

    fun snoozeAlarm(
        call: MethodCall,
        id: Int,
    ) {
        val extras = createExtras(call, id)
        snoozeAlarm(id, extras)
    }

    fun snoozeAlarm(
        id: Int,
        extras: Bundle,
    ) {
        Log.d("flutter/AlarmHandler", "Snoozing alarm $id")
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.action = AlarmService.SNOOZE_ALARM_ACTION
        intent.putExtras(extras)
        val handler = Handler(Looper.getMainLooper())
        handler.post({ context.sendBroadcast(intent) })
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

    fun scheduleBedtimeNotification(alarm: JSONObject) {
        val id = alarm.getInt("id")
        val bedtime = alarm.optLong("bedtime", -1L)
        if (bedtime == -1L) {
            Log.d("flutter/AlarmHandler", "No bedtime set")
            return
        }

        val title = alarm.optString("bedtimeNotificationTitle")
        val body = alarm.optString("bedtimeNotificationBody")
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
            alarm.optInt("bedtimeAutoDismiss", 120 * 60),
            alarm.optString("bedtimeDeepLinkUri"),
        )
    }

    fun scheduleBedtimeNotification(
        call: MethodCall,
        id: Int,
    ) {
        val bedtime = call.argument<Long>("bedtime")
        if (bedtime == null) {
            Log.d("flutter/AlarmHandler", "No bedtime set")
            return
        }

        val title = call.argument<String>("bedtimeNotificationTitle")
        val body = call.argument<String>("bedtimeNotificationBody")
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
            call.argument<Int>("bedtimeAutoDismiss") ?: 120 * 60,
            call.argument<String>("bedtimeDeepLinkUri"),
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
        Log.d("flutter/AlarmHandler", "Bedtime notification $bedtimeId set at ${toDateTimeString(bedtime)}")
    }
}
