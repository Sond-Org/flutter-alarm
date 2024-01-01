enum NotificationAction {
  dismiss,
  snooze,
  select;

  /// Tries to parse [name] into a [NotificationAction]. When it fails, defaults
  /// to [NotificationAction.select].
  static NotificationAction from(String? name) {
    final action = NotificationAction.values
        .where(
          (e) => e.name == name,
        )
        .firstOrNull;

    if (action == null) {
      return NotificationAction.select;
    }

    return action;
  }
}
