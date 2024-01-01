package com.gdelataillade.alarm.utils

import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

fun toDateTimeString(millis: Long): String {
    val date = Date(millis)
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return format.format(date)
}

fun toBedtimeId(id: Int): Int {
    return -id
}

/**
 * Returns the next [Date] in milliseconds when the alarm should be set. If the time
 * has passed today, it will return the time for tomorrow at the same
 * hour and minute this alarm was originally scheduled for.
 */
fun nextDateInMillis(
    hour: Int,
    minute: Int,
): Long {
    val now = Date()
    val calendar = Calendar.getInstance()
    calendar.time = now

    val result = Calendar.getInstance()
    result.set(Calendar.HOUR_OF_DAY, hour)
    result.set(Calendar.MINUTE, minute)
    result.set(Calendar.SECOND, 0)
    result.set(Calendar.MILLISECOND, 0)

    if (result.timeInMillis <= now.time) {
        result.add(Calendar.DAY_OF_MONTH, 1)
    }

    return result.timeInMillis
}

fun nextDateInMillis(millis: Long): Long {
    val date = Date(millis)
    return nextDateInMillis(date.hours, date.minutes)
}

fun toJSONObject(bundle: Bundle): JSONObject {
    val jsonObject = JSONObject()

    for (key in bundle.keySet()) {
        when (val value = bundle.get(key)) {
            is String -> jsonObject.put(key, value)
            is Int -> jsonObject.put(key, value)
            is Boolean -> jsonObject.put(key, value)
            is Long -> jsonObject.put(key, value)
            is Serializable -> {
                if (value is Map<*, *>) {
                    jsonObject.put(key, toJSONObject(value as Map<String, Any>))
                } else if (value is JSONObject) {
                    jsonObject.put(key, value)
                } else {
                    val variableType = value!!::class.java.simpleName
                    throw IllegalArgumentException("Unsupported data type ($variableType) for key $key in Bundle")
                }
            }
            else -> {
                if (value == null) {
                    throw IllegalArgumentException("Unsupported data type for key $key in Bundle")
                }

                val variableType = value!!::class.java.simpleName
                throw IllegalArgumentException("Unsupported data type ($variableType) for key $key in Bundle")
            }
        }
    }

    return jsonObject
}

fun toJSONObject(map: Map<String, Any>): JSONObject {
    val jsonObject = JSONObject()

    for ((key, value) in map) {
        if (value is Map<*, *>) {
            jsonObject.put(key, toJSONObject(value as Map<String, Any>))
        } else {
            jsonObject.put(key, value)
        }
    }

    return jsonObject
}

fun toMap(jsonObject: JSONObject): Map<String, Any> {
    val map = mutableMapOf<String, Any>()

    for (key in jsonObject.keys()) {
        val value = jsonObject[key]

        if (value != null) {
            if (value is JSONObject) {
                map[key] = toMap(value)
            } else if (value is JSONArray) {
                map[key] = toList(value)
            } else {
                map[key] = value
            }
        }
    }

    return map
}

fun toList(jsonArray: JSONArray): List<Any> {
    val list = mutableListOf<Any>()

    for (i in 0 until jsonArray.length()) {
        val element = jsonArray[i]

        if (element != null) {
            if (element is JSONObject) {
                list.add(toMap(element))
            } else if (element is JSONArray) {
                list.add(toList(element))
            } else {
                list.add(element)
            }
        }
    }

    return list
}
