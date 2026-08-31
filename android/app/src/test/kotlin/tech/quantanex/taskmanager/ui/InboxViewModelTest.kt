package tech.quantanex.taskmanager.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import tech.quantanex.taskmanager.data.InboxStoreOpenOutcome
import tech.quantanex.taskmanager.data.InboxStoreUnavailableReason
import tech.quantanex.taskmanager.data.InboxTaskStore
import tech.quantanex.taskmanager.domain.InboxTask
import tech.quantanex.taskmanager.domain.ReminderAt
import tech.quantanex.taskmanager.domain.TaskId
import tech.quantanex.taskmanager.domain.TaskTitle
import tech.quantanex.taskmanager.reminders.ExactReminderScheduler
import tech.quantanex.taskmanager.reminders.LocalReminderCoordinator
import tech.quantanex.taskmanager.reminders.NotificationPermissionGate
import tech.quantanex.taskmanager.reminders.ReminderDeliveryState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun firstStateKeepsDraftAffordanceAvailableWhileEncryptedStoreOpens() = runTest(dispatcher) {
        val viewModel = InboxViewModel(
            storeProvider = { InboxStoreOpenOutcome.Opened(FakeInboxTaskStore()) },
            ioDispatcher = dispatcher,
        )

        val state = viewModel.state.value

        assertEquals("", state.draftTitle)
        assertNull(state.storeErrorMessage)
    }

    @Test
    fun createListViewEditCompleteUndoAndDeleteRefreshesInbox() = runTest(dispatcher) {
        val viewModel = openedViewModel(FakeInboxTaskStore())

        viewModel.updateDraftTitle("  Draft inbox task  ")
        viewModel.createTask()
        val created = viewModel.state.value.tasks.single()
        assertEquals("Draft inbox task", created.title.value)
        assertEquals("", viewModel.state.value.draftTitle)

        viewModel.selectTask(created.id)
        assertEquals(created.id, viewModel.state.value.selectedTask?.id)

        viewModel.updateEditTitle("Edited inbox task")
        viewModel.saveSelectedTask()
        assertEquals("Edited inbox task", viewModel.state.value.tasks.single().title.value)

        viewModel.completeSelectedTask()
        assertTrue(viewModel.state.value.tasks.single().isCompleted)

        viewModel.undoSelectedTaskCompletion()
        assertFalse(viewModel.state.value.tasks.single().isCompleted)

        viewModel.requestDeleteSelectedTask()
        assertEquals(created.id, viewModel.state.value.pendingDeleteTaskId)
        viewModel.confirmDeleteSelectedTask()
        assertTrue(viewModel.state.value.tasks.isEmpty())
        assertNull(viewModel.state.value.selectedTask)
    }

    @Test
    fun blankTitlesShowValidationWithoutStoreMutation() = runTest(dispatcher) {
        val store = FakeInboxTaskStore()
        val viewModel = openedViewModel(store)

        viewModel.updateDraftTitle("   ")
        viewModel.createTask()

        assertNotNull(viewModel.state.value.validationMessage)
        assertTrue(store.tasks.isEmpty())
    }

    @Test
    fun emptyLoadingAndStoreErrorStatesAreExplicit() = runTest(dispatcher) {
        val emptyViewModel = openedViewModel(FakeInboxTaskStore())
        assertFalse(emptyViewModel.state.value.isLoading)
        assertTrue(emptyViewModel.state.value.tasks.isEmpty())
        assertNull(emptyViewModel.state.value.storeErrorMessage)

        val failingViewModel = InboxViewModel(
            storeProvider = { InboxStoreOpenOutcome.Unavailable(InboxStoreUnavailableReason.KeyBootstrapFailed) },
            ioDispatcher = dispatcher,
        )

        assertFalse(failingViewModel.state.value.isLoading)
        assertNotNull(failingViewModel.state.value.storeErrorMessage)
    }

    @Test
    fun draftIsNotClearedAndNoFallbackStoreIsUsedWhenEncryptedOpenFails() = runTest(dispatcher) {
        val viewModel = InboxViewModel(
            storeProvider = { InboxStoreOpenOutcome.Unavailable(InboxStoreUnavailableReason.DatabaseOpenFailed) },
            ioDispatcher = dispatcher,
        )

        viewModel.updateDraftTitle("keep this volatile draft")
        viewModel.createTask()

        assertEquals("keep this volatile draft", viewModel.state.value.draftTitle)
        assertTrue(viewModel.state.value.tasks.isEmpty())
        assertNotNull(viewModel.state.value.storeErrorMessage)
    }

    @Test
    fun reopeningWithSameEncryptedStoreShowsDurableInboxTasks() = runTest(dispatcher) {
        val durableStore = FakeInboxTaskStore()
        val firstViewModel = openedViewModel(durableStore)
        firstViewModel.updateDraftTitle("Durable offline task")
        firstViewModel.createTask()

        val reopenedViewModel = openedViewModel(durableStore)

        assertEquals("Durable offline task", reopenedViewModel.state.value.tasks.single().title.value)
    }

    @Test
    fun editCompleteAndUndoPreserveExistingReminderValue() = runTest(dispatcher) {
        val reminder = ReminderAt(Instant.parse("2026-09-01T10:15:30Z"))
        val task = InboxTask(
            TaskId("task-with-reminder"),
            TaskTitle("Original"),
            isCompleted = false,
            reminderAt = reminder,
            reminderDeliveryState = ReminderDeliveryState.Scheduled,
        )
        val store = FakeInboxTaskStore(mutableListOf(task))
        val viewModel = openedViewModel(store)

        viewModel.selectTask(task.id)
        viewModel.updateEditTitle("Edited")
        viewModel.saveSelectedTask()
        viewModel.completeSelectedTask()
        viewModel.undoSelectedTaskCompletion()

        assertEquals(reminder, store.tasks.single().reminderAt)
        assertEquals(ReminderDeliveryState.Scheduled, store.tasks.single().reminderDeliveryState)
        assertEquals(listOf<ReminderAt?>(reminder), store.editReminderValues)
    }

    @Test
    fun addEditViewAndRemoveReminderMutatesEncryptedStoreBoundaryAndSchedulesExactly() = runTest(dispatcher) {
        val scheduler = RecordingReminderScheduler()
        val viewModel = openedViewModel(
            store = FakeInboxTaskStore(),
            coordinator = LocalReminderCoordinator(AlwaysAllowedNotifications, scheduler),
        )

        viewModel.updateDraftTitle("Task with reminder")
        viewModel.createTask()
        val task = viewModel.state.value.tasks.single()
        viewModel.selectTask(task.id)
        viewModel.updateEditReminderText("2026-09-01T09:30:00Z")
        viewModel.saveSelectedReminder()

        assertEquals(Instant.parse("2026-09-01T09:30:00Z"), viewModel.state.value.selectedTask?.reminderAt?.instant)
        assertEquals("2026-09-01T09:30:00Z", viewModel.state.value.editReminderText)
        assertEquals(ReminderDeliveryState.Scheduled, viewModel.state.value.reminderDeliveryState)
        assertEquals(listOf(Instant.parse("2026-09-01T09:30:00Z")), scheduler.scheduledInstants)

        viewModel.updateEditReminderText("2026-09-02T10:45:00Z")
        viewModel.saveSelectedReminder()
        assertEquals(Instant.parse("2026-09-02T10:45:00Z"), viewModel.state.value.selectedTask?.reminderAt?.instant)
        assertEquals(Instant.parse("2026-09-02T10:45:00Z"), scheduler.scheduledInstants.last())

        viewModel.removeSelectedReminder()
        assertNull(viewModel.state.value.selectedTask?.reminderAt)
        assertEquals(ReminderDeliveryState.NoReminder, viewModel.state.value.reminderDeliveryState)
        assertEquals(listOf(task.id), scheduler.cancelledTaskIds)
    }

    @Test
    fun notificationPermissionDenialDoesNotBlockReminderSaveAndRequestsPermissionContextually() = runTest(dispatcher) {
        val scheduler = RecordingReminderScheduler()
        val store = FakeInboxTaskStore()
        val viewModel = openedViewModel(
            store = store,
            coordinator = LocalReminderCoordinator(DeniedNotifications, scheduler),
        )

        viewModel.updateDraftTitle("Permission denied reminder")
        viewModel.createTask()
        val task = viewModel.state.value.tasks.single()
        viewModel.selectTask(task.id)
        viewModel.updateEditReminderText("2026-09-01T09:30:00Z")
        viewModel.saveSelectedReminder()

        assertEquals(Instant.parse("2026-09-01T09:30:00Z"), viewModel.state.value.selectedTask?.reminderAt?.instant)
        assertEquals(ReminderDeliveryState.NotificationPermissionDenied, viewModel.state.value.reminderDeliveryState)
        assertTrue(viewModel.state.value.shouldRequestNotificationPermission)
        assertTrue(scheduler.scheduledInstants.isEmpty())
        assertEquals(listOf(task.id), scheduler.cancelledTaskIds)

        val reopenedViewModel = openedViewModel(store)
        reopenedViewModel.selectTask(task.id)

        assertEquals(ReminderDeliveryState.NotificationPermissionDenied, reopenedViewModel.state.value.reminderDeliveryState)
    }

    @Test
    fun exactAlarmUnavailableIsTypedAndUserVisibleWithoutBestEffortFallback() = runTest(dispatcher) {
        val scheduler = RecordingReminderScheduler(scheduleResult = ReminderDeliveryState.ExactAlarmUnavailable)
        val store = FakeInboxTaskStore()
        val viewModel = openedViewModel(
            store = store,
            coordinator = LocalReminderCoordinator(AlwaysAllowedNotifications, scheduler),
        )

        viewModel.updateDraftTitle("Exact unavailable reminder")
        viewModel.createTask()
        viewModel.selectTask(viewModel.state.value.tasks.single().id)
        viewModel.updateEditReminderText("2026-09-01T09:30:00Z")
        viewModel.saveSelectedReminder()

        assertEquals(ReminderDeliveryState.ExactAlarmUnavailable, viewModel.state.value.reminderDeliveryState)
        assertEquals(listOf(Instant.parse("2026-09-01T09:30:00Z")), scheduler.scheduledInstants)
        assertEquals(listOf(viewModel.state.value.selectedTask!!.id), scheduler.cancelledTaskIds)

        val reopenedViewModel = openedViewModel(store)
        reopenedViewModel.selectTask(viewModel.state.value.selectedTask!!.id)

        assertEquals(ReminderDeliveryState.ExactAlarmUnavailable, reopenedViewModel.state.value.reminderDeliveryState)
    }

    @Test
    fun invalidReminderInputDoesNotMutateStoreOrSchedule() = runTest(dispatcher) {
        val scheduler = RecordingReminderScheduler()
        val store = FakeInboxTaskStore()
        val viewModel = openedViewModel(store, LocalReminderCoordinator(AlwaysAllowedNotifications, scheduler))

        viewModel.updateDraftTitle("Invalid reminder")
        viewModel.createTask()
        viewModel.selectTask(viewModel.state.value.tasks.single().id)
        viewModel.updateEditReminderText("not-a-time")
        viewModel.saveSelectedReminder()

        assertNull(store.tasks.single().reminderAt)
        assertNotNull(viewModel.state.value.validationMessage)
        assertTrue(scheduler.scheduledInstants.isEmpty())
    }

    private fun openedViewModel(
        store: FakeInboxTaskStore,
        coordinator: LocalReminderCoordinator? = null,
    ): InboxViewModel = InboxViewModel(
        storeProvider = { InboxStoreOpenOutcome.Opened(store) },
        ioDispatcher = dispatcher,
        reminderCoordinator = coordinator,
    )
}

