package com.gdelataillade.alarm.alarm

import android.content.Context
import androidx.annotation.NonNull
import com.gdelataillade.alarm.features.AlarmHandler
import com.gdelataillade.alarm.features.NotificationHandler
import com.gdelataillade.alarm.features.StorageHandler
import com.gdelataillade.alarm.reboot.RebootBroadcastReceiver
import com.gdelataillade.alarm.utils.toBundle
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class AlarmPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var context: Context
    private lateinit var channel: MethodChannel
    private lateinit var alarmHandler: AlarmHandler
    private lateinit var storageHandler: StorageHandler
    private lateinit var notificationHandler: NotificationHandler

    companion object {
        @JvmStatic
        var binaryMessenger: BinaryMessenger? = null

        @JvmStatic
        val CHANNEL_NAME = "com.gdelataillade.alarm/alarm"
    }

    override fun onAttachedToEngine(
        @NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding,
    ) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)
        binaryMessenger = flutterPluginBinding.binaryMessenger
        alarmHandler = AlarmHandler(context)
        storageHandler = StorageHandler(context)
        notificationHandler = NotificationHandler(context)
        RebootBroadcastReceiver.enableRescheduleOnReboot(context)
        Log.d("flutter/AlarmPlugin", "onAttachedToEngine")
    }

    override fun onMethodCall(
        @NonNull call: MethodCall,
        @NonNull result: Result,
    ) {
        when (call.method) {
            // Alarm management
            "setAlarm" -> {
                val bundle = toBundle(call)
                alarmHandler.scheduleAlarm(bundle)
                alarmHandler.scheduleBedtimeNotification(bundle)
                val id = bundle.getInt("id")
                storageHandler.saveAlarm(id, call.arguments<Map<String, Any>>()!!)
                result.success(true)
            }
            "stopAlarm" -> {
                val id = call.argument<Int>("id")!!
                alarmHandler.stopAlarm(id)
                result.success(true)
            }
            "snoozeAlarm" -> {
                alarmHandler.snoozeAlarm(toBundle(call))
                result.success(true)
            }
            // Alarm states
            "isRinging" -> {
                val id = call.argument<Int>("id")
                val ringingAlarmIds = AlarmService.ringingAlarmIds
                val isRinging =
                    if (id != null) {
                        ringingAlarmIds.contains(id)
                    } else {
                        !ringingAlarmIds.isEmpty()
                    }
                result.success(isRinging)
            }
            "getRingIds" -> {
                result.success(AlarmService.ringingAlarmIds)
            }
            // Notification on-app-killed management
            "setNotificationOnKillService" -> {
                notificationHandler.startNotificationOnKillService(
                    call.argument<String>("title")!!,
                    call.argument<String>("body")!!,
                )
                result.success(true)
            }
            "stopNotificationOnKillService" -> {
                notificationHandler.stopNotificationOnKillService()
                result.success(true)
            }
            "setNotificationContentOnAppKill" -> {
                storageHandler.setNotificationContentOnAppKill(
                    call.argument<String>("title")!!,
                    call.argument<String>("body")!!,
                )
                result.success(true)
            }
            "getNotificationOnAppKillTitle" -> {
                result.success(storageHandler.getNotificationOnAppKillTitle())
            }
            "getNotificationOnAppKillBody" -> {
                result.success(storageHandler.getNotificationOnAppKillBody())
            }
            // Storage
            "saveAlarm" -> {
                val id = call.argument<Int>("id")!!
                storageHandler.saveAlarm(id, call.arguments<Map<String, Any>>()!!)
                result.success(true)
            }
            "unsaveAlarm" -> {
                val id = call.argument<Int>("id")!!
                alarmHandler.stopAlarm(id)
                storageHandler.deleteAlarm(id)
                result.success(true)
            }
            "hasAlarm" -> {
                result.success(storageHandler.listAlarms().isNotEmpty())
            }
            "getAlarm" -> {
                val id = call.argument<Int>("id")!!
                val alarm = storageHandler.getAlarm(id)
                if (alarm == null) {
                    result.success(null)
                    return
                }
                result.success(alarm.toString())
            }
            "listAlarms" -> {
                val alarms = storageHandler.listAlarms()
                val alarmsJson = mutableListOf<String>()
                alarms.forEach { alarm ->
                    alarmsJson.add(alarm.toString())
                }
                result.success(alarmsJson)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(
        @NonNull binding: FlutterPlugin.FlutterPluginBinding,
    ) {
        binaryMessenger = null
        Log.clearChannel()
        channel.setMethodCallHandler(null)
    }
}
