package tech.quantanex.taskmanager.persistence.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DatabaseKeyBootstrapperTest {
    @Test
    fun createsAndThenUnwrapsExistingDatabaseKeyWithoutChangingWrapper() {
        val store = InMemoryWrappedDatabaseKeyStore()
        val protector = XorTestProtector()
        val generatedKey = ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { index -> index.toByte() }
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = protector,
            keyGenerator = FixedDatabaseKeyGenerator(generatedKey),
            elapsedMillis = { 25 },
        )

        val first = bootstrapper.openOrCreate()
        assertIs<DatabaseKeyBootstrapResult.Success>(first)
        assertContentEquals(generatedKey, first.keyMaterial.copyBytes())
        val storedWrapper = store.read()!!.copyOf()
        assertNotEquals(generatedKey.toList(), storedWrapper.toList())
        first.keyMaterial.close()

        val reopened = bootstrapper.openOrCreate()
        assertIs<DatabaseKeyBootstrapResult.Success>(reopened)
        assertContentEquals(generatedKey, reopened.keyMaterial.copyBytes())
        assertContentEquals(storedWrapper, store.read())
        reopened.keyMaterial.close()
    }

    @Test
    fun keyUnavailableFailureDoesNotCreateWrapper() {
        val store = InMemoryWrappedDatabaseKeyStore()
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = FailingProtector(DatabaseKeyBootstrapError.KeyUnavailable),
            keyGenerator = FixedDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 7 }),
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertEquals(DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyUnavailable), result)
        assertNull(store.read())
    }

    @Test
    fun invalidatedExistingWrapperFailsClosedWithoutOverwrite() {
        val existingWrapper = byteArrayOf(1, 2, 3, 4)
        val store = InMemoryWrappedDatabaseKeyStore(existingWrapper)
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = FailingProtector(DatabaseKeyBootstrapError.KeyInvalidated),
            keyGenerator = FixedDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 8 }),
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertEquals(DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyInvalidated), result)
        assertContentEquals(existingWrapper, store.read())
    }

    @Test
    fun corruptExistingWrapperFailsClosedWithoutOverwrite() {
        val existingWrapper = byteArrayOf(9, 9, 9)
        val store = InMemoryWrappedDatabaseKeyStore(existingWrapper)
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = FailingProtector(DatabaseKeyBootstrapError.CorruptKeyMaterial),
            keyGenerator = FixedDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 8 }),
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertEquals(DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.CorruptKeyMaterial), result)
        assertContentEquals(existingWrapper, store.read())
    }

    @Test
    fun wrapperStoreReadFailureReturnsTypedKeyUnavailableWithoutCreatingPlaintextFallback() {
        val keyGenerator = CountingDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 3 })
        val store = ThrowingReadWrappedDatabaseKeyStore()
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = XorTestProtector(),
            keyGenerator = keyGenerator,
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertEquals(DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyUnavailable), result)
        assertEquals(1, store.readAttempts)
        assertEquals(0, store.writeAttempts)
        assertEquals(0, keyGenerator.generateCount)
    }

    @Test
    fun finalWrapperFileCreateFailureReturnsTypedKeyUnavailableWithoutPersistingGeneratedKey() {
        val generatedKey = ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 4 }
        val store = ScriptedWrappedDatabaseKeyStore(
            initial = null,
            writeResults = listOf(WrappedDatabaseKeyStoreWriteResult.Failed),
        )
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = XorTestProtector(),
            keyGenerator = FixedDatabaseKeyGenerator(generatedKey),
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertEquals(DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyUnavailable), result)
        assertEquals(1, store.writeAttempts)
        assertNull(store.read())
        assertFalse(store.writtenBlobs.any { it.contentEquals(generatedKey) })
    }

    @Test
    fun fileBackedStoreFinalFileCreateFailureReturnsFailureWithoutDeletingExistingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "database-key-create-failure-${UUID.randomUUID()}")
        val blockingParent = File(root, "not-a-directory")
        val wrapperFile = File(blockingParent, "database-key.wrapper")
        val existingContent = "existing non-wrapper data"

        try {
            root.mkdirs()
            blockingParent.writeText(existingContent)

            val store = NoBackupWrappedDatabaseKeyStore(wrapperFile)

            assertEquals(
                WrappedDatabaseKeyStoreWriteResult.Failed,
                store.writeIfAbsent(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 14 }),
            )
            assertEquals(existingContent, blockingParent.readText())
            assertFalse(wrapperFile.exists())
        } finally {
            wrapperFile.delete()
            blockingParent.delete()
            root.delete()
        }
    }

    @Test
    fun wrapperWriteOrFsyncFailureReturnsTypedKeyUnavailableWithoutRetryingRecursively() {
        val generatedKey = ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 5 }
        val keyGenerator = CountingDatabaseKeyGenerator(generatedKey)
        val store = ThrowingWriteWrappedDatabaseKeyStore()
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = XorTestProtector(),
            keyGenerator = keyGenerator,
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertEquals(DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyUnavailable), result)
        assertEquals(1, keyGenerator.generateCount)
        assertEquals(1, store.writeAttempts)
        assertNull(store.read())
    }

    @Test
    fun writeContentionReReadsOnceAndFailsClosedWhenNoWrapperAppears() {
        val generatedKey = ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 6 }
        val keyGenerator = CountingDatabaseKeyGenerator(generatedKey)
        val store = ScriptedWrappedDatabaseKeyStore(
            initial = null,
            writeResults = listOf(WrappedDatabaseKeyStoreWriteResult.AlreadyExists),
        )
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = XorTestProtector(),
            keyGenerator = keyGenerator,
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertEquals(DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyUnavailable), result)
        assertEquals(1, keyGenerator.generateCount)
        assertEquals(1, store.writeAttempts)
        assertEquals(2, store.readAttempts)
        assertNull(store.read())
    }

    @Test
    fun writeContentionReturnsExistingWrapperOnlyWhenBoundedReReadFindsOne() {
        val existingKey = ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 12 }
        val existingWrapper = XorTestProtector().wrap(existingKey)
        val generatedKey = ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 13 }
        val store = ScriptedWrappedDatabaseKeyStore(
            initial = null,
            writeResults = listOf(WrappedDatabaseKeyStoreWriteResult.AlreadyExists),
            readAfterAlreadyExists = existingWrapper,
        )
        val bootstrapper = DatabaseKeyBootstrapper(
            store = store,
            protector = XorTestProtector(),
            keyGenerator = FixedDatabaseKeyGenerator(generatedKey),
            elapsedMillis = { 10 },
        )

        val result = bootstrapper.openOrCreate()

        assertIs<DatabaseKeyBootstrapResult.Success>(result)
        assertContentEquals(existingKey, result.keyMaterial.copyBytes())
        assertContentEquals(existingWrapper, store.read())
        assertFalse(store.writtenBlobs.any { it.contentEquals(generatedKey) })
        result.keyMaterial.close()
    }

    @Test
    fun excessiveSetupLatencyReturnsTypedFailureAndDoesNotExposeKey() {
        val store = InMemoryWrappedDatabaseKeyStore()
        val result = DatabaseKeyBootstrapper(
            store = store,
            protector = XorTestProtector(),
            keyGenerator = FixedDatabaseKeyGenerator(ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES) { 1 }),
            elapsedMillis = { 2_000 },
            maxSetupMillis = 1_500,
        ).openOrCreate()

        assertEquals(
            DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.SetupTimedOut(2_000, 1_500)),
            result,
        )
    }

    @Test
    fun fileBackedStoreWriteIfAbsentDoesNotReplaceExistingWrapper() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "database-key-${UUID.randomUUID()}.wrapper"
        val wrapperFile = File(
            File(context.noBackupFilesDir, NoBackupWrappedDatabaseKeyStore.SECRET_DIRECTORY),
            fileName,
        )
        val store = NoBackupWrappedDatabaseKeyStore(context, fileName)
        val originalWrapper = byteArrayOf(11, 12, 13, 14)
        val replacementWrapper = byteArrayOf(99, 98, 97, 96)

        try {
            assertEquals(WrappedDatabaseKeyStoreWriteResult.Written, store.writeIfAbsent(originalWrapper))
            assertContentEquals(originalWrapper, store.read())

            assertEquals(WrappedDatabaseKeyStoreWriteResult.AlreadyExists, store.writeIfAbsent(replacementWrapper))

            assertContentEquals(originalWrapper, store.read())
        } finally {
            wrapperFile.delete()
        }
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

    private class ScriptedWrappedDatabaseKeyStore(
        initial: ByteArray?,
        private val writeResults: List<WrappedDatabaseKeyStoreWriteResult>,
        private val readAfterAlreadyExists: ByteArray? = null,
    ) : WrappedDatabaseKeyStore {
        private var blob: ByteArray? = initial?.copyOf()
        val writtenBlobs = mutableListOf<ByteArray>()
        var readAttempts = 0
            private set
        var writeAttempts = 0
            private set

        override fun read(): ByteArray? {
            readAttempts += 1
            return blob?.copyOf()
        }

        override fun writeIfAbsent(blob: ByteArray): WrappedDatabaseKeyStoreWriteResult {
            writeAttempts += 1
            writtenBlobs += blob.copyOf()
            val result = writeResults.getOrElse(writeAttempts - 1) { WrappedDatabaseKeyStoreWriteResult.Failed }
            if (result == WrappedDatabaseKeyStoreWriteResult.Written) this.blob = blob.copyOf()
            if (result == WrappedDatabaseKeyStoreWriteResult.AlreadyExists && readAfterAlreadyExists != null) {
                this.blob = readAfterAlreadyExists.copyOf()
            }
            return result
        }
    }

    private class ThrowingReadWrappedDatabaseKeyStore : WrappedDatabaseKeyStore {
        var readAttempts = 0
            private set
        var writeAttempts = 0
            private set

        override fun read(): ByteArray? {
            readAttempts += 1
            throw IOException("synthetic read failure")
        }

        override fun writeIfAbsent(blob: ByteArray): WrappedDatabaseKeyStoreWriteResult {
            writeAttempts += 1
            return WrappedDatabaseKeyStoreWriteResult.Failed
        }
    }

    private class ThrowingWriteWrappedDatabaseKeyStore : WrappedDatabaseKeyStore {
        var writeAttempts = 0
            private set

        override fun read(): ByteArray? = null

        override fun writeIfAbsent(blob: ByteArray): WrappedDatabaseKeyStoreWriteResult {
            writeAttempts += 1
            throw IOException("synthetic write/fsync failure")
        }
    }

    private class FixedDatabaseKeyGenerator(private val key: ByteArray) : DatabaseKeyGenerator {
        override fun generate(): ByteArray = key.copyOf()
    }

    private class CountingDatabaseKeyGenerator(private val key: ByteArray) : DatabaseKeyGenerator {
        var generateCount = 0
            private set

        override fun generate(): ByteArray {
            generateCount += 1
            return key.copyOf()
        }
    }

    private class XorTestProtector : DatabaseKeyProtector {
        override val capability: KeystoreCapability = KeystoreCapability(hardwareBacked = false)
        override fun wrap(plaintext: ByteArray): ByteArray = plaintext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun unwrap(blob: ByteArray): ByteArray = wrap(blob)
    }

    private class FailingProtector(private val error: DatabaseKeyBootstrapError) : DatabaseKeyProtector {
        override val capability: KeystoreCapability = KeystoreCapability(hardwareBacked = null)
        override fun wrap(plaintext: ByteArray): ByteArray = throw DatabaseKeyProtectionException(error)
        override fun unwrap(blob: ByteArray): ByteArray = throw DatabaseKeyProtectionException(error)
    }
}
