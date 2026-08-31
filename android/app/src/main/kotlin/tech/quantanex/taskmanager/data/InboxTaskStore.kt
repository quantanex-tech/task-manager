package tech.quantanex.taskmanager.data

import android.content.Context
import tech.quantanex.taskmanager.domain.InboxTask
import tech.quantanex.taskmanager.domain.ReminderAt
import tech.quantanex.taskmanager.domain.TaskId
import tech.quantanex.taskmanager.domain.TaskRepository
import tech.quantanex.taskmanager.persistence.EncryptedTaskRepositoryCloseable
import tech.quantanex.taskmanager.persistence.EncryptedTaskRepositoryFactory
import tech.quantanex.taskmanager.persistence.EncryptedTaskRepositoryOpenResult
import tech.quantanex.taskmanager.persistence.TaskStoreOpenError

interface InboxTaskStore : AutoCloseable {
    fun listInbox(): List<InboxTask>
    fun create(title: String): InboxTask
    fun get(id: TaskId): InboxTask?
    fun edit(task: InboxTask, title: String): InboxTask
    fun setReminder(task: InboxTask, reminderAt: ReminderAt): InboxTask
    fun removeReminder(task: InboxTask): InboxTask
    fun complete(id: TaskId): InboxTask
    fun undoCompletion(id: TaskId): InboxTask
    fun delete(id: TaskId)
}

class RepositoryInboxTaskStore(
    private val repository: TaskRepository,
    private val closeable: EncryptedTaskRepositoryCloseable,
) : InboxTaskStore {
    override fun listInbox(): List<InboxTask> = repository.listInbox()

    override fun create(title: String): InboxTask = repository.create(title, reminderAt = null)

    override fun get(id: TaskId): InboxTask? = repository.get(id)

    override fun edit(task: InboxTask, title: String): InboxTask =
        repository.edit(task.id, title, task.reminderAt)

    override fun setReminder(task: InboxTask, reminderAt: ReminderAt): InboxTask =
        repository.edit(task.id, task.title.value, reminderAt)

    override fun removeReminder(task: InboxTask): InboxTask =
        repository.edit(task.id, task.title.value, reminderAt = null)

    override fun complete(id: TaskId): InboxTask = repository.complete(id)

    override fun undoCompletion(id: TaskId): InboxTask = repository.undoCompletion(id)

    override fun delete(id: TaskId) = repository.delete(id)

    override fun close() = closeable.close()
}

sealed interface InboxStoreOpenOutcome {
    data class Opened(val store: InboxTaskStore) : InboxStoreOpenOutcome
    data class Unavailable(val reason: InboxStoreUnavailableReason) : InboxStoreOpenOutcome
}

enum class InboxStoreUnavailableReason {
    KeyBootstrapFailed,
    UnsupportedCipher,
    DatabaseOpenFailed,
}

object EncryptedInboxTaskStoreFactory {
    fun open(context: Context): InboxStoreOpenOutcome = when (
        val result = EncryptedTaskRepositoryFactory.open(context.applicationContext)
    ) {
        is EncryptedTaskRepositoryOpenResult.Success -> InboxStoreOpenOutcome.Opened(
            RepositoryInboxTaskStore(result.repository, result.closeable)
        )
        is EncryptedTaskRepositoryOpenResult.Failure -> InboxStoreOpenOutcome.Unavailable(result.error.toReason())
    }

    private fun TaskStoreOpenError.toReason(): InboxStoreUnavailableReason = when (this) {
        is TaskStoreOpenError.KeyBootstrapFailed -> InboxStoreUnavailableReason.KeyBootstrapFailed
        is TaskStoreOpenError.UnsupportedCipher -> InboxStoreUnavailableReason.UnsupportedCipher
        is TaskStoreOpenError.DatabaseOpenFailed -> InboxStoreUnavailableReason.DatabaseOpenFailed
    }
}
