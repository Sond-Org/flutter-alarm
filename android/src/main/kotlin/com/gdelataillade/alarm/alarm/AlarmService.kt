package com.gdelataillade.alarm.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import com.gdelataillade.alarm.features.AlarmHandler
import com.gdelataillade.alarm.features.AudioHandler
import com.gdelataillade.alarm.features.NotificationHandler
import com.gdelataillade.alarm.features.StorageHandler
import com.gdelataillade.alarm.features.VibrationHandler
import com.gdelataillade.alarm.features.VolumeHandler
import com.gdelataillade.alarm.utils.nextDateInMillis
import com.gdelataillade.alarm.alarm.Log
import io.flutter.plugin.common.MethodChannel

class AlarmService : Service() {
    private lateinit var channel: MethodChannel
    private lateinit var audioHandler: AudioHandler
    private lateinit var vibrationHandler: VibrationHandler
    private lateinit var volumeHandler: VolumeHandler
    private lateinit var alarmHandler: AlarmHandler
    private lateinit var storageHandler: StorageHandler
    private val showSystemUI: Boolean = true
    private var channelInitialized: Boolean = false

    companion object {
        @JvmStatic
        var ringingAlarmIds: List<Int> = listOf()

        @JvmStatic
        val START_ALARM_ACTION = "START_ALARM"

        @JvmStatic
        val STOP_ALARM_ACTION = "STOP_ALARM"

        @JvmStatic
        val SNOOZE_ALARM_ACTION = "SNOOZE_ALARM"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("flutter/AlarmService", "onCreate")
        initChannel()
        audioHandler = AudioHandler(this)
        vibrationHandler = VibrationHandler(this)
        volumeHandler = VolumeHandler(this)
        alarmHandler = AlarmHandler(this)
        storageHandler = StorageHandler(this)
    }

    private fun initChannel() {
        try {
            Log.d("flutter/AlarmService", "Attempting to initialize method channel")
            val messenger = AlarmPlugin.binaryMessenger
            if (messenger != null) {
                channel = MethodChannel(messenger, AlarmPlugin.CHANNEL_NAME)
                channelInitialized = true
                Log.d("flutter/AlarmService", "Method channel initialized successfully")
            }
        } catch (e: Exception) {
            Log.d("flutter/AlarmService", "Error while creating method channel: $e")
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!channelInitialized) {
            initChannel()
        }

        val id = intent?.getIntExtra("id", -1) ?: -1
        if (id == -1) {
            Log.d("flutter/AlarmService", "No id provided")
            return START_NOT_STICKY
        }

        val action = intent?.action
        when (action) {
            START_ALARM_ACTION -> {
                Log.d("flutter/AlarmService", "START alarm $id")
                startAlarm(id, intent.getExtras())
                return START_STICKY
            }

            STOP_ALARM_ACTION -> {
                Log.d("flutter/AlarmService", "STOP alarm $id")
                stopAlarm(id)
                val recurring = intent?.getBooleanExtra("recurring", false) ?: false
                if (recurring && intent.getExtras() != null) {
                    scheduleRecurringAlarm(id, intent.getExtras()!!)
                }
                notifyAlarmDismissed(id)
                return START_NOT_STICKY
            }

            SNOOZE_ALARM_ACTION -> {
                Log.d("flutter/AlarmService", "SNOOZE alarm $id")
                stopAlarm(id)

                if (intent?.getExtras() == null) {
                    Log.d("flutter/AlarmService", "Cannot snooze alarm - no extras provided")
                    return START_NOT_STICKY
                }

                val snoozeDuration = intent?.getIntExtra("snoozeDuration", 5 * 60) ?: 5 * 60 // default to 5 minutes
                val rescheduleTime = System.currentTimeMillis() + snoozeDuration * 1000
                intent.putExtra("dateTime", rescheduleTime)
                val extras = intent.getExtras()!!
                alarmHandler.scheduleAlarm(rescheduleTime, id, extras)
                notifyAlarmSnoozed(id)

                // Save alarm
                storageHandler.saveAlarm(id, extras)
                return START_NOT_STICKY
            }

            else -> {
                Log.d("flutter/AlarmService", "Unknown action: $action")
                return START_NOT_STICKY
            }
        }
    }

    private fun startAlarm(
        id: Int,
        extras: Bundle?,
    ) {
        // Wake up the device for at least 5 minutes to ensure the alarm rings
        val wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:AlarmWakelockTag")
        wakeLock.acquire(5 * 60 * 1000L)

        showNotification(id, extras)
        startAlarmAudio(id, extras)
        notifyAlarmRinging(id)

        // TODO(system-alert): Decide if this is something we want to do
        // val activityIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        // activityIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        // startActivity(activityIntent)
    }

