// ignore_for_file: avoid_print

export 'package:alarm/model/alarm_settings.dart';
import 'dart:async';

import 'package:alarm/model/alarm_settings.dart';
import 'package:alarm/model/notification_event.dart';
import 'package:alarm/platform/ios_alarm.dart';
import 'package:alarm/platform/android_alarm.dart';
import 'package:alarm/platform/base_alarm.dart';
import 'package:alarm/service/storage.dart';
import 'package:flutter/foundation.dart';

/// Custom print function designed for Alarm plugin.
DebugPrintCallback alarmPrint = debugPrintThrottled;

class Alarm {
  /// Whether it's iOS device.
  static bool get iOS => defaultTargetPlatform == TargetPlatform.iOS;

  /// Whether it's Android device.
  static bool get android => defaultTargetPlatform == TargetPlatform.android;

  /// Stream of the ringing status.
  static final ringStream = StreamController<AlarmSettings>();

  /// Stream when notification is selected.
  static final alarmNotificationStream = StreamController<NotificationEvent>();

  static bool _initialized = false;
  static bool get initialized => _initialized;

  // Platform specific alarm implementation
  static late BaseAlarm platformSpecificAlarm;

  /// Initializes Alarm services.
  ///
  /// Also calls [checkAlarm] that will reschedule alarms that were set before
  /// app termination.
  ///
  /// Set [showDebugLogs] to `false` to hide all the logs from the plugin.
  static Future<void> init({
    bool showDebugLogs = true,
    void Function(String msg)? logProxy,
  }) async {
    if (_initialized) {
      alarmPrint('Already initialized. Ignoring request to initialize again');
      return;
    }

    alarmPrint = (String? message, {int? wrapWidth}) {
      if (!showDebugLogs) {
        return;
      }

      if (logProxy != null) {
        logProxy("[Alarm] $message");
      } else if (kDebugMode) {
        print("[Alarm] $message");
      }
    };

    if (android) {
      platformSpecificAlarm = AndroidAlarm();
    } else if (iOS) {
      platformSpecificAlarm = IOSAlarm();
    } else {
      throw UnimplementedError("Unsupported platform");
    }

    AlarmStorage.init();
    await platformSpecificAlarm.init();
    await checkAlarm();
    _initialized = true;
  }

  /// Checks if some alarms were set on previous session.
  /// If it's the case then reschedules them.
  /// This is required after an app updates.
  static Future<void> checkAlarm() async {
    final alarms = await AlarmStorage.getSavedAlarms();
    final now = DateTime.now();
    for (final alarm in alarms) {
      if (alarm.dateTime.isAfter(now)) {
        await set(alarmSettings: alarm);
      } else {
        alarmPrint('Keeping past alarms during initialization');
      }
    }
  }

  /// Schedules an alarm with given [alarmSettings].
  ///
  /// If you set an alarm for the same [dateTime] as an existing one,
  /// the new alarm will replace the existing one.
  static Future<bool> set({required AlarmSettings alarmSettings}) async {
    if (iOS) {
      // TODO: implement iOS
      throw UnimplementedError("iOS is not supported yet.");
    }

    if (!alarmSettings.assetAudioPath.contains('.')) {
      throw AlarmException(
        'Provided asset audio file does not have extension: ${alarmSettings.assetAudioPath}',
      );
    }

    // If the same alarm was already set, clear it first
    for (final alarm in await Alarm.getAlarms()) {
      if (alarm.id == alarmSettings.id ||
          (alarm.dateTime.day == alarmSettings.dateTime.day &&
              alarm.dateTime.hour == alarmSettings.dateTime.hour &&
              alarm.dateTime.minute == alarmSettings.dateTime.minute)) {
        await Alarm.stop(alarm.id);
      }
    }

    return await platformSpecificAlarm.set(alarmSettings);
  }

  /// When the app is killed, all the processes are terminated
  /// so the alarm may never ring. By default, to warn the user, a notification
  /// is shown at the moment he kills the app.
  /// This methods allows you to customize this notification content.
  ///
  /// [title] default value is `Your alarm may not ring`
  ///
  /// [body] default value is `You killed the app. Please reopen so your alarm can ring.`
  static Future<void> setNotificationOnAppKillContent(
    String title,
    String body,
  ) =>
      AlarmStorage.setNotificationContentOnAppKill(title, body);

  /// Stops alarm.
  static Future<bool> stop(int id) => platformSpecificAlarm.stop(id);

  /// Stops all the alarms.
  static Future<bool> stopAll() async {
    final alarms = await AlarmStorage.getSavedAlarms();

    bool allStopped = true;
    for (final alarm in alarms) {
      allStopped &= await stop(alarm.id);
    }
    return allStopped;
  }

  /// Snoozes alarm.
  static Future<bool> snooze(int id) => platformSpecificAlarm.snooze(id);

  /// Snoozes all the alarms.
  static Future<bool> snoozeAll() async {
    final alarms = await AlarmStorage.getSavedAlarms();

    bool allSnoozed = true;
    for (final alarm in alarms) {
      allSnoozed &= await snooze(alarm.id);
    }
    return allSnoozed;
  }

  /// Whether the alarm is ringing.
  static Future<bool> isRinging({int id = 0}) =>
      platformSpecificAlarm.isRinging(id);

  /// Get the ringing alarm's [AlarmSetting] or `null` if none is ringing.
  /// NOTE: if there are multiple alarms ringing, only one will be returned.
  static Future<AlarmSettings?> getRingingAlarm() async {
    final ids = await platformSpecificAlarm.getRingingIds();
    if (ids.isEmpty) {
      return null;
    } else {
      return await getAlarm(ids.first);
    }
  }

  /// Whether an alarm is set.
  static Future<bool> hasAlarm() => AlarmStorage.hasAlarm();

  /// Returns alarm by given id. Returns null if not found.
  static Future<AlarmSettings?> getAlarm(int id) async {
    List<AlarmSettings> alarms = await AlarmStorage.getSavedAlarms();

    for (final alarm in alarms) {
      if (alarm.id == id) return alarm;
    }

    alarmPrint('Alarm with id $id not found.');
    return null;
  }

  /// Returns all the alarms.
  static Future<List<AlarmSettings>> getAlarms() =>
      AlarmStorage.getSavedAlarms();

  /// Saves the alarm in local storage.
  static Future<bool> saveAlarm(AlarmSettings settings) =>
      AlarmStorage.saveAlarm(settings);

  /// Remove the alarm from local storage.
  static Future<bool> deleteAlarm(int id) => AlarmStorage.unsaveAlarm(id);
}

class AlarmException implements Exception {
  final String message;

  const AlarmException(this.message);

  @override
  String toString() => message;
}
