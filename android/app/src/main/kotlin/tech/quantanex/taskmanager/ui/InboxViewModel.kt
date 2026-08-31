package tech.quantanex.taskmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.quantanex.taskmanager.data.InboxStoreOpenOutcome
import tech.quantanex.taskmanager.data.InboxTaskStore
import tech.quantanex.taskmanager.domain.InboxTask
import tech.quantanex.taskmanager.domain.ReminderAt
import tech.quantanex.taskmanager.domain.TaskId
import tech.quantanex.taskmanager.reminders.LocalReminderCoordinator
import tech.quantanex.taskmanager.reminders.ReminderDeliveryState
import java.time.Instant
import java.time.format.DateTimeParseException

private const val STORE_UNAVAILABLE_MESSAGE = "Encrypted local storage is unavailable. Unsaved draft text stays only on this screen."
private const val REMINDER_TIME_HELP = "Use an exact UTC time like 2026-09-01T09:30:00Z."

data class InboxUiState(
    val isLoading: Boolean = true,
    val draftTitle: String = "",
    val tasks: List<InboxTask> = emptyList(),
    val selectedTask: InboxTask? = null,
    val editTitle: String = "",
    val editReminderText: String = "",
    val pendingDeleteTaskId: TaskId? = null,
    val validationMessage: String? = null,
    val storeErrorMessage: String? = null,
    val reminderDeliveryState: ReminderDeliveryState = ReminderDeliveryState.NoReminder,
    val shouldRequestNotificationPermission: Boolean = false,
) {
    val hasTasks: Boolean = tasks.isNotEmpty()
}

