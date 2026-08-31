package tech.quantanex.taskmanager.persistence.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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

    private class InMemoryWrappedDatabaseKeyStore(initial: ByteArray? = null) : WrappedDatabaseKeyStore {
        private var blob: ByteArray? = initial?.copyOf()
        override fun read(): ByteArray? = blob?.copyOf()
        override fun writeIfAbsent(blob: ByteArray): Boolean {
            if (this.blob != null) return false
            this.blob = blob.copyOf()
            return true
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

    private class FailingProtector(private val error: DatabaseKeyBootstrapError) : DatabaseKeyProtector {
        override val capability: KeystoreCapability = KeystoreCapability(hardwareBacked = null)
        override fun wrap(plaintext: ByteArray): ByteArray = throw DatabaseKeyProtectionException(error)
        override fun unwrap(blob: ByteArray): ByteArray = throw DatabaseKeyProtectionException(error)
    }
}