    private fun stopAlarm(id: Int) {
        volumeHandler.restorePreviousVolume(showSystemUI)
        volumeHandler.abandonAudioFocus()

        audioHandler.stopAudio(id)
        ringingAlarmIds = audioHandler.getPlayingMediaPlayersIds()!!

        alarmHandler.cancelBedtimeNotification(id)

        if (audioHandler.isMediaPlayerEmpty()!!) {
            vibrationHandler.stopVibrating()
            stopSelf()
        }

        stopForeground(true)
    }

    private fun scheduleRecurringAlarm(
        id: Int,
        extras: Bundle,
    ) {
        // Schedule next alarm
        val originalHour = extras.getInt("originalHour", -1)
        val originalMinute = extras.getInt("originalMinute", -1)
        if (originalHour == -1 || originalMinute == -1) {
            Log.w("flutter/AlarmService", "No originalHour or originalMinute provided")
            return
        }

        val nextAlarmTime = nextDateInMillis(originalHour, originalMinute)
        extras.putLong("dateTime", nextAlarmTime)
        alarmHandler.scheduleAlarm(nextAlarmTime, id, extras)

        // Schedule next bedtime notification
        val bedtime = extras.getLong("bedtime", -1L)
        if (bedtime == -1L) {
            Log.w("flutter/AlarmService", "No bedtime provided")
            return
        }

        val nextBedtime = nextDateInMillis(bedtime)
        extras.putLong("bedtime", nextBedtime)
        val title = extras.getString("bedtimeNotificationTitle")
        val body = extras.getString("bedtimeNotificationBody")
        if (title == null || body == null) {
            Log.w("flutter/AlarmService", "No bedtime notification title or body set")
            return
        }

        val autoDismissSeconds = extras.getInt("bedtimeAutoDismiss", 120 * 60) ?: 120 * 60 // default to 2 hours
        val deeplinkUri = extras.getString("bedtimeDeeplinkUri")
        alarmHandler.scheduleBedtimeNotification(
            id,
            nextBedtime,
            title,
            body,
            autoDismissSeconds,
            deeplinkUri,
        )

        // Save alarm
        storageHandler.saveAlarm(id, extras)
    }

    private fun showNotification(
        id: Int,
        extras: Bundle?,
    ) {
        val notificationTitle = extras?.getString("notificationTitle")!!
        val notificationBody = extras?.getString("notificationBody")!!
        val fullScreenIntent = extras?.getBoolean("fullScreenIntent", true)!!
        val snoozeEnabled = extras?.getBoolean("snooze", false)!!
        val snoozeLabel = extras?.getString("notificationActionSnoozeLabel", "Snooze")!!
        val dismissLabel = extras?.getString("notificationActionDismissLabel", "Dismiss")!!

        val notificationHandler = NotificationHandler(this)
        val notification =
            notificationHandler.buildAlarmNotification(
                id,
                notificationTitle,
                notificationBody,
                fullScreenIntent,
                snoozeEnabled,
                extras,
                snoozeLabel,
                dismissLabel,
            )
        startForeground(id, notification)
    }

    private fun startAlarmAudio(
        id: Int,
        extras: Bundle?,
    ) {
        val assetAudioPath = extras?.getString("assetAudioPath")!!
        val loopAudio = extras?.getBoolean("loopAudio", true) ?: true
        val vibrate = extras?.getBoolean("vibrate", true) ?: true
        val volumeMax = extras?.getBoolean("volumeMax", false) ?: false
        val fadeDuration = extras?.getInt("fadeDuration", 0) ?: 0

        if (volumeMax) {
            volumeHandler.setVolume(1.0, showSystemUI)
        }

        volumeHandler.requestAudioFocus()

        audioHandler.setOnAudioCompleteListener {
            if (!loopAudio) {
                vibrationHandler.stopVibrating()
                volumeHandler.restorePreviousVolume(showSystemUI)
                volumeHandler.abandonAudioFocus()
            }
        }

        audioHandler.playAudio(id, assetAudioPath, loopAudio, fadeDuration)
        ringingAlarmIds = ringingAlarmIds + id
        if (vibrate) {
            vibrationHandler.startVibrating(longArrayOf(0, 500, 500), 1)
        }
    }

    private fun notifyAlarmRinging(id: Int) {
        notify("alarmRinging", id)
    }

    private fun notifyAlarmDismissed(id: Int) {
        notify("alarmDismissed", id)
    }

    private fun notifyAlarmSnoozed(id: Int) {
        notify("alarmSnoozed", id)
    }

    private fun notify(
        method: String,
        id: Int,
    ) {
        try {
            channel?.invokeMethod(method, mapOf("id" to id))
        } catch (e: Exception) {
            Log.d("flutter/AlarmService", "Error while invoking $method channel: $e")
        }
    }

    override fun onDestroy() {
        ringingAlarmIds = listOf()

        audioHandler.cleanUp()
        vibrationHandler.stopVibrating()
        volumeHandler.restorePreviousVolume(showSystemUI)

        stopForeground(true)

        // Call the superclass method
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
