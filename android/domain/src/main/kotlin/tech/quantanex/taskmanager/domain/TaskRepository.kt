package tech.quantanex.taskmanager.domain

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "TaskId must be opaque and non-blank" }
    }
}

@JvmInline
value class TaskTitle(val value: String) {
    init {
        require(value.isNotBlank()) { "Task title must not be blank" }
    }
}

data class ReminderAt internal constructor(
    val instant: Instant,
    val isValid: Boolean,
) {
    constructor(instant: Instant) : this(instant, true)

    companion object {
        fun fromEpochMilliseconds(epochMilliseconds: Long): ReminderAt =
            ReminderAt(Instant.ofEpochMilli(epochMilliseconds.coerceAtLeast(0)), epochMilliseconds >= 0)
    }
}

data class InboxTask(
    val id: TaskId,
    val title: TaskTitle,
    val isCompleted: Boolean,
    val reminderAt: ReminderAt?,
)

sealed interface TaskRepositoryError {
    data object BlankTitle : TaskRepositoryError
    data object InvalidReminder : TaskRepositoryError
    data class NotFound(val id: TaskId) : TaskRepositoryError
}

fun interface TaskIdGenerator {
    fun nextId(): TaskId
}

class SequentialSyntheticTaskIdGenerator : TaskIdGenerator {
    private companion object {
        val nextValue = AtomicInteger(1)
    }

    override fun nextId(): TaskId = TaskId("synthetic-task-${nextValue.getAndIncrement()}")
}

interface TaskRepository {
    fun create(title: String, reminderAt: ReminderAt?): InboxTask

    fun tryCreate(title: String, reminderAt: ReminderAt?): TaskRepositoryError?

    fun get(id: TaskId): InboxTask?

    fun listInbox(): List<InboxTask>

    fun edit(id: TaskId, title: String, reminderAt: ReminderAt?): InboxTask

    fun tryEdit(id: TaskId, title: String, reminderAt: ReminderAt?): TaskRepositoryError?

    fun complete(id: TaskId): InboxTask

    fun tryComplete(id: TaskId): TaskRepositoryError?

    fun undoCompletion(id: TaskId): InboxTask

    fun tryUndoCompletion(id: TaskId): TaskRepositoryError?

    fun setReminder(id: TaskId, reminderAt: ReminderAt): InboxTask

    fun trySetReminder(id: TaskId, reminderAt: ReminderAt): TaskRepositoryError?

    fun removeReminder(id: TaskId): InboxTask

    fun tryRemoveReminder(id: TaskId): TaskRepositoryError?

    fun delete(id: TaskId)

    fun tryDelete(id: TaskId): TaskRepositoryError?
}
