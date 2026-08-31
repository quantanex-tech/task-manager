package tech.quantanex.taskmanager.persistence.crypto

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class NoBackupWrappedDatabaseKeyStore(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
) : WrappedDatabaseKeyStore {
    private val file: File = File(File(context.noBackupFilesDir, SECRET_DIRECTORY).also { it.mkdirs() }, fileName)

    override fun read(): ByteArray? = synchronized(FILE_LOCK) {
        if (file.exists()) file.readBytes() else null
    }

    override fun writeIfAbsent(blob: ByteArray): Boolean = synchronized(FILE_LOCK) {
        val directory = file.parentFile ?: return@synchronized false
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) return@synchronized false
        if (!file.createNewFile()) return@synchronized false
        try {
            FileOutputStream(file).use { output ->
                output.write(blob)
                output.fd.sync()
            }
            true
        } catch (_: RuntimeException) {
            file.delete()
            false
        } catch (_: java.io.IOException) {
            file.delete()
            false
        }
    }

    companion object {
        const val SECRET_DIRECTORY = "task-manager-secrets"
        const val DEFAULT_FILE_NAME = "database-key-v1.wrapper"
        private val FILE_LOCK = Any()
    }
}
