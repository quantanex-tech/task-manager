package tech.quantanex.taskmanager.persistence.crypto

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class NoBackupWrappedDatabaseKeyStore internal constructor(
    private val file: File,
) : WrappedDatabaseKeyStore {
    constructor(
        context: Context,
        fileName: String = DEFAULT_FILE_NAME,
    ) : this(File(File(context.noBackupFilesDir, SECRET_DIRECTORY), fileName))

    override fun read(): ByteArray? = synchronized(FILE_LOCK) {
        if (file.exists()) file.readBytes() else null
    }

    override fun writeIfAbsent(blob: ByteArray): WrappedDatabaseKeyStoreWriteResult = synchronized(FILE_LOCK) {
        val directory = file.parentFile ?: return@synchronized WrappedDatabaseKeyStoreWriteResult.Failed
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            return@synchronized WrappedDatabaseKeyStoreWriteResult.Failed
        }
        try {
            if (!file.createNewFile()) return@synchronized WrappedDatabaseKeyStoreWriteResult.AlreadyExists
        } catch (_: IOException) {
            return@synchronized WrappedDatabaseKeyStoreWriteResult.Failed
        } catch (_: RuntimeException) {
            return@synchronized WrappedDatabaseKeyStoreWriteResult.Failed
        }
        try {
            FileOutputStream(file).use { output ->
                output.write(blob)
                output.fd.sync()
            }
            WrappedDatabaseKeyStoreWriteResult.Written
        } catch (_: RuntimeException) {
            file.delete()
            WrappedDatabaseKeyStoreWriteResult.Failed
        } catch (_: IOException) {
            file.delete()
            WrappedDatabaseKeyStoreWriteResult.Failed
        }
    }

    companion object {
        const val SECRET_DIRECTORY = "task-manager-secrets"
        const val DEFAULT_FILE_NAME = "database-key-v1.wrapper"
        private val FILE_LOCK = Any()
    }
}
