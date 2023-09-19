// ignore_for_file: avoid_print

export 'package:alarm/model/alarm_settings.dart';
import 'dart:async';

import 'package:alarm/model/alarm_settings.dart';
import 'package:alarm/model/notification_action.dart';
import 'package:alarm/model/notification_type.dart';
import 'package:alarm/src/ios_alarm.dart';
import 'package:alarm/src/android_alarm.dart';
import 'package:alarm/service/notification.dart';
import 'package:alarm/service/storage.dart';
import 'package:alarm/utils.dart';
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

  /// Stream when bedtime notification is selected.
  static final bedtimeNotificationStream =
      StreamController<NotificationEvent>();

  static bool _initialized = false;
  static bool get initialized => _initialized;

  /// Initializes Alarm services.
  ///
  /// Also calls [checkAlarm] that will reschedule alarms that were set before
  /// app termination.
  ///
  /// Set [showDebugLogs] to `false` to hide all the logs from the plugin.
  static Future<void> init({
    bool showDebugLogs = true,
    DateTime? nowAtStartup,
    String dismissLabel = "Dismiss",
    String snoozeLabel = "Snooze",
  }) async {
    if (_initialized) {
      alarmPrint('Already initialized. Ignoring request to initialize again');
      return;
    }

    alarmPrint = (String? message, {int? wrapWidth}) {
      if (kDebugMode && showDebugLogs) {
        print("[Alarm] $message");
      }
    };

    await AlarmStorage.init();
    await Future.wait([
      if (android) AndroidAlarm.init(),
      AlarmNotification.instance.init(
        snoozeLabel: snoozeLabel,
      ),
    ]);

    // Pipe notification streams
    AlarmNotification.alarmNotificationStream.stream.listen((event) {
      if (!alarmNotificationStream.hasListener) {
        alarmPrint("No listeners for alarm notification!");
      }

      alarmNotificationStream.add(event);

      // When snoozed, reschedule the alarm
      if (event.snoozed) {
        set(
          alarmSettings: event.alarmSettings.copyWith(
            dateTime: DateTime.now().add(event.alarmSettings.snoozeDuration),
          ),
        );
      }
    });
    AlarmNotification.bedtimeNotificationStream.stream.listen((event) {
      if (!bedtimeNotificationStream.hasListener) {
        alarmPrint("No listeners for bedtime notification!");
      }

      bedtimeNotificationStream.add(event);
    });

    await checkAlarm(nowAtStartup);
    _initialized = true;
  }

  /// Checks if some alarms were set on previous session.
  /// If it's the case then reschedules them.
  static Future<void> checkAlarm([DateTime? nowAtStartup]) async {
    nowAtStartup ??= DateTime.now();
    final launchedByNotification =
        await AlarmNotification.checkNotificationLaunchedApp();
    alarmPrint('Notification launched app: $launchedByNotification');

    final alarms = await AlarmStorage.getSavedAlarms();
    for (final alarm in alarms) {
      final now = DateTime.now();
      if (alarm.dateTime.isAfter(now)) {
        alarmPrint(
          'Now: $now; Now (startup): $nowAtStartup; bedtime: ${alarm.bedtime}',
        );
        await set(
          alarmSettings: alarm,
          skipBedtimeNotification:
              alarm.bedtime?.isBefore(nowAtStartup) ?? false,
          nowAtStartup: nowAtStartup,
        );
      } else {
        alarmPrint('Keeping past alarms during initialization');
      }
    }
  }

  /// Schedules an alarm with given [alarmSettings].
  ///
  /// If you set an alarm for the same [dateTime] as an existing one,
  /// the new alarm will replace the existing one.
  ///
  /// Also, schedules notification if [notificationTitle] and [notificationBody]
  /// are not null nor empty.
  static Future<bool> set({
    required AlarmSettings alarmSettings,
    bool skipBedtimeNotification = false,
    DateTime? nowAtStartup,
  }) async {
    if (!alarmSettings.assetAudioPath.contains('.')) {
      throw AlarmException(
        'Provided asset audio file does not have extension: ${alarmSettings.assetAudioPath}',
      );
    }

    for (final alarm in await Alarm.getAlarms()) {
      if (alarm.id == alarmSettings.id ||
          (alarm.dateTime.day == alarmSettings.dateTime.day &&
              alarm.dateTime.hour == alarmSettings.dateTime.hour &&
              alarm.dateTime.minute == alarmSettings.dateTime.minute)) {
        await Alarm.stop(
          alarm.id,
          skipBedtimeNotification: skipBedtimeNotification,
        );
      }
    }

    await AlarmStorage.saveAlarm(alarmSettings);

    // Alarm notification
    if (alarmSettings.notificationTitle?.isNotEmpty == true &&
        alarmSettings.notificationBody?.isNotEmpty == true) {
      await AlarmNotification.scheduleNotification(
        id: alarmSettings.id,
        dateTime: alarmSettings.dateTime,
        title: alarmSettings.notificationTitle!,
        body: alarmSettings.notificationBody!,
        nowAtStartup: nowAtStartup,
        snooze: alarmSettings.snooze ?? false,
        type: NotificationType.alarm,
      );
    }

    // Bedtime notification
    if (!skipBedtimeNotification &&
        alarmSettings.bedtime != null &&
        alarmSettings.bedtimeNotificationTitle?.isNotEmpty == true &&
        alarmSettings.bedtimeNotificationBody?.isNotEmpty == true) {
      await AlarmNotification.scheduleNotification(
        alarmId: alarmSettings.id,
        id: _toBedtimeNotificationId(alarmSettings.id),
        dateTime: alarmSettings.bedtime!,
        title: alarmSettings.bedtimeNotificationTitle!,
        body: alarmSettings.bedtimeNotificationBody!,
        playSound: true,
        enableLights: true,
        nowAtStartup: nowAtStartup,
        type: NotificationType.bedtime,
      );
    }

    if (iOS) {
      return IOSAlarm.setAlarm(
        alarmSettings.id,
        alarmSettings.dateTime,
        () => ringStream.add(alarmSettings),
        alarmSettings.assetAudioPath,
        alarmSettings.loopAudio,
        alarmSettings.vibrate,
        alarmSettings.volumeMax,
        alarmSettings.fadeDuration,
        alarmSettings.enableNotificationOnKill,
      );
    } else if (android) {
      return await AndroidAlarm.set(alarmSettings);
    }

    return false;
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
  static Future<bool> stop(
    int id, {
    bool skipBedtimeNotification = false,
  }) async {
    await AlarmStorage.unsaveAlarm(id);

    AlarmNotification.instance.cancel(id);
    if (!skipBedtimeNotification) {
      AlarmNotification.instance.cancel(_toBedtimeNotificationId(id));
    }

    return iOS ? await IOSAlarm.stopAlarm(id) : await AndroidAlarm.stop(id);
  }

  /// Stops all the alarms.
  static Future<bool> stopAll() async {
    final alarms = await AlarmStorage.getSavedAlarms();

    bool allStopped = true;
    for (final alarm in alarms) {
      allStopped &= await stop(alarm.id);
    }
    return allStopped;
  }

  /// Whether the alarm is ringing.
  ///
  /// TODO(https://go.flat.app/o/6tj4rW/t/Krrnr9KG/engineering/implement-an-alarm-in-ios)
  static Future<bool> isRinging({int? id}) async => iOS
      ? await IOSAlarm.checkIfRinging(id ?? 0)
      : AndroidAlarm.ringing != null;

  /// Get the ringing alarm's [AlarmSetting] or `null` if none is ringing.
  ///
  /// TODO(https://go.flat.app/o/6tj4rW/t/Krrnr9KG/engineering/implement-an-alarm-in-ios)
  static Future<AlarmSettings?> getRingingAlarm() async =>
      iOS ? null : AndroidAlarm.ringing;

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

  /// Returns a unique ID for the bedtime notification
  static _toBedtimeNotificationId(int id) => fastHash('$id-bedtime');
}

class AlarmException implements Exception {
  final String message;

  const AlarmException(this.message);

  @override
  String toString() => message;
}
