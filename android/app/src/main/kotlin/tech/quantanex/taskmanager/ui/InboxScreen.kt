package tech.quantanex.taskmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import tech.quantanex.taskmanager.domain.InboxTask
import tech.quantanex.taskmanager.domain.TaskId

@Composable
fun InboxScreen(viewModel: InboxViewModel) {
    val state by viewModel.state.collectAsState()
    InboxContent(
        state = state,
        onDraftChange = viewModel::updateDraftTitle,
        onCreate = viewModel::createTask,
        onSelect = viewModel::selectTask,
        onEditChange = viewModel::updateEditTitle,
        onSaveEdit = viewModel::saveSelectedTask,
        onComplete = viewModel::completeSelectedTask,
        onUndo = viewModel::undoSelectedTaskCompletion,
        onRequestDelete = viewModel::requestDeleteSelectedTask,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDeleteSelectedTask,
        onRetry = viewModel::retryOpenStore,
    )
}

@Composable
fun InboxContent(
    state: InboxUiState,
    onDraftChange: (String) -> Unit,
    onCreate: () -> Unit,
    onSelect: (TaskId) -> Unit,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    val titleFocusRequester = FocusRequester()
    LaunchedEffect(Unit) {
        titleFocusRequester.requestFocus()
    }

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Inbox", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = state.draftTitle,
                onValueChange = onDraftChange,
                label = { Text("New task title") },
                singleLine = true,
                keyboardActions = KeyboardActions(onDone = { onCreate() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester)
                    .semantics { contentDescription = "New task title" }
                    .testTag("title-input"),
            )
            Button(
                onClick = onCreate,
                enabled = !state.isLoading,
                modifier = Modifier.testTag("add-task"),
            ) {
                Text("Save to encrypted Inbox")
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loading-state"),
                )
                Text("Opening encrypted local storage…")
            }

            state.validationMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("validation-state"),
                )
            }

            state.storeErrorMessage?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("error-state"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("retry-open-store")) {
                        Text("Try encrypted storage again")
                    }
                }
            }

            if (!state.isLoading && state.storeErrorMessage == null && !state.hasTasks) {
                Text(
                    text = "No Inbox tasks yet. Add one above to keep it encrypted on this device.",
                    modifier = Modifier.testTag("empty-state"),
                )
            }

            InboxTaskList(state.tasks, state.selectedTask?.id, onSelect)
            SelectedTaskEditor(
                state = state,
                onEditChange = onEditChange,
                onSaveEdit = onSaveEdit,
                onComplete = onComplete,
                onUndo = onUndo,
                onRequestDelete = onRequestDelete,
                onCancelDelete = onCancelDelete,
                onConfirmDelete = onConfirmDelete,
            )
        }
    }
}

@Composable
private fun InboxTaskList(
    tasks: List<InboxTask>,
    selectedTaskId: TaskId?,
    onSelect: (TaskId) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tasks, key = { it.id.value }) { task ->
            val selectedSuffix = if (task.id == selectedTaskId) " selected" else ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(task.id) }
                    .semantics { contentDescription = "Inbox task ${task.title.value}$selectedSuffix" }
                    .testTag("task-row-${task.id.value}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = null,
                    modifier = Modifier.testTag("task-completed-${task.id.value}"),
                )
                Text(
                    text = task.title.value,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

@Composable
private fun SelectedTaskEditor(
    state: InboxUiState,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val task = state.selectedTask ?: return
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-detail"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Task details", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.editTitle,
            onValueChange = onEditChange,
            label = { Text("Task title") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Selected task title" }
                .testTag("edit-title"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSaveEdit, enabled = !state.isLoading, modifier = Modifier.testTag("save-edit")) {
                Text("Save edit")
            }
            if (task.isCompleted) {
                OutlinedButton(onClick = onUndo, enabled = !state.isLoading, modifier = Modifier.testTag("undo-completion")) {
                    Text("Undo complete")
                }
            } else {
                OutlinedButton(onClick = onComplete, enabled = !state.isLoading, modifier = Modifier.testTag("complete-task")) {
                    Text("Complete")
                }
            }
        }
        if (state.pendingDeleteTaskId == task.id) {
            Text(
                text = "Delete this task? This only affects the encrypted local Inbox.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("delete-protection"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirmDelete, enabled = !state.isLoading, modifier = Modifier.testTag("confirm-delete")) {
                    Text("Confirm delete")
                }
                OutlinedButton(onClick = onCancelDelete, enabled = !state.isLoading, modifier = Modifier.testTag("cancel-delete")) {
                    Text("Cancel")
                }
            }
        } else {
            OutlinedButton(onClick = onRequestDelete, enabled = !state.isLoading, modifier = Modifier.testTag("delete-task")) {
                Text("Delete…")
            }
        }
    }
}
