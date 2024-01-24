package com.gdelataillade.alarm.alarm

import com.gdelataillade.alarm.alarm.AlarmPlugin
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.ConcurrentLinkedQueue

class Log {

    companion object {

        private var channel: MethodChannel? = null

        fun clearChannel() {
            channel = null
        }

        private fun log(method: String, tag: String, message: String, e: Exception? = null) {
            if (channel == null) {
                AlarmPlugin.binaryMessenger?.let {
                    channel = MethodChannel(it, AlarmPlugin.CHANNEL_NAME)
                }
            }
            val logMessage = if (e != null) {
                "[$tag] $message $e"
            } else {
                "[$tag] $message"
            }
            if (e != null) {
                when (method) {
                    "logD" -> io.flutter.Log.d(tag, message, e)
                    "logI" -> io.flutter.Log.i(tag, message, e)
                    "logW" -> io.flutter.Log.w(tag, message, e)
                    "logE" -> io.flutter.Log.e(tag, message, e)
                }
            } else {
                when (method) {
                    "logD" -> io.flutter.Log.d(tag, message)
                    "logI" -> io.flutter.Log.i(tag, message)
                    "logW" -> io.flutter.Log.w(tag, message)
                    "logE" -> io.flutter.Log.e(tag, message)
                }
            }
            channel?.invokeMethod(method, mapOf("message" to logMessage))
        }

        fun d(tag: String, message: String) {
            log("logD", tag, message)
        }

        fun d(tag: String, message: String, e: Exception) {
            log("logD", tag, message, e)
        }

        fun i(tag: String, message: String) {
            log("logI", tag, message)
        }

        fun i(tag: String, message: String, e: Exception) {
            log("logI", tag, message, e)
        }

        fun w(tag: String, message: String) {
            log("logW", tag, message)
        }


        fun w(tag: String, message: String, e: Exception) {
            log("logW", tag, message, e)
        }

        fun e(tag: String, message: String) {
            log("logE", tag, message)
        }

        fun e(tag: String, message: String, e: Exception) {
            log("logE", tag, message, e)
        }
    }
}