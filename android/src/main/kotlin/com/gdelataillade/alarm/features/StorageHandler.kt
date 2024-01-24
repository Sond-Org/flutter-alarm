package com.gdelataillade.alarm.features

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.gdelataillade.alarm.utils.toJSONObject
import com.gdelataillade.alarm.alarm.Log
import org.json.JSONObject

class StorageHandler(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("alarm-storage", Context.MODE_PRIVATE)
    private val keyPrefix = "__alarm__"
    private val notificationOnAppKillTitle = "notificationOnAppKillTitle"
    private val notificationOnAppKillBody = "notificationOnAppKillBody"

    fun saveAlarm(
        id: Int,
        bundle: Bundle,
    ) {
        saveAlarm(id, toJSONObject(bundle))
    }

    fun saveAlarm(
        id: Int,
        map: Map<String, Any>,
    ) {
        saveAlarm(id, toJSONObject(map))
    }

    fun saveAlarm(
        id: Int,
        json: JSONObject,
    ) {
        val str = json.toString()
        Log.d("flutter/StorageHandler", "Saving alarm $id: $str")
        setValue(getKey(id), str)
    }

    fun deleteAlarm(id: Int) {
        Log.d("flutter/StorageHandler", "Deleting alarm $id")
        removeValue(getKey(id))
    }

    fun hasAlarm(id: Int): Boolean {
        return getValue(getKey(id)).isNotEmpty()
    }

    fun getAlarm(id: Int): JSONObject? {
        val alarm = getValue(getKey(id))
        if (alarm.isEmpty()) {
            return null
        }
        return JSONObject(alarm)
    }

    fun listAlarms(): List<JSONObject> {
        return sharedPreferences.all
            .filter { (key, _) -> key.startsWith(keyPrefix) }
            .map { (_, value) -> JSONObject(value as String) }
    }

    fun setNotificationContentOnAppKill(
        title: String,
        body: String,
    ) {
        setValue(notificationOnAppKillTitle, title)
        setValue(notificationOnAppKillBody, body)
    }

    fun getNotificationOnAppKillTitle(): String {
        return getValue(notificationOnAppKillTitle)
    }

    fun getNotificationOnAppKillBody(): String {
        return getValue(notificationOnAppKillBody)
    }

    private fun getKey(id: Int): String {
        return "$keyPrefix$id"
    }

    private fun getValue(key: String): String {
        return sharedPreferences.getString(key, "") ?: ""
    }

    private fun setValue(
        key: String,
        value: String,
    ) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    private fun removeValue(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
}
