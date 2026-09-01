package tech.quantanex.taskmanager.persistence

import tech.quantanex.taskmanager.domain.InboxTask
import tech.quantanex.taskmanager.domain.ReminderAt
import tech.quantanex.taskmanager.domain.ReminderDeliveryStatus
import tech.quantanex.taskmanager.domain.TaskId
import tech.quantanex.taskmanager.domain.TaskIdGenerator
import tech.quantanex.taskmanager.domain.TaskRepository
import tech.quantanex.taskmanager.domain.TaskRepositoryError
import tech.quantanex.taskmanager.domain.TaskTitle
import tech.quantanex.taskmanager.persistence.db.TaskDao
import tech.quantanex.taskmanager.persistence.db.TaskEntity
import java.time.Instant

class RoomTaskRepository(
    private val taskDao: TaskDao,
    private val idGenerator: TaskIdGenerator,
) : TaskRepository {
    override fun tryCreate(title: String, reminderAt: ReminderAt?): TaskRepositoryError? {
        val cleanTitle = title.trim()
        val validationError = validate(cleanTitle, reminderAt)
        if (validationError != null) return validationError

        taskDao.insert(
            TaskEntity(
                id = idGenerator.nextId().value,
                title = cleanTitle,
                isCompleted = false,
                reminderEpochMillis = reminderAt?.instant?.toEpochMilli(),
                reminderDeliveryState = reminderAt.initialDeliveryState().name,
            )
        )
        return null
    }

    override fun create(title: String, reminderAt: ReminderAt?): InboxTask {
        val cleanTitle = title.trim()
        val error = tryCreate(cleanTitle, reminderAt)
        if (error != null) throw IllegalArgumentException(error.toString())
        return taskDao.listInbox().last().toDomain()
    }

    override fun get(id: TaskId): InboxTask? = taskDao.get(id.value)?.toDomain()

    override fun listInbox(): List<InboxTask> = taskDao.listInbox().map { it.toDomain() }

    override fun tryEdit(id: TaskId, title: String, reminderAt: ReminderAt?): TaskRepositoryError? {
        val current = taskDao.get(id.value) ?: return TaskRepositoryError.NotFound(id)
        val cleanTitle = title.trim()
        val validationError = validate(cleanTitle, reminderAt)
        if (validationError != null) return validationError

        val reminderEpochMillis = reminderAt?.instant?.toEpochMilli()
        taskDao.update(
            current.copy(
                title = cleanTitle,
                reminderEpochMillis = reminderEpochMillis,
                reminderDeliveryState = when {
                    reminderEpochMillis == null -> ReminderDeliveryStatus.NoReminder.name
                    reminderEpochMillis == current.reminderEpochMillis -> current.reminderDeliveryState
                    else -> ReminderDeliveryStatus.EncryptedStateUnavailable.name
                },
            )
        )
        return null
    }

    override fun edit(id: TaskId, title: String, reminderAt: ReminderAt?): InboxTask {
        val error = tryEdit(id, title, reminderAt)
        if (error != null) throw IllegalArgumentException(error.toString())
        return taskDao.get(id.value)!!.toDomain()
    }

    override fun tryComplete(id: TaskId): TaskRepositoryError? = updateTask(id) { it.copy(isCompleted = true) }

    override fun complete(id: TaskId): InboxTask {
        val error = tryComplete(id)
        if (error != null) throw IllegalArgumentException(error.toString())
        return taskDao.get(id.value)!!.toDomain()
    }

    override fun tryUndoCompletion(id: TaskId): TaskRepositoryError? = updateTask(id) { it.copy(isCompleted = false) }

    override fun undoCompletion(id: TaskId): InboxTask {
        val error = tryUndoCompletion(id)
        if (error != null) throw IllegalArgumentException(error.toString())
        return taskDao.get(id.value)!!.toDomain()
    }

    override fun trySetReminder(id: TaskId, reminderAt: ReminderAt): TaskRepositoryError? {
        if (!reminderAt.isValid) return TaskRepositoryError.InvalidReminder
        return updateTask(id) {
            it.copy(
                reminderEpochMillis = reminderAt.instant.toEpochMilli(),
                reminderDeliveryState = ReminderDeliveryStatus.EncryptedStateUnavailable.name,
            )
        }
    }

    override fun setReminder(id: TaskId, reminderAt: ReminderAt): InboxTask {
        val error = trySetReminder(id, reminderAt)
        if (error != null) throw IllegalArgumentException(error.toString())
        return taskDao.get(id.value)!!.toDomain()
    }

    override fun tryRemoveReminder(id: TaskId): TaskRepositoryError? = updateTask(id) {
        it.copy(
            reminderEpochMillis = null,
            reminderDeliveryState = ReminderDeliveryStatus.NoReminder.name,
        )
    }

    override fun removeReminder(id: TaskId): InboxTask {
        val error = tryRemoveReminder(id)
        if (error != null) throw IllegalArgumentException(error.toString())
        return taskDao.get(id.value)!!.toDomain()
    }

    override fun trySetReminderDeliveryState(id: TaskId, state: ReminderDeliveryStatus): TaskRepositoryError? =
        updateTask(id) { current ->
            current.copy(
                reminderDeliveryState = if (current.reminderEpochMillis == null) {
                    ReminderDeliveryStatus.NoReminder.name
                } else {
                    state.name
                },
            )
        }

    override fun setReminderDeliveryState(id: TaskId, state: ReminderDeliveryStatus): InboxTask {
        val error = trySetReminderDeliveryState(id, state)
        if (error != null) throw IllegalArgumentException(error.toString())
        return taskDao.get(id.value)!!.toDomain()
    }

    override fun tryDelete(id: TaskId): TaskRepositoryError? {
        val current = taskDao.get(id.value) ?: return TaskRepositoryError.NotFound(id)
        taskDao.delete(current)
        return null
    }

    override fun delete(id: TaskId) {
        val error = tryDelete(id)
        if (error != null) throw IllegalArgumentException(error.toString())
    }

    private fun updateTask(id: TaskId, update: (TaskEntity) -> TaskEntity): TaskRepositoryError? {
        val current = taskDao.get(id.value) ?: return TaskRepositoryError.NotFound(id)
        taskDao.update(update(current))
        return null
    }

    private fun validate(title: String, reminderAt: ReminderAt?): TaskRepositoryError? = when {
        title.isBlank() -> TaskRepositoryError.BlankTitle
        reminderAt?.isValid == false -> TaskRepositoryError.InvalidReminder
        else -> null
    }

    private fun TaskEntity.toDomain(): InboxTask = InboxTask(
        id = TaskId(id),
        title = TaskTitle(title),
        isCompleted = isCompleted,
        reminderAt = reminderEpochMillis?.let { ReminderAt(Instant.ofEpochMilli(it)) },
        reminderDeliveryState = deliveryStateFor(reminderEpochMillis, reminderDeliveryState),
    )

    private fun ReminderAt?.initialDeliveryState(): ReminderDeliveryStatus =
        if (this == null) ReminderDeliveryStatus.NoReminder else ReminderDeliveryStatus.EncryptedStateUnavailable

    private fun deliveryStateFor(reminderEpochMillis: Long?, persisted: String): ReminderDeliveryStatus {
        if (reminderEpochMillis == null) return ReminderDeliveryStatus.NoReminder
        return ReminderDeliveryStatus.entries.firstOrNull { it.name == persisted }
            ?: ReminderDeliveryStatus.EncryptedStateUnavailable
    }
}