private class FakeInboxTaskStore(
    val tasks: MutableList<InboxTask> = mutableListOf(),
) : InboxTaskStore {
    val editReminderValues = mutableListOf<ReminderAt?>()
    private var nextId = 1

    override fun listInbox(): List<InboxTask> = tasks.toList()

    override fun create(title: String): InboxTask {
        val task = InboxTask(
            id = TaskId("task-${nextId++}"),
            title = TaskTitle(title.trim()),
            isCompleted = false,
            reminderAt = null,
        )
        tasks += task
        return task
    }

    override fun get(id: TaskId): InboxTask? = tasks.firstOrNull { it.id == id }

    override fun edit(task: InboxTask, title: String): InboxTask {
        editReminderValues += task.reminderAt
        val current = requireNotNull(get(task.id))
        val edited = current.copy(title = TaskTitle(title.trim()))
        replace(edited)
        return edited
    }

    override fun setReminder(task: InboxTask, reminderAt: ReminderAt): InboxTask {
        val withReminder = task.copy(reminderAt = reminderAt)
        replace(withReminder)
        return withReminder
    }

    override fun removeReminder(task: InboxTask): InboxTask {
        val withoutReminder = task.copy(reminderAt = null)
        replace(withoutReminder)
        return withoutReminder
    }

    override fun setReminderDeliveryState(task: InboxTask, state: ReminderDeliveryState): InboxTask {
        val withState = task.copy(reminderDeliveryState = state)
        replace(withState)
        return withState
    }

    override fun complete(id: TaskId): InboxTask {
        val completed = requireNotNull(get(id)).copy(isCompleted = true)
        replace(completed)
        return completed
    }

    override fun undoCompletion(id: TaskId): InboxTask {
        val restored = requireNotNull(get(id)).copy(isCompleted = false)
        replace(restored)
        return restored
    }

    override fun delete(id: TaskId) {
        tasks.removeIf { it.id == id }
    }

    override fun close() = Unit

    private fun replace(task: InboxTask) {
        val index = tasks.indexOfFirst { it.id == task.id }
        require(index >= 0)
        tasks[index] = task
    }
}

private object AlwaysAllowedNotifications : NotificationPermissionGate {
    override fun canPostNotifications(): Boolean = true
}

private object DeniedNotifications : NotificationPermissionGate {
    override fun canPostNotifications(): Boolean = false
}

private class RecordingReminderScheduler(
    private val scheduleResult: ReminderDeliveryState = ReminderDeliveryState.Scheduled,
) : ExactReminderScheduler {
    val scheduledInstants = mutableListOf<Instant>()
    val cancelledTaskIds = mutableListOf<TaskId>()

    override fun schedule(task: InboxTask): ReminderDeliveryState {
        scheduledInstants += requireNotNull(task.reminderAt).instant
        return scheduleResult
    }

    override fun cancel(task: InboxTask) {
        cancelledTaskIds += task.id
    }
}
