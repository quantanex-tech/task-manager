package tech.quantanex.taskmanager.persistence

import android.content.Context
import androidx.room.Room
import tech.quantanex.taskmanager.domain.TaskIdGenerator
import tech.quantanex.taskmanager.domain.TaskRepository
import tech.quantanex.taskmanager.domain.UuidTaskIdGenerator
import tech.quantanex.taskmanager.persistence.crypto.AndroidKeystoreDatabaseKeyProtector
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyBootstrapError
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyBootstrapResult
import tech.quantanex.taskmanager.persistence.crypto.DatabaseKeyBootstrapper
import tech.quantanex.taskmanager.persistence.crypto.KeystoreCapability
import tech.quantanex.taskmanager.persistence.crypto.NoBackupWrappedDatabaseKeyStore
import tech.quantanex.taskmanager.persistence.db.TaskManagerDatabase
import tech.quantanex.taskmanager.persistence.db.TaskMigrations
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object EncryptedTaskRepositoryFactory {
    const val DEFAULT_DATABASE_NAME = "task-manager.db"

    fun open(
        context: Context,
        databaseName: String = DEFAULT_DATABASE_NAME,
        idGenerator: TaskIdGenerator = UuidTaskIdGenerator(),
        keyBootstrapper: DatabaseKeyBootstrapper = defaultBootstrapper(context),
    ): EncryptedTaskRepositoryOpenResult {
        val startedAt = System.nanoTime()
        return when (val bootstrap = keyBootstrapper.openOrCreate()) {
            is DatabaseKeyBootstrapResult.Failure -> EncryptedTaskRepositoryOpenResult.Failure(
                TaskStoreOpenError.KeyBootstrapFailed(bootstrap.error)
            )
            is DatabaseKeyBootstrapResult.Success -> openEncryptedDatabase(
                context = context,
                databaseName = databaseName,
                idGenerator = idGenerator,
                keyBytes = bootstrap.keyMaterial.copyBytes(),
                capability = bootstrap.capability,
                startedAtNanos = startedAt,
            ).also { bootstrap.keyMaterial.close() }
        }
    }

    private fun openEncryptedDatabase(
        context: Context,
        databaseName: String,
        idGenerator: TaskIdGenerator,
        keyBytes: ByteArray,
        capability: KeystoreCapability,
        startedAtNanos: Long,
    ): EncryptedTaskRepositoryOpenResult = try {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(keyBytes, null, true)
        val database = Room.databaseBuilder(context.applicationContext, TaskManagerDatabase::class.java, databaseName)
            .openHelperFactory(factory)
            .addMigrations(*TaskMigrations.ALL)
            .build()
        database.openHelper.writableDatabase.query("PRAGMA cipher_version").close()
        val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
        EncryptedTaskRepositoryOpenResult.Success(
            repository = RoomTaskRepository(database.taskDao(), idGenerator),
            closeable = EncryptedTaskRepositoryCloseable { database.close() },
            diagnostics = EncryptedTaskRepositoryDiagnostics(
                keystoreCapability = capability,
                setupElapsedMillis = elapsedMillis,
            ),
        )
    } catch (error: UnsatisfiedLinkError) {
        keyBytes.fill(0)
        EncryptedTaskRepositoryOpenResult.Failure(TaskStoreOpenError.UnsupportedCipher(error.message))
    } catch (error: IllegalStateException) {
        keyBytes.fill(0)
        EncryptedTaskRepositoryOpenResult.Failure(TaskStoreOpenError.DatabaseOpenFailed(error.safeMessage()))
    } catch (error: android.database.sqlite.SQLiteException) {
        keyBytes.fill(0)
        EncryptedTaskRepositoryOpenResult.Failure(TaskStoreOpenError.DatabaseOpenFailed(error.safeMessage()))
    } catch (error: RuntimeException) {
        keyBytes.fill(0)
        EncryptedTaskRepositoryOpenResult.Failure(TaskStoreOpenError.DatabaseOpenFailed(error.safeMessage()))
    }

    fun defaultBootstrapper(context: Context): DatabaseKeyBootstrapper {
        val startedAt = System.nanoTime()
        return DatabaseKeyBootstrapper(
            store = NoBackupWrappedDatabaseKeyStore(context.applicationContext),
            protector = AndroidKeystoreDatabaseKeyProtector(),
            elapsedMillis = { (System.nanoTime() - startedAt) / 1_000_000 },
        )
    }

    private fun Throwable.safeMessage(): String = this::class.java.simpleName
}

sealed interface EncryptedTaskRepositoryOpenResult {
    data class Success(
        val repository: TaskRepository,
        val closeable: EncryptedTaskRepositoryCloseable,
        val diagnostics: EncryptedTaskRepositoryDiagnostics,
    ) : EncryptedTaskRepositoryOpenResult

    data class Failure(val error: TaskStoreOpenError) : EncryptedTaskRepositoryOpenResult
}

fun interface EncryptedTaskRepositoryCloseable {
    fun close()
}

data class EncryptedTaskRepositoryDiagnostics(
    val keystoreCapability: KeystoreCapability,
    val setupElapsedMillis: Long,
)

sealed interface TaskStoreOpenError {
    data class KeyBootstrapFailed(val error: DatabaseKeyBootstrapError) : TaskStoreOpenError
    data class UnsupportedCipher(val safeReason: String?) : TaskStoreOpenError
    data class DatabaseOpenFailed(val safeReason: String?) : TaskStoreOpenError
}
