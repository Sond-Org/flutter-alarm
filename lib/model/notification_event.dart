import 'package:alarm/model/notification_action.dart';

import 'alarm_settings.dart';

class NotificationEvent {
  NotificationEvent(this.alarmSettings, this.action);
  final AlarmSettings alarmSettings;
  final NotificationAction action;
}
