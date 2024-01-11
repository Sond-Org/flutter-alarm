import 'package:alarm/model/alarm_settings.dart';
import 'package:alarm/platform/base_alarm.dart';

/// iOS implementation of the alarm plugin.
///
/// NOTE: Native-side not implemented yet.
class IOSAlarm extends BaseAlarm {
  @override
  Future<bool> set(AlarmSettings settings) {
    return Future.value(false);
  }

  @override
  Future<bool> snooze(int id) {
    return Future.value(false);
  }

  @override
  Future<bool> stop(int id) async {
    return Future.value(false);
  }

  @override
  Future<bool> isRinging(int id) async {
    return Future.value(false);
  }

  @override
  Future<List<int>> getRingingIds() async {
    return Future.value([]);
  }

  @override
  Future<void> stopNotificationOnKillService() {
    return Future.value();
  }
}
