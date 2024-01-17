import 'dart:convert';

import 'package:alarm/service/alarm.dart';
import 'package:flutter/services.dart';

/// Provides methods to interact with the native platform for caching alarms.
abstract class BaseAlarmStorage {
  static const platform = MethodChannel('com.gdelataillade.alarm/alarm');

  static const notificationOnAppKill = 'notificationOnAppKill';
  static const notificationOnAppKillTitle = 'notificationOnAppKillTitle';
  static const notificationOnAppKillBody = 'notificationOnAppKillBody';

  /// Saves alarm info in local storage so we can restore it later
  /// in the case app is terminated.
  Future<bool> saveAlarm(AlarmSettings alarmSettings) async {
    try {
      return await platform.invokeMethod('saveAlarm', alarmSettings.toJson());
    } catch (e) {
      throw AlarmException('Failed to save alarm ${alarmSettings.id}. $e');
    }
  }

  /// Removes alarm from local storage.
  Future<bool> unsaveAlarm(int id) async {
    try {
      return await platform.invokeMethod('unsaveAlarm', {'id': id});
    } catch (e) {
      throw AlarmException('Failed to unsave alarm $id. $e');
    }
  }

  /// Whether at least one alarm is set.
  Future<bool> hasAlarm() async {
    try {
      return await platform.invokeMethod('hasAlarm');
    } catch (e) {
      throw AlarmException(
        'Failed to check if there is at least one alarm set. $e',
      );
    }
  }

  /// Returns all alarms info from local storage in the case app is terminated
  /// and we need to restore previously scheduled alarms.
  Future<List<AlarmSettings>> getSavedAlarms() async {
    late final List<Object?>? rawAlarms;
    try {
      rawAlarms = await platform.invokeMethod<List<Object?>>('listAlarms');
    } catch (e) {
      throw AlarmException('Failed to list alarms. $e');
    }

    if (rawAlarms == null || rawAlarms.isEmpty) {
      return [];
    }

    final alarms = <AlarmSettings>[];
    for (final rawAlarm in rawAlarms) {
      if (rawAlarm == null) continue;
      late final Map<String, dynamic> jsonAlarm;
      try {
        jsonAlarm = json.decode(rawAlarm.toString());
      } catch (e) {
        alarmPrint(
          '[STORAGE] Failed to parse alarm as json: $rawAlarm. $e',
        );
        continue;
      }

      try {
        alarms.add(AlarmSettings.fromJson(jsonAlarm));
      } catch (e) {
        alarmPrint(
          '[STORAGE] Failed to parse alarm $rawAlarm - removing alarm from storage. $e',
        );
        final id = jsonAlarm['id'];
        if (id != null) {
          unsaveAlarm(id);
        }
      }
    }

    return alarms;
  }

  /// Saves on app kill notification custom [title] and [body].
  Future<bool> setNotificationContentOnAppKill(
    String title,
    String body,
  ) async {
    try {
      return await platform.invokeMethod('setNotificationContentOnAppKill', {
        'title': title,
        'body': body,
      });
    } catch (e) {
      throw AlarmException(
        'Failed to set the title and body of the notification that shows when the app is killed. $e',
      );
    }
  }

  /// Returns notification on app kill [title].
  Future<String> getNotificationOnAppKillTitle() async {
    try {
      return await platform.invokeMethod('getNotificationOnAppKillTitle');
    } catch (e) {
      throw AlarmException(
        'Failed to get the title of the notification that shows when the app is killed. $e',
      );
    }
  }

  /// Returns notification on app kill [body].
  Future<String> getNotificationOnAppKillBody() async {
    try {
      return await platform.invokeMethod('getNotificationOnAppKillBody');
    } catch (e) {
      throw AlarmException(
        'Failed to get the body of the notification that shows when the app is killed. $e',
      );
    }
  }
}