class InboxViewModel(
    private val storeProvider: suspend () -> InboxStoreOpenOutcome,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val reminderCoordinator: LocalReminderCoordinator? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = mutableState

    private var store: InboxTaskStore? = null

    init {
        openStoreAndRefresh()
    }

    fun retryOpenStore() = openStoreAndRefresh()

    fun updateDraftTitle(title: String) {
        mutableState.update { it.copy(draftTitle = title, validationMessage = null) }
    }

    fun createTask() {
        val title = state.value.draftTitle
        if (title.isBlank()) {
            mutableState.update { it.copy(validationMessage = "Enter a task title before saving.") }
            return
        }
        runStoreAction(clearStoreError = true) { activeStore ->
            activeStore.create(title)
            refreshFrom(activeStore).copy(draftTitle = "", validationMessage = null)
        }
    }

    fun selectTask(id: TaskId) {
        val task = state.value.tasks.firstOrNull { it.id == id } ?: return
        mutableState.update {
            it.copy(
                selectedTask = task,
                editTitle = task.title.value,
                editReminderText = task.reminderAt?.instant?.toString().orEmpty(),
                pendingDeleteTaskId = null,
                validationMessage = null,
                reminderDeliveryState = deliveryStateFor(task),
                shouldRequestNotificationPermission = false,
            )
        }
    }

    fun updateEditTitle(title: String) {
        mutableState.update { it.copy(editTitle = title, validationMessage = null) }
    }

    fun updateEditReminderText(reminderText: String) {
        mutableState.update { it.copy(editReminderText = reminderText, validationMessage = null) }
    }

    fun saveSelectedTask() {
        val current = state.value.selectedTask ?: return
        val title = state.value.editTitle
        if (title.isBlank()) {
            mutableState.update { it.copy(validationMessage = "Task title cannot be blank.") }
            return
        }
        runStoreAction { activeStore ->
            val edited = activeStore.edit(current, title)
            refreshFrom(activeStore).withSelectedTask(edited)
        }
    }

    fun saveSelectedReminder() {
        val current = state.value.selectedTask ?: return
        val reminder = parseReminderOrUpdateState(state.value.editReminderText) ?: return
        runStoreAction { activeStore ->
            val withReminder = activeStore.setReminder(current, reminder)
            val deliveryState = reminderCoordinator?.reconcile(withReminder) ?: ReminderDeliveryState.EncryptedStateUnavailable
            val persistedReminder = activeStore.setReminderDeliveryState(withReminder, deliveryState)
            refreshFrom(activeStore).withSelectedTask(persistedReminder).copy(
                reminderDeliveryState = deliveryState,
                shouldRequestNotificationPermission = deliveryState == ReminderDeliveryState.NotificationPermissionDenied,
                validationMessage = null,
            )
        }
    }

    fun removeSelectedReminder() {
        val current = state.value.selectedTask ?: return
        runStoreAction { activeStore ->
            reminderCoordinator?.cancel(current)
            val withoutReminder = activeStore.removeReminder(current)
            refreshFrom(activeStore).withSelectedTask(withoutReminder).copy(
                editReminderText = "",
                reminderDeliveryState = ReminderDeliveryState.NoReminder,
                shouldRequestNotificationPermission = false,
                validationMessage = null,
            )
        }
    }

    fun onNotificationPermissionResult() {
        val current = state.value.selectedTask ?: return
        if (current.reminderAt == null) {
            mutableState.update { it.copy(shouldRequestNotificationPermission = false) }
            return
        }
        runStoreAction { activeStore ->
            val refreshed = activeStore.get(current.id) ?: current
            val deliveryState = reminderCoordinator?.reconcile(refreshed) ?: ReminderDeliveryState.EncryptedStateUnavailable
            val persistedReminder = activeStore.setReminderDeliveryState(refreshed, deliveryState)
            refreshFrom(activeStore).withSelectedTask(persistedReminder).copy(
                reminderDeliveryState = deliveryState,
                shouldRequestNotificationPermission = false,
            )
        }
    }

    fun completeSelectedTask() {
        val current = state.value.selectedTask ?: return
        runStoreAction { activeStore ->
            val completed = activeStore.complete(current.id)
            refreshFrom(activeStore).withSelectedTask(completed)
        }
    }

    fun undoSelectedTaskCompletion() {
        val current = state.value.selectedTask ?: return
        runStoreAction { activeStore ->
            val restored = activeStore.undoCompletion(current.id)
            refreshFrom(activeStore).withSelectedTask(restored)
        }
    }

    fun requestDeleteSelectedTask() {
        val current = state.value.selectedTask ?: return
        mutableState.update { it.copy(pendingDeleteTaskId = current.id) }
    }

    fun cancelDelete() {
        mutableState.update { it.copy(pendingDeleteTaskId = null) }
    }

    fun confirmDeleteSelectedTask() {
        val current = state.value.selectedTask ?: return
        if (state.value.pendingDeleteTaskId != current.id) return
        runStoreAction { activeStore ->
            reminderCoordinator?.cancel(current)
            activeStore.delete(current.id)
            refreshFrom(activeStore).copy(
                selectedTask = null,
                editTitle = "",
                editReminderText = "",
                reminderDeliveryState = ReminderDeliveryState.NoReminder,
                pendingDeleteTaskId = null,
                shouldRequestNotificationPermission = false,
            )
        }
    }

    private fun openStoreAndRefresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, storeErrorMessage = null) }
            when (val result = withContext(ioDispatcher) { storeProvider() }) {
                is InboxStoreOpenOutcome.Opened -> {
                    store?.close()
                    store = result.store
                    val nextState = withContext(ioDispatcher) { refreshFrom(result.store) }
                    mutableState.value = nextState.copy(
                        isLoading = false,
                        draftTitle = mutableState.value.draftTitle,
                        validationMessage = null,
                        storeErrorMessage = null,
                    )
                }
                is InboxStoreOpenOutcome.Unavailable -> mutableState.update {
                    it.copy(isLoading = false, storeErrorMessage = STORE_UNAVAILABLE_MESSAGE)
                }
            }
        }
    }

    private fun runStoreAction(
        clearStoreError: Boolean = false,
        action: (InboxTaskStore) -> InboxUiState,
    ) {
        viewModelScope.launch {
            val activeStore = store
            if (activeStore == null) {
                mutableState.update { it.copy(storeErrorMessage = STORE_UNAVAILABLE_MESSAGE) }
                return@launch
            }
            mutableState.update { it.copy(isLoading = true, storeErrorMessage = if (clearStoreError) null else it.storeErrorMessage) }
            val nextState = try {
                withContext(ioDispatcher) { action(activeStore) }
            } catch (_: RuntimeException) {
                mutableState.value.copy(storeErrorMessage = STORE_UNAVAILABLE_MESSAGE)
            }
            mutableState.value = nextState.copy(isLoading = false)
        }
    }

    private fun parseReminderOrUpdateState(reminderText: String): ReminderAt? {
        if (reminderText.isBlank()) {
            mutableState.update { it.copy(validationMessage = REMINDER_TIME_HELP) }
            return null
        }
        return try {
            ReminderAt(Instant.parse(reminderText.trim()))
        } catch (_: DateTimeParseException) {
            mutableState.update { it.copy(validationMessage = REMINDER_TIME_HELP) }
            null
        }
    }

    private fun refreshFrom(activeStore: InboxTaskStore): InboxUiState {
        val tasks = activeStore.listInbox()
        val selectedId = mutableState.value.selectedTask?.id
        val selected = selectedId?.let { id -> tasks.firstOrNull { it.id == id } }
        return mutableState.value.copy(
            tasks = tasks,
            selectedTask = selected,
            editTitle = selected?.title?.value ?: mutableState.value.editTitle,
            editReminderText = selected?.reminderAt?.instant?.toString() ?: mutableState.value.editReminderText,
            pendingDeleteTaskId = mutableState.value.pendingDeleteTaskId?.takeIf { it == selected?.id },
        )
    }

    private fun InboxUiState.withSelectedTask(task: InboxTask): InboxUiState = copy(
        selectedTask = task,
        editTitle = task.title.value,
        editReminderText = task.reminderAt?.instant?.toString().orEmpty(),
        reminderDeliveryState = deliveryStateFor(task),
    )

    private fun deliveryStateFor(task: InboxTask): ReminderDeliveryState = when {
        task.reminderAt == null -> ReminderDeliveryState.NoReminder
        state.value.selectedTask?.id == task.id -> reminderDeliveryState
        else -> task.reminderDeliveryState
    }

    override fun onCleared() {
        store?.close()
        store = null
    }
}