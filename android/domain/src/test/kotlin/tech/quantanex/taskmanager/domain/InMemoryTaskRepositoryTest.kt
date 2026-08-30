package tech.quantanex.taskmanager.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryTaskRepositoryTest {
    @Test
    fun supportsInboxTaskLifecycleWithOptionalExactReminder() {
        val repository: TaskRepository = InMemoryTaskRepository()
        val initialReminder = ReminderAt(Instant.parse("2026-09-01T09:30:00Z"))
        val editedReminder = ReminderAt(Instant.parse("2026-09-02T10:45:00Z"))

        val created = repository.create(title = "Synthetic fixture task", reminderAt = initialReminder)

        assertEquals("Synthetic fixture task", created.title.value)
        assertFalse(created.isCompleted)
        assertEquals(initialReminder, created.reminderAt)
        assertEquals(created, repository.get(created.id))
        assertEquals(listOf(created), repository.listInbox())

        val edited = repository.edit(created.id, title = "Edited synthetic fixture", reminderAt = editedReminder)
        assertEquals("Edited synthetic fixture", edited.title.value)
        assertEquals(editedReminder, edited.reminderAt)

        val completed = repository.complete(created.id)
        assertTrue(completed.isCompleted)

        val activeAgain = repository.undoCompletion(created.id)
        assertFalse(activeAgain.isCompleted)

        val withoutReminder = repository.removeReminder(created.id)
        assertNull(withoutReminder.reminderAt)

        val reminderAddedAgain = repository.setReminder(created.id, initialReminder)
        assertEquals(initialReminder, reminderAddedAgain.reminderAt)

        repository.delete(created.id)

        assertNull(repository.get(created.id))
        assertTrue(repository.listInbox().isEmpty())
    }

    @Test
    fun rejectsBlankTitlesAndInvalidReminderValues() {
        val repository: TaskRepository = InMemoryTaskRepository()
        val missingId = TaskId("synthetic-missing-id")

        assertIs<TaskRepositoryError.BlankTitle>(repository.tryCreate(title = "   ", reminderAt = null))
        assertIs<TaskRepositoryError.BlankTitle>(repository.tryCreate(title = "", reminderAt = null))
        assertIs<TaskRepositoryError.InvalidReminder>(
            repository.tryCreate(title = "Synthetic invalid reminder", reminderAt = ReminderAt.fromEpochMilliseconds(-1))
        )
        assertIs<TaskRepositoryError.NotFound>(repository.tryEdit(missingId, title = "Still synthetic", reminderAt = null))
        assertIs<TaskRepositoryError.NotFound>(repository.tryComplete(missingId))
        assertIs<TaskRepositoryError.NotFound>(repository.tryUndoCompletion(missingId))
        assertIs<TaskRepositoryError.NotFound>(repository.trySetReminder(missingId, ReminderAt(Instant.parse("2026-09-01T09:30:00Z"))))
        assertIs<TaskRepositoryError.NotFound>(repository.tryRemoveReminder(missingId))
        assertIs<TaskRepositoryError.NotFound>(repository.tryDelete(missingId))
    }

    @Test
    fun repositoryInstancesAreVolatileInMemoryAndUseSyntheticFixturesOnly() {
        val firstRepository: TaskRepository = InMemoryTaskRepository()
        val secondRepository: TaskRepository = InMemoryTaskRepository()

        val firstTask = firstRepository.create(title = "Synthetic fixture only", reminderAt = null)
        val secondTask = secondRepository.create(title = "Another synthetic fixture", reminderAt = null)

        assertEquals(listOf(firstTask), firstRepository.listInbox())
        assertEquals(listOf(secondTask), secondRepository.listInbox())
        assertNull(secondRepository.get(firstTask.id))
        assertNull(firstRepository.get(secondTask.id))
        assertNotEquals(firstTask.id, secondTask.id)
    }
}
