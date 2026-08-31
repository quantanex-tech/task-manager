package tech.quantanex.taskmanager.reminders

import tech.quantanex.taskmanager.domain.InboxTask
import java.time.Instant

enum class ReminderDeliveryState {
    NoReminder,
    Scheduled,
    NotificationPermissionDenied,
    ExactAlarmUnavailable,
    EncryptedStateUnavailable,
}

interface NotificationPermissionGate {
    fun canPostNotifications(): Boolean
}

interface ExactReminderScheduler {
    fun schedule(task: InboxTask): ReminderDeliveryState
    fun cancel(task: InboxTask)
}

interface ReminderNotificationRenderer {
    fun render(task: InboxTask?, dueAt: Instant)
}

class LocalReminderCoordinator(
    private val notificationPermissionGate: NotificationPermissionGate,
    private val scheduler: ExactReminderScheduler,
) {
    fun reconcile(task: InboxTask): ReminderDeliveryState {
        if (task.reminderAt == null) {
            scheduler.cancel(task)
            return ReminderDeliveryState.NoReminder
        }
        if (!notificationPermissionGate.canPostNotifications()) {
            scheduler.cancel(task)
            return ReminderDeliveryState.NotificationPermissionDenied
        }
        return scheduler.schedule(task)
    }

    fun cancel(task: InboxTask) = scheduler.cancel(task)
}
