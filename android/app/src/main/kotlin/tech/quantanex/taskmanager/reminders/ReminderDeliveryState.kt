package tech.quantanex.taskmanager.reminders

import tech.quantanex.taskmanager.domain.InboxTask
import tech.quantanex.taskmanager.domain.ReminderDeliveryStatus
import java.time.Instant

typealias ReminderDeliveryState = ReminderDeliveryStatus

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
        val state = scheduler.schedule(task)
        if (state == ReminderDeliveryState.ExactAlarmUnavailable) {
            scheduler.cancel(task)
        }
        return state
    }

    fun cancel(task: InboxTask) = scheduler.cancel(task)
}

interface ReminderStateStore {
    fun listReminderTasks(): List<InboxTask>
    fun persistReminderDeliveryState(task: InboxTask, state: ReminderDeliveryState)
}

class LocalReminderRecovery(
    private val coordinator: LocalReminderCoordinator,
    private val store: ReminderStateStore,
) {
    fun recover() {
        store.listReminderTasks().forEach { task ->
            store.persistReminderDeliveryState(task, coordinator.reconcile(task))
        }
    }
}

data class ReminderNotificationRequest(
    val task: InboxTask,
    val dueAt: Instant,
)

class LocalReminderAlarmDelivery {
    fun notificationsFor(
        tasks: List<InboxTask>,
        alarmReminderId: Int?,
        dueAt: Instant,
    ): List<ReminderNotificationRequest> = tasks.filter { task ->
        val reminderAt = task.reminderAt?.instant
        reminderAt != null &&
            !reminderAt.isAfter(dueAt) &&
            alarmReminderId == OpaqueReminderIds.forTask(task)
    }.map { task ->
        ReminderNotificationRequest(task = task, dueAt = task.reminderAt!!.instant)
    }
}
