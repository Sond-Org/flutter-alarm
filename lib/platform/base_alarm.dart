import 'package:alarm/model/notification_action.dart';
import 'package:alarm/model/notification_event.dart';
import 'package:alarm/service/alarm.dart';
import 'package:alarm/service/storage.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

abstract class BaseAlarm {
  static const platform = MethodChannel('com.gdelataillade.alarm/alarm');

  Future<bool> get hasOtherAlarms async =>
      (await AlarmStorage.getSavedAlarms()).length > 1;

  Future<void> init() async {
    platform.setMethodCallHandler(handleMethodCall);
  }

  Future<dynamic> handleMethodCall(MethodCall call) async {
    int id = call.arguments['id'];
    final settings = await Alarm.getAlarm(id);
    if (settings == null) {
      alarmPrint(
        'Handle method call "${call.method}" error: Alarm $id not found',
      );
      return;
    }

    try {
      switch (call.method) {
        case 'alarmRinging':
          alarmPrint('Alarm $id is ringing');
          Alarm.ringStream.add(settings);
          break;

        case 'alarmDismissed':
          alarmPrint('Alarm $id stopped');
          Alarm.alarmNotificationStream.add(
            NotificationEvent(settings, NotificationAction.dismiss),
          );
          break;

        case 'alarmSnoozed':
          alarmPrint('Alarm $id snoozed');
          Alarm.alarmNotificationStream.add(
            NotificationEvent(settings, NotificationAction.snooze),
          );
          break;

        default:
          alarmPrint(
            'Handle method call "${call.method}" error: Unknown method',
          );
      }
    } catch (e) {
      alarmPrint('Handle method call "${call.method}" error: $e');
    }
  }

  /// Checks if the app has the required permissions.
  /// If not, it will request them.
  Future<PermissionStatus> checkPermissions() async {
    try {
      var ignoreBatteryOptimizationPermission =
          await Permission.ignoreBatteryOptimizations.request();
      if (ignoreBatteryOptimizationPermission.isDenied) {
        alarmPrint(
          'Permission to ignore battery optimization not granted. Alarm may trigger with up to 15 minute delay due to Android Doze optimization',
        );
      }
      final scheduleExactAlarmPermission =
          await Permission.scheduleExactAlarm.request();
      if (scheduleExactAlarmPermission.isDenied) {
        alarmPrint(
          'Permission to schedule exact alarm not granted. Alarm may not trigger at the exact time it is scheduled for',
        );
      }
      // TODO(system-alert): Decide if this is something we want to do
      // final systemAlertWindowPermission = await Permission.systemAlertWindow.request();
      // if (systemAlertWindowPermission.isDenied) {
      //   alarmPrint(
      //     'Permission to show system alert window not granted. Alarm will not open the app when it is ringing',
      //   );
      // }
      return ignoreBatteryOptimizationPermission.isGranted &&
              scheduleExactAlarmPermission.isGranted
          ? PermissionStatus.granted
          : PermissionStatus.denied;
    } catch (e) {
      alarmPrint(
        'Failed to request for permissions to ignore battery optimization and schedule an exact alarm. $e',
      );
      return PermissionStatus.denied;
    }
  }

  /// Schedules a native alarm with given [alarmSettings] with its notification.
  Future<bool> set(AlarmSettings settings) async {
    await checkPermissions();

    try {
      await platform.invokeMethod(
        'setAlarm',
        settings.toJson(),
      );
    } catch (e) {
      throw AlarmException('Failed to schedule alarm ${settings.id}. $e');
    }

    if (settings.enableNotificationOnKill && !await hasOtherAlarms) {
      try {
        await platform.invokeMethod(
          'setNotificationOnKillService',
          {
            'title': await AlarmStorage.getNotificationOnAppKillTitle(),
            'body': await AlarmStorage.getNotificationOnAppKillBody(),
          },
        );
      } catch (e) {
        throw AlarmException(
          'Failed to schedule notification-on-kill service. $e',
        );
      }
    }

    alarmPrint('Alarm with id ${settings.id} scheduled');
    return true;
  }

  /// Snoozes the alarm with given [id].
  Future<bool> snooze(int id) async {
    var alarmSettings = await Alarm.getAlarm(id);
    if (alarmSettings == null) {
      alarmPrint('Failed to snooze alarm $id - not found');
      return false;
    }

    alarmSettings = alarmSettings.copyWith(
      dateTime: alarmSettings.nextSnoozeDateTime(),
    );
    try {
      return await platform.invokeMethod(
        'snoozeAlarm',
        alarmSettings.toJson(),
      );
    } catch (e) {
      throw AlarmException('Failed to snooze alarm $id. $e');
    }
  }

  /// Stops the alarm with given [id].
  Future<bool> stop(int id) async {
    late final bool success;
    try {
      success =
          await platform.invokeMethod<bool>('stopAlarm', {'id': id}) ?? false;
    } catch (e) {
      throw AlarmException('Failed to stop alarm $id. $e');
    }

    if (success) {
      alarmPrint('Alarm with id $id stopped');
    } else {
      alarmPrint('Failed to stop alarm $id - not found');
    }

    if (!await hasOtherAlarms) {
      stopNotificationOnKillService();
    }

    return success;
  }

  /// Checks if the alarm with given [id] is ringing.
  Future<bool> isRinging(int id) async {
    try {
      return await platform.invokeMethod<bool>('isRinging', {'id': id}) ??
          false;
    } catch (e) {
      throw AlarmException('Failed to check if alarm $id is ringing. $e');
    }
  }

  /// Get all the ids of the alarms that are ringing.
  Future<List<int>> getRingingIds() async {
    try {
      final result = await platform.invokeMethod<List<Object?>>('getRingIds');
      return result?.cast<int>().toList() ?? [];
    } catch (e) {
      throw AlarmException('Failed to get all the alarms that are ringing. $e');
    }
  }

  /// Stop the notification on kill service.
  Future<void> stopNotificationOnKillService() async {
    try {
      await platform.invokeMethod('stopNotificationOnKillService');
    } catch (e) {
      throw AlarmException(
        'Failed to stop the notification-on-kill service. $e',
      );
    }
  }
}
