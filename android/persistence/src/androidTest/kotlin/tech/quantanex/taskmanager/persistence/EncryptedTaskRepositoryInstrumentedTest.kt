package tech.quantanex.taskmanager.persistence

import androidx.test.core.app.ApplicationProvider
import tech.quantanex.taskmanager.domain.ReminderAt
import tech.quantanex.taskmanager.domain.ReminderDeliveryStatus
import tech.quantanex.taskmanager.domain.TaskId
import tech.quantanex.taskmanager.domain.TaskIdGenerator
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyBootstrapResult
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyBootstrapper
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyMaterial
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyProtector
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyGenerator
import tech.quantanex.taskmanager.persistence.crypto.KeystoreCapability
import tech.quantanex.taskmanager.persistence.crypto.WrappedDatabaseKeyStore
import tech.quantanex.taskmanager.persistence.crypto.WrappedDatabaseKeyStoreWriteResult
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class EncryptedTaskRepositoryInstrumentedTest {
    @Test
    fun encryptedStorePersistsTaskLifecycleAcrossRepositoryInstancesOffline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "encrypted-reopen-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = InMemoryWrappedDatabaseKeyStore()
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = XorTestProtector(),
            keyGenerator = FixedDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { index -> (index + 1).toByte() }),
            elapsedMillis = { 15 },
        )
        val ids = IteratorTaskIdGenerator("fixture-1", "fixture-2")
        val firstOpen = EncryptedTaskRepositoryFactory.open(context, databaseName, ids, bootstrapper)
        assertIs<EncryptedTaskRepositoryOpenResult.Success>(firstOpen)
        val initialReminder = ReminderAt(Instant.parse("2026-09-01T09:30:00Z"))
        val editedReminder = ReminderAt(Instant.parse("2026-09-02T10:45:00Z"))

        val created = firstOpen.repository.create("Synthetic encrypted fixture", initialReminder)
        firstOpen.repository.edit(created.id, "Synthetic encrypted fixture edited", editedReminder)
        firstOpen.repository.complete(created.id)
        firstOpen.repository.undoCompletion(created.id)
        firstOpen.repository.removeReminder(created.id)
        firstOpen.repository.setReminder(created.id, initialReminder)
        firstOpen.repository.setReminderDeliveryState(created.id, ReminderDeliveryStatus.ExactAlarmUnavailable)
        firstOpen.closeable.close()

        val reopened = EncryptedTaskRepositoryFactory.open(context, databaseName, ids, bootstrapper)
        assertIs<EncryptedTaskRepositoryOpenResult.Success>(reopened)
        val task = reopened.repository.get(created.id)!!
        assertEquals("Synthetic encrypted fixture edited", task.title.value)
        assertFalse(task.isCompleted)
        assertEquals(initialReminder, task.reminderAt)
        assertEquals(ReminderDeliveryStatus.ExactAlarmUnavailable, task.reminderDeliveryState)
        assertEquals(listOf(task), reopened.repository.listInbox())
        reopened.repository.delete(created.id)
        assertNull(reopened.repository.get(created.id))
        reopened.closeable.close()
    }

    @Test
    fun encryptedStoreDefaultIdsRemainDistinctAcrossRepositoryReopen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "encrypted-default-id-reopen-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val bootstrapper = DatabaseKeyBootstrapper(
            store = InMemoryWrappedDatabaseKeyStore(),
            protector = XorTestProtector(),
            keyGenerator = FixedDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { index -> (index + 7).toByte() }),
            elapsedMillis = { 15 },
        )

        val firstOpen = EncryptedTaskRepositoryFactory.open(
            context = context,
            databaseName = databaseName,
            keyBootstrapper = bootstrapper,
        )
        assertIs<EncryptedTaskRepositoryOpenResult.Success>(firstOpen)
        val firstTask = firstOpen.repository.create("Synthetic durable task one", null)
        firstOpen.closeable.close()

        val reopened = EncryptedTaskRepositoryFactory.open(
            context = context,
            databaseName = databaseName,
            keyBootstrapper = bootstrapper,
        )
        assertIs<EncryptedTaskRepositoryOpenResult.Success>(reopened)
        val secondTask = reopened.repository.create("Synthetic durable task two", null)

        assertProductionOpaqueTaskId(firstTask.id)
        assertProductionOpaqueTaskId(secondTask.id)
        assertNotEquals(firstTask.id, secondTask.id)
        assertEquals(listOf(firstTask, secondTask), reopened.repository.listInbox())
        reopened.closeable.close()
    }

    @Test
    fun plaintextFixtureValueIsAbsentFromDatabaseAndBackupIncludedFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "encrypted-plaintext-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val fixtureTitle = "Synthetic plaintext absence fixture ${System.nanoTime()}"
        val open = EncryptedTaskRepositoryFactory.open(
            context = context,
            databaseName = databaseName,
            idGenerator = IteratorTaskIdGenerator("fixture-plaintext"),
            keyBootstrapper = DatabaseKeyBootstrapper(
                store = InMemoryWrappedDatabaseKeyStore(),
                protector = XorTestProtector(),
                keyGenerator = FixedDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 42 }),
                elapsedMillis = { 15 },
            ),
        )
        assertIs<EncryptedTaskRepositoryOpenResult.Success>(open)
        open.repository.create(fixtureTitle, ReminderAt(Instant.parse("2026-09-01T09:30:00Z")))
        open.closeable.close()

        val databaseFiles = listOf(
            context.getDatabasePath(databaseName),
            File(context.getDatabasePath(databaseName).path + "-wal"),
            File(context.getDatabasePath(databaseName).path + "-shm"),
            File(context.getDatabasePath(databaseName).path + "-journal"),
        ).filter { it.exists() }
        assertContains(databaseFiles.map { it.name }, databaseName)
        databaseFiles.forEach { file ->
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(fixtureTitle), "Plaintext fixture leaked in ${file.name}")
        }

        val backupIncludedFiles = listOf(context.filesDir, context.cacheDir).flatMap { root ->
            root.walkTopDown().filter { it.isFile }.toList()
        }
        backupIncludedFiles.forEach { file ->
            assertFalse(file.readText(Charsets.ISO_8859_1).contains(fixtureTitle), "Plaintext fixture leaked in backup-included file ${file.name}")
        }
    }

    private class IteratorTaskIdGenerator(vararg ids: String) : TaskIdGenerator {
        private val iterator = ids.iterator()
        override fun nextId(): TaskId = TaskId(iterator.next())
    }

    private fun assertProductionOpaqueTaskId(id: TaskId) {
        assertFalse(id.value.startsWith("synthetic-task-"), "Production default ID must not use the sequential synthetic fixture form")
        assertFalse(id.value.startsWith("fixture-"), "Production default ID must not use deterministic fixture IDs")
        assertEquals("task-", id.value.take("task-".length), "Production default ID must use the opaque task UUID prefix")
        val uuid = UUID.fromString(id.value.removePrefix("task-"))
        assertEquals("task-$uuid", id.value, "Production default ID must contain a canonical RFC-4122 UUID payload")
    }

    private class InMemoryWrappedDatabaseKeyStore(initial: ByteArray? = null) : WrappedDatabaseKeyStore {
        private var blob: ByteArray? = initial?.copyOf()
        override fun read(): ByteArray? = blob?.copyOf()
        override fun writeIfAbsent(blob: ByteArray): WrappedDatabaseKeyStoreWriteResult {
            if (this.blob != null) return WrappedDatabaseKeyStoreWriteResult.AlreadyExists
            this.blob = blob.copyOf()
            return WrappedDatabaseKeyStoreWriteResult.Written
        }
    }

    private class FixedDatabaseKeyGenerator(private val key: ByteArray) : DatabaseKeyGenerator {
        override fun generate(): ByteArray = key.copyOf()
    }

    private class XorTestProtector : DatabaseKeyProtector {
        override val capability: KeystoreCapability = KeystoreCapability(hardwareBacked = false)
        override fun wrap(plaintext: ByteArray): ByteArray = plaintext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun unwrap(blob: ByteArray): ByteArray = wrap(blob)
    }
}
