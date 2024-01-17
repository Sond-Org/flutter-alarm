import 'package:alarm/platform/android_alarm_storage.dart';
import 'package:alarm/platform/base_alarm_storage.dart';
import 'package:alarm/platform/ios_alarm_storage.dart';
import 'package:alarm/service/alarm.dart';
import 'package:flutter/foundation.dart';

/// Provides methods to interact with the native platform for caching alarms.
class AlarmStorage {
  static late BaseAlarmStorage platformSpecificStorage;
  static bool ininitialized = false;

  static void init() {
    if (ininitialized) {
      return;
    }

    if (defaultTargetPlatform == TargetPlatform.iOS) {
      platformSpecificStorage = IOSAlarmStorage();
    } else if (defaultTargetPlatform == TargetPlatform.android) {
      platformSpecificStorage = AndroidAlarmStorage();
    } else {
      throw UnimplementedError("Unsupported platform");
    }

    ininitialized = true;
  }

  /// Saves alarm info in local storage so we can restore it later
  /// in the case app is terminated.
  static Future<bool> saveAlarm(AlarmSettings alarmSettings) {
    init();
    return platformSpecificStorage.saveAlarm(alarmSettings);
  }

  /// Removes alarm from local storage.
  static Future<bool> unsaveAlarm(int id) {
    init();
    return platformSpecificStorage.unsaveAlarm(id);
  }

  /// Whether at least one alarm is set.
  static Future<bool> hasAlarm() {
    init();
    return platformSpecificStorage.hasAlarm();
  }

  /// Returns all alarms info from local storage in the case app is terminated
  /// and we need to restore previously scheduled alarms.
  static Future<List<AlarmSettings>> getSavedAlarms() {
    init();
    return platformSpecificStorage.getSavedAlarms();
  }

  /// Saves on app kill notification custom [title] and [body].
  static Future<bool> setNotificationContentOnAppKill(
    String title,
    String body,
  ) {
    init();
    return platformSpecificStorage.setNotificationContentOnAppKill(title, body);
  }

  /// Returns notification on app kill [title].
  static Future<String> getNotificationOnAppKillTitle() {
    init();
    return platformSpecificStorage.getNotificationOnAppKillTitle();
  }

  /// Returns notification on app kill [body].
  static Future<String> getNotificationOnAppKillBody() {
    init();
    return platformSpecificStorage.getNotificationOnAppKillBody();
  }
}
