package com.gdelataillade.alarm.reboot

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.gdelataillade.alarm.alarm.Log
import com.gdelataillade.alarm.features.AlarmHandler

/**
 * Reschedules background work after the Android device reboots.
 *
 * When an Android device reboots, all previously scheduled [android.app.AlarmManager]
 * timers are cleared.
 */
class RebootBroadcastReceiver : BroadcastReceiver() {
    /**
     * Invoked by the OS whenever a broadcast is received by this app.
     *
     * If the broadcast's action is `BOOT_COMPLETED` then this [RebootBroadcastReceiver] reschedules all timer callbacks. That rescheduling work is
     * handled by [AlarmHandler.rescheduleAlarms].
     */
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.i("flutter/RebootBroadcastReceiver", "Rescheduling after boot!")
            AlarmHandler(context).rescheduleAlarms()
        }
    }

    companion object {
        /**
         * Schedules this [RebootBroadcastReceiver] to be run whenever the Android device reboots.
         */
        fun enableRescheduleOnReboot(context: Context) {
            Log.d("flutter/RebootBroadcastReceiver", "Enabling reboot receiver")
            scheduleOnReboot(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }

        /**
         * Unschedules this [RebootBroadcastReceiver] to be run whenever the Android device reboots.
         * This [RebootBroadcastReceiver] will no longer be run upon reboot.
         */
        fun disableRescheduleOnReboot(context: Context) {
            Log.d("flutter/RebootBroadcastReceiver", "Disabling reboot receiver")
            scheduleOnReboot(context, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }

        private fun scheduleOnReboot(
            context: Context,
            state: Int,
        ) {
            val receiver = ComponentName(context, RebootBroadcastReceiver::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
        }
    }
}
