package tech.quantanex.taskmanager.persistence.crypto

import java.security.SecureRandom

class SecureRandomDatabaseKeyGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) : DatabaseKeyGenerator {
    override fun generate(): ByteArray = ByteArray(DatabaseKeyMaterial.DATABASE_KEY_BYTES).also(secureRandom::nextBytes)
}

class DatabaseKeyBootstrapper(
    private val store: WrappedDatabaseKeyStore,
    private val protector: DatabaseKeyProtector,
    private val keyGenerator: DatabaseKeyGenerator = SecureRandomDatabaseKeyGenerator(),
    private val elapsedMillis: () -> Long,
    private val maxSetupMillis: Long = DEFAULT_MAX_SETUP_MILLIS,
) {
    fun openOrCreate(): DatabaseKeyBootstrapResult {
        val existing = try {
            store.read()
        } catch (_: RuntimeException) {
            return DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyUnavailable)
        }

        val result = if (existing == null) createAndWrapNewKey() else unwrapExistingKey(existing)
        val elapsed = elapsedMillis()
        if (elapsed > maxSetupMillis) {
            (result as? DatabaseKeyBootstrapResult.Success)?.keyMaterial?.close()
            return DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.SetupTimedOut(elapsed, maxSetupMillis))
        }
        return result
    }

    private fun createAndWrapNewKey(): DatabaseKeyBootstrapResult {
        val plaintext = keyGenerator.generate()
        try {
            val wrapped = protector.wrap(plaintext)
            if (!store.writeIfAbsent(wrapped)) {
                plaintext.fill(0)
                return openOrCreate()
            }
            return DatabaseKeyBootstrapResult.Success(DatabaseKeyMaterial(plaintext), protector.capability)
        } catch (error: DatabaseKeyProtectionException) {
            plaintext.fill(0)
            return DatabaseKeyBootstrapResult.Failure(error.error)
        } catch (_: RuntimeException) {
            plaintext.fill(0)
            return DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.KeyUnavailable)
        }
    }

    private fun unwrapExistingKey(blob: ByteArray): DatabaseKeyBootstrapResult = try {
        val plaintext = protector.unwrap(blob)
        if (plaintext.size != DatabaseKeyMaterial.DATABASE_KEY_BYTES) {
            plaintext.fill(0)
            DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.CorruptKeyMaterial)
        } else {
            DatabaseKeyBootstrapResult.Success(DatabaseKeyMaterial(plaintext), protector.capability)
        }
    } catch (error: DatabaseKeyProtectionException) {
        DatabaseKeyBootstrapResult.Failure(error.error)
    } catch (_: RuntimeException) {
        DatabaseKeyBootstrapResult.Failure(DatabaseKeyBootstrapError.CorruptKeyMaterial)
    }

    companion object {
        const val DEFAULT_MAX_SETUP_MILLIS = 1_500L
    }
}
