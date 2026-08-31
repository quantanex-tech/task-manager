package tech.quantanex.taskmanager.reminders

import tech.quantanex.taskmanager.domain.InboxTask
import tech.quantanex.taskmanager.domain.ReminderAt
import tech.quantanex.taskmanager.domain.TaskId
import tech.quantanex.taskmanager.domain.TaskTitle
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalReminderCoordinatorTest {
    @Test
    fun schedulesExactReminderOnlyWhenNotificationPermissionIsGranted() {
        val scheduler = RecordingScheduler()
        val task = fixtureTask(ReminderAt(Instant.parse("2026-09-01T09:30:00Z")))

        val result = LocalReminderCoordinator(AllowedNotifications, scheduler).reconcile(task)

        assertEquals(ReminderDeliveryState.Scheduled, result)
        assertEquals(listOf(task), scheduler.scheduled)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun permissionDeniedCancelsAnyExistingAlarmAndReturnsVisibleDisabledState() {
        val scheduler = RecordingScheduler()
        val task = fixtureTask(ReminderAt(Instant.parse("2026-09-01T09:30:00Z")))

        val result = LocalReminderCoordinator(DeniedNotifications, scheduler).reconcile(task)

        assertEquals(ReminderDeliveryState.NotificationPermissionDenied, result)
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(task), scheduler.cancelled)
    }

    @Test
    fun exactAlarmUnavailablePropagatesTypedDegradedStateWithoutFallback() {
        val scheduler = RecordingScheduler(scheduleResult = ReminderDeliveryState.ExactAlarmUnavailable)
        val task = fixtureTask(ReminderAt(Instant.parse("2026-09-01T09:30:00Z")))

        val result = LocalReminderCoordinator(AllowedNotifications, scheduler).reconcile(task)

        assertEquals(ReminderDeliveryState.ExactAlarmUnavailable, result)
        assertEquals(listOf(task), scheduler.scheduled)
        assertEquals(listOf(task), scheduler.cancelled)
    }

    @Test
    fun recoveryPersistsEveryReconciledReminderDeliveryState() {
        val scheduler = RecordingScheduler(scheduleResult = ReminderDeliveryState.ExactAlarmUnavailable)
        val task = fixtureTask(ReminderAt(Instant.parse("2026-09-01T09:30:00Z")))
        val store = RecordingReminderStateStore(listOf(task))

        LocalReminderRecovery(
            coordinator = LocalReminderCoordinator(AllowedNotifications, scheduler),
            store = store,
        ).recover()

        assertEquals(listOf(task.id to ReminderDeliveryState.ExactAlarmUnavailable), store.persistedStates)
        assertEquals(listOf(task), scheduler.cancelled)
    }

    @Test
    fun dueAlarmWithNoMatchingExtantReminderIsSuppressed() {
        val dueAt = Instant.parse("2026-09-01T09:30:00Z")
        val task = fixtureTask(ReminderAt(dueAt))

        val notifications = LocalReminderAlarmDelivery().notificationsFor(
            tasks = listOf(task),
            alarmReminderId = OpaqueReminderIds.forTask(task) + 1,
            dueAt = dueAt,
        )

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun missingReminderCancelsAlarmAndReportsNoReminder() {
        val scheduler = RecordingScheduler()
        val task = fixtureTask(reminderAt = null)

        val result = LocalReminderCoordinator(AllowedNotifications, scheduler).reconcile(task)

        assertEquals(ReminderDeliveryState.NoReminder, result)
        assertEquals(listOf(task), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    private fun fixtureTask(reminderAt: ReminderAt?): InboxTask = InboxTask(
        id = TaskId("synthetic-reminder-task"),
        title = TaskTitle("Synthetic reminder task"),
        isCompleted = false,
        reminderAt = reminderAt,
    )

    private object AllowedNotifications : NotificationPermissionGate {
        override fun canPostNotifications(): Boolean = true
    }

    private object DeniedNotifications : NotificationPermissionGate {
        override fun canPostNotifications(): Boolean = false
    }

    private class RecordingScheduler(
        private val scheduleResult: ReminderDeliveryState = ReminderDeliveryState.Scheduled,
    ) : ExactReminderScheduler {
        val scheduled = mutableListOf<InboxTask>()
        val cancelled = mutableListOf<InboxTask>()

        override fun schedule(task: InboxTask): ReminderDeliveryState {
            scheduled += task
            return scheduleResult
        }

        override fun cancel(task: InboxTask) {
            cancelled += task
        }
    }

    private class RecordingReminderStateStore(
        private val tasks: List<InboxTask>,
    ) : ReminderStateStore {
        val persistedStates = mutableListOf<Pair<TaskId, ReminderDeliveryState>>()

        override fun listReminderTasks(): List<InboxTask> = tasks

        override fun persistReminderDeliveryState(task: InboxTask, state: ReminderDeliveryState) {
            persistedStates += task.id to state
        }
    }
}
