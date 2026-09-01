package tech.quantanex.taskmanager.persistence

import tech.quantanex.taskmanager.domain.ReminderAt
import tech.quantanex.taskmanager.domain.ReminderDeliveryStatus
import tech.quantanex.taskmanager.domain.TaskId
import tech.quantanex.taskmanager.domain.TaskIdGenerator
import tech.quantanex.taskmanager.persistence.db.TaskDao
import tech.quantanex.taskmanager.persistence.db.TaskEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomTaskRepositoryTest {
    @Test
    fun corruptReminderDeliveryStateFailsClosedWithoutDroppingPersistedReminder() {
        val dao = FakeTaskDao(
            TaskEntity(
                id = "task-corrupt-marker",
                title = "Encrypted reminder fixture",
                isCompleted = false,
                reminderEpochMillis = Instant.parse("2026-09-01T09:30:00Z").toEpochMilli(),
                reminderDeliveryState = "not-a-valid-state",
            )
        )
        val repository = RoomTaskRepository(dao, TaskIdGenerator { TaskId("unused") })

        val task = repository.get(TaskId("task-corrupt-marker"))

        assertEquals(Instant.parse("2026-09-01T09:30:00Z"), task?.reminderAt?.instant)
        assertEquals(ReminderDeliveryStatus.EncryptedStateUnavailable, task?.reminderDeliveryState)
    }

    @Test
    fun persistsReminderDeliveryStateInsideTaskEntity() {
        val dao = FakeTaskDao(
            TaskEntity(
                id = "task-delivery-state",
                title = "Encrypted reminder fixture",
                isCompleted = false,
                reminderEpochMillis = Instant.parse("2026-09-01T09:30:00Z").toEpochMilli(),
                reminderDeliveryState = ReminderDeliveryStatus.EncryptedStateUnavailable.name,
            )
        )
        val repository = RoomTaskRepository(dao, TaskIdGenerator { TaskId("unused") })

        repository.setReminderDeliveryState(
            TaskId("task-delivery-state"),
            ReminderDeliveryStatus.NotificationPermissionDenied,
        )

        assertEquals(ReminderDeliveryStatus.NotificationPermissionDenied.name, dao.tasks.single().reminderDeliveryState)
    }
}

private class FakeTaskDao(vararg initialTasks: TaskEntity) : TaskDao {
    val tasks = initialTasks.toMutableList()

    override fun insert(task: TaskEntity) {
        tasks += task
    }

    override fun update(task: TaskEntity) {
        val index = tasks.indexOfFirst { it.id == task.id }
        require(index >= 0)
        tasks[index] = task
    }

    override fun delete(task: TaskEntity) {
        tasks.removeIf { it.id == task.id }
    }

    override fun get(id: String): TaskEntity? = tasks.firstOrNull { it.id == id }

    override fun listInbox(): List<TaskEntity> = tasks.toList()
}
