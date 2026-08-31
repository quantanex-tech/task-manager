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
import tech.quantanex.taskmanager.domain.TaskId

private const val STORE_UNAVAILABLE_MESSAGE = "Encrypted local storage is unavailable. Unsaved draft text stays only on this screen."

data class InboxUiState(
    val isLoading: Boolean = true,
    val draftTitle: String = "",
    val tasks: List<InboxTask> = emptyList(),
    val selectedTask: InboxTask? = null,
    val editTitle: String = "",
    val pendingDeleteTaskId: TaskId? = null,
    val validationMessage: String? = null,
    val storeErrorMessage: String? = null,
) {
    val hasTasks: Boolean = tasks.isNotEmpty()
}

class InboxViewModel(
    private val storeProvider: suspend () -> InboxStoreOpenOutcome,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
                pendingDeleteTaskId = null,
                validationMessage = null,
            )
        }
    }

    fun updateEditTitle(title: String) {
        mutableState.update { it.copy(editTitle = title, validationMessage = null) }
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
            refreshFrom(activeStore).copy(selectedTask = edited, editTitle = edited.title.value, validationMessage = null)
        }
    }

    fun completeSelectedTask() {
        val current = state.value.selectedTask ?: return
        runStoreAction { activeStore ->
            val completed = activeStore.complete(current.id)
            refreshFrom(activeStore).copy(selectedTask = completed, editTitle = completed.title.value)
        }
    }

    fun undoSelectedTaskCompletion() {
        val current = state.value.selectedTask ?: return
        runStoreAction { activeStore ->
            val restored = activeStore.undoCompletion(current.id)
            refreshFrom(activeStore).copy(selectedTask = restored, editTitle = restored.title.value)
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
            activeStore.delete(current.id)
            refreshFrom(activeStore).copy(selectedTask = null, editTitle = "", pendingDeleteTaskId = null)
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

    private fun refreshFrom(activeStore: InboxTaskStore): InboxUiState {
        val tasks = activeStore.listInbox()
        val selectedId = mutableState.value.selectedTask?.id
        val selected = selectedId?.let { id -> tasks.firstOrNull { it.id == id } }
        return mutableState.value.copy(
            tasks = tasks,
            selectedTask = selected,
            editTitle = selected?.title?.value ?: mutableState.value.editTitle,
            pendingDeleteTaskId = mutableState.value.pendingDeleteTaskId?.takeIf { it == selected?.id },
        )
    }

    override fun onCleared() {
        store?.close()
        store = null
    }
}
