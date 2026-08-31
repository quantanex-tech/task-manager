package tech.quantanex.taskmanager.persistence.crypto

import java.security.GeneralSecurityException
import java.util.Arrays

class DatabaseKeyMaterial(private val bytes: ByteArray) : AutoCloseable {
    init {
        require(bytes.size == DATABASE_KEY_BYTES) { "Database key must be $DATABASE_KEY_BYTES bytes" }
    }

    fun copyBytes(): ByteArray = bytes.copyOf()

    override fun close() {
        Arrays.fill(bytes, 0)
    }

    companion object {
        const val DATABASE_KEY_BYTES = 32
    }
}

sealed interface DatabaseKeyBootstrapResult {
    data class Success(
        val keyMaterial: DatabaseKeyMaterial,
        val capability: KeystoreCapability,
    ) : DatabaseKeyBootstrapResult

    data class Failure(val error: DatabaseKeyBootstrapError) : DatabaseKeyBootstrapResult
}

sealed interface DatabaseKeyBootstrapError {
    data object KeyUnavailable : DatabaseKeyBootstrapError
    data object KeyInvalidated : DatabaseKeyBootstrapError
    data object CorruptKeyMaterial : DatabaseKeyBootstrapError
    data object UnsupportedCipher : DatabaseKeyBootstrapError
    data class SetupTimedOut(val elapsedMillis: Long, val maxMillis: Long) : DatabaseKeyBootstrapError
}

data class KeystoreCapability(
    val hardwareBacked: Boolean?,
)

interface WrappedDatabaseKeyStore {
    fun read(): ByteArray?
    fun writeIfAbsent(blob: ByteArray): WrappedDatabaseKeyStoreWriteResult
}

sealed interface WrappedDatabaseKeyStoreWriteResult {
    data object Written : WrappedDatabaseKeyStoreWriteResult
    data object AlreadyExists : WrappedDatabaseKeyStoreWriteResult
    data object Failed : WrappedDatabaseKeyStoreWriteResult
}

interface DatabaseKeyGenerator {
    fun generate(): ByteArray
}

interface DatabaseKeyProtector {
    val capability: KeystoreCapability
    fun wrap(plaintext: ByteArray): ByteArray
    fun unwrap(blob: ByteArray): ByteArray
}

open class DatabaseKeyProtectionException(
    val error: DatabaseKeyBootstrapError,
    cause: Throwable? = null,
) : GeneralSecurityException(cause)
