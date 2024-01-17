import 'package:alarm/model/alarm_settings.dart';
import 'package:alarm/platform/base_alarm_storage.dart';

/// iOS implementation of the alarm storage plugin.
///
/// NOTE: Native-side not implemented yet.
class IOSAlarmStorage extends BaseAlarmStorage {
  @override
  Future<bool> saveAlarm(AlarmSettings alarmSettings) {
    return Future.value(false);
  }

  @override
  Future<bool> unsaveAlarm(int id) {
    return Future.value(false);
  }

  @override
  Future<bool> hasAlarm() {
    return Future.value(false);
  }

  @override
  Future<List<AlarmSettings>> getSavedAlarms() {
    return Future.value([]);
  }

  @override
  Future<bool> setNotificationContentOnAppKill(String title, String body) {
    return Future.value(false);
  }

  @override
  Future<String> getNotificationOnAppKillTitle() {
    return Future.value('');
  }

  @override
  Future<String> getNotificationOnAppKillBody() {
    return Future.value('');
  }
}
