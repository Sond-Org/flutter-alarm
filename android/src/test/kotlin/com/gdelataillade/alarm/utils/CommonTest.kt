package com.gdelataillade.alarm.utils

import android.os.Bundle
import io.flutter.plugin.common.MethodCall
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class CommonTest {
    @Test
    fun testToDateTimeString() {
        val calendar =
            Calendar.getInstance().apply {
                set(2024, Calendar.JANUARY, 2, 17, 45, 35)
            }
        val expected = "2024-01-02 17:45:35"
        val result = toDateTimeString(calendar.timeInMillis)
        assertEquals("Date conversion should be correct", expected, result)
    }

    @Test
    fun testToBundle_withJSONObject() {
        val jsonObject =
            JSONObject().apply {
                put("stringKey", "stringValue")
                put("intKey", 123)
                put("booleanKey", true)
            }
        val resultBundle = toBundle(jsonObject)

        assertEquals("stringValue", resultBundle.getString("stringKey"))
        assertEquals(123, resultBundle.getInt("intKey"))
        assertEquals(true, resultBundle.getBoolean("booleanKey"))
    }

    @Test
    fun testToBundle_withMethodCall() {
        val methodCall = MethodCall("methodName", mapOf("stringKey" to "stringValue", "intKey" to 123, "booleanKey" to true))
        val resultBundle = toBundle(methodCall)

        assertEquals("stringValue", resultBundle.getString("stringKey"))
        assertEquals(123, resultBundle.getInt("intKey"))
        assertEquals(true, resultBundle.getBoolean("booleanKey"))
    }

    @Test
    fun testToJSONObject_withBundle() {
        val bundle =
            Bundle().apply {
                putString("stringKey", "stringValue")
                putInt("intKey", 123)
                putBoolean("booleanKey", true)
            }
        val resultJSON = toJSONObject(bundle)

        assertEquals("stringValue", resultJSON.getString("stringKey"))
        assertEquals(123, resultJSON.getInt("intKey"))
        assertEquals(true, resultJSON.getBoolean("booleanKey"))
    }

    @Test
    fun testNextDateInMillis_forFutureTime() {
        // Set a specific time and calculate the next alarm time for an hour later
        val calendar =
            Calendar.getInstance().apply {
                set(2024, Calendar.JANUARY, 2, 10, 0, 0)
            }
        val hourOfAlarm = 11 // one hour later
        val nextAlarmTime = nextDateInMillis(hourOfAlarm, 0)

        val expectedAlarmCalendar =
            Calendar.getInstance().apply {
                timeInMillis = nextAlarmTime
            }

        assertEquals("The hour should be the same as the alarm hour set", hourOfAlarm, expectedAlarmCalendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun testNextDateInMillis_forPastTime() {
        // Set a specific time and calculate the next alarm time for an hour earlier (which should be the next day)
        val calendar =
            Calendar.getInstance().apply {
                set(2024, Calendar.JANUARY, 2, 10, 0, 0)
            }
        val hourOfAlarm = 9 // one hour earlier
        val nextAlarmTime = nextDateInMillis(hourOfAlarm, 0)

        val expectedAlarmCalendar =
            Calendar.getInstance().apply {
                timeInMillis = nextAlarmTime
            }

        assertTrue(
            "The alarm should be set for the next day",
            expectedAlarmCalendar.get(Calendar.DAY_OF_MONTH) != calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    @Test
    fun testToBedtimeId() {
        val id = 123
        val result = toBedtimeId(id)
        assertEquals("The bedtime ID should be the negative of the original ID", -id, result)
    }

    @Test
    fun testToBundle_withNestedJSONObject() {
        val nestedObject =
            JSONObject().apply {
                put("nestedKey", "nestedValue")
            }
        val jsonObject =
            JSONObject().apply {
                put("nestedObject", nestedObject)
            }
        val resultBundle = toBundle(jsonObject)

        val nestedBundle = resultBundle.getSerializable("nestedObject") as HashMap<*, *>?
        assertNotNull("The nested bundle should not be null", nestedBundle)
        assertEquals("nestedValue", nestedBundle?.get("nestedKey"))
    }
}
