package tech.quantanex.taskmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.quantanex.taskmanager.ui.InboxContent
import tech.quantanex.taskmanager.ui.InboxUiState
import tech.quantanex.taskmanager.ui.TaskManagerTheme

@RunWith(AndroidJUnit4::class)
class InboxWorkflowInstrumentedTest {
    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsImmediatelyReachableTitleInput() {
        activityRule.onNodeWithTag("title-input").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class InboxThemeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightThemeRendersEmptyState() {
        composeRule.setContent {
            TaskManagerTheme(darkTheme = false) {
                InboxContent(
                    state = InboxUiState(isLoading = false),
                    onDraftChange = {},
                    onCreate = {},
                    onSelect = {},
                    onEditChange = {},
                    onSaveEdit = {},
                    onComplete = {},
                    onUndo = {},
                    onRequestDelete = {},
                    onCancelDelete = {},
                    onConfirmDelete = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("title-input").assertIsDisplayed()
        composeRule.onNodeWithTag("empty-state").assertIsDisplayed()
    }

    @Test
    fun darkThemeRendersErrorState() {
        composeRule.setContent {
            TaskManagerTheme(darkTheme = true) {
                InboxContent(
                    state = InboxUiState(
                        isLoading = false,
                        draftTitle = "unsaved draft",
                        storeErrorMessage = "Encrypted local storage is unavailable.",
                    ),
                    onDraftChange = {},
                    onCreate = {},
                    onSelect = {},
                    onEditChange = {},
                    onSaveEdit = {},
                    onComplete = {},
                    onUndo = {},
                    onRequestDelete = {},
                    onCancelDelete = {},
                    onConfirmDelete = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("title-input").assertIsDisplayed()
        composeRule.onNodeWithTag("error-state").assertIsDisplayed()
    }
}
