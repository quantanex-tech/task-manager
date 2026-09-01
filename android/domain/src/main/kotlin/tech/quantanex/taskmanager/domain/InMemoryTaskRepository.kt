package tech.quantanex.taskmanager.domain

class InMemoryTaskRepository(
    private val idGenerator: TaskIdGenerator = SequentialSyntheticTaskIdGenerator(),
) : TaskRepository {
    private val tasksById = LinkedHashMap<TaskId, InboxTask>()

    override fun tryCreate(title: String, reminderAt: ReminderAt?): TaskRepositoryError? {
        val cleanTitle = title.trim()
        val validationError = validate(cleanTitle, reminderAt)
        if (validationError != null) return validationError

        val task = InboxTask(
            id = idGenerator.nextId(),
            title = TaskTitle(cleanTitle),
            isCompleted = false,
            reminderAt = reminderAt,
        )
        tasksById[task.id] = task
        return null
    }

    override fun create(title: String, reminderAt: ReminderAt?): InboxTask {
        val cleanTitle = title.trim()
        val error = tryCreate(cleanTitle, reminderAt)
        if (error != null) throw IllegalArgumentException(error.toString())
        return tasksById.values.last()
    }

    override fun get(id: TaskId): InboxTask? = tasksById[id]

    override fun listInbox(): List<InboxTask> = tasksById.values.toList()

    override fun tryEdit(id: TaskId, title: String, reminderAt: ReminderAt?): TaskRepositoryError? {
        val current = tasksById[id] ?: return TaskRepositoryError.NotFound(id)
        val cleanTitle = title.trim()
        val validationError = validate(cleanTitle, reminderAt)
        if (validationError != null) return validationError

        tasksById[id] = current.copy(
            title = TaskTitle(cleanTitle),
            reminderAt = reminderAt,
            reminderDeliveryState = if (reminderAt == null) {
                ReminderDeliveryStatus.NoReminder
            } else if (current.reminderAt == reminderAt) {
                current.reminderDeliveryState
            } else {
                ReminderDeliveryStatus.EncryptedStateUnavailable
            },
        )
        return null
    }

    override fun edit(id: TaskId, title: String, reminderAt: ReminderAt?): InboxTask {
        val error = tryEdit(id, title, reminderAt)
        if (error != null) throw IllegalArgumentException(error.toString())
        return tasksById.getValue(id)
    }

    override fun tryComplete(id: TaskId): TaskRepositoryError? = updateTask(id) { it.copy(isCompleted = true) }

    override fun complete(id: TaskId): InboxTask {
        val error = tryComplete(id)
        if (error != null) throw IllegalArgumentException(error.toString())
        return tasksById.getValue(id)
    }

    override fun tryUndoCompletion(id: TaskId): TaskRepositoryError? = updateTask(id) { it.copy(isCompleted = false) }

    override fun undoCompletion(id: TaskId): InboxTask {
        val error = tryUndoCompletion(id)
        if (error != null) throw IllegalArgumentException(error.toString())
        return tasksById.getValue(id)
    }

    override fun trySetReminder(id: TaskId, reminderAt: ReminderAt): TaskRepositoryError? {
        if (!reminderAt.isValid) return TaskRepositoryError.InvalidReminder
        return updateTask(id) {
            it.copy(
                reminderAt = reminderAt,
                reminderDeliveryState = ReminderDeliveryStatus.EncryptedStateUnavailable,
            )
        }
    }

    override fun setReminder(id: TaskId, reminderAt: ReminderAt): InboxTask {
        val error = trySetReminder(id, reminderAt)
        if (error != null) throw IllegalArgumentException(error.toString())
        return tasksById.getValue(id)
    }

    override fun tryRemoveReminder(id: TaskId): TaskRepositoryError? = updateTask(id) {
        it.copy(reminderAt = null, reminderDeliveryState = ReminderDeliveryStatus.NoReminder)
    }

    override fun removeReminder(id: TaskId): InboxTask {
        val error = tryRemoveReminder(id)
        if (error != null) throw IllegalArgumentException(error.toString())
        return tasksById.getValue(id)
    }

    override fun trySetReminderDeliveryState(id: TaskId, state: ReminderDeliveryStatus): TaskRepositoryError? =
        updateTask(id) { current ->
            current.copy(
                reminderDeliveryState = if (current.reminderAt == null) ReminderDeliveryStatus.NoReminder else state,
            )
        }

    override fun setReminderDeliveryState(id: TaskId, state: ReminderDeliveryStatus): InboxTask {
        val error = trySetReminderDeliveryState(id, state)
        if (error != null) throw IllegalArgumentException(error.toString())
        return tasksById.getValue(id)
    }

    override fun tryDelete(id: TaskId): TaskRepositoryError? =
        if (tasksById.remove(id) == null) TaskRepositoryError.NotFound(id) else null

    override fun delete(id: TaskId) {
        val error = tryDelete(id)
        if (error != null) throw IllegalArgumentException(error.toString())
    }

    private fun updateTask(id: TaskId, update: (InboxTask) -> InboxTask): TaskRepositoryError? {
        val current = tasksById[id] ?: return TaskRepositoryError.NotFound(id)
        tasksById[id] = update(current)
        return null
    }

    private fun validate(title: String, reminderAt: ReminderAt?): TaskRepositoryError? = when {
        title.isBlank() -> TaskRepositoryError.BlankTitle
        reminderAt?.isValid == false -> TaskRepositoryError.InvalidReminder
        else -> null
    }
}
