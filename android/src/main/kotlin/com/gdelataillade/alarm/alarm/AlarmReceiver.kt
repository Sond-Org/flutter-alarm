package com.gdelataillade.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val serviceIntent = Intent(context, AlarmService::class.java)
        serviceIntent.action = intent.action
        serviceIntent.putExtras(intent)

        context.startService(serviceIntent)
    }
}
