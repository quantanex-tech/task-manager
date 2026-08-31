package tech.quantanex.taskmanager.persistence.crypto

import android.content.Context
import java.io.File

class NoBackupWrappedDatabaseKeyStore(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
) : WrappedDatabaseKeyStore {
    private val file: File = File(File(context.noBackupFilesDir, SECRET_DIRECTORY).also { it.mkdirs() }, fileName)

    override fun read(): ByteArray? = if (file.exists()) file.readBytes() else null

    override fun writeIfAbsent(blob: ByteArray): Boolean {
        val directory = file.parentFile ?: return false
        if (!directory.exists() && !directory.mkdirs()) return false
        if (file.exists()) return false
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeBytes(blob)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            return false
        }
        return true
    }

    companion object {
        const val SECRET_DIRECTORY = "task-manager-secrets"
        const val DEFAULT_FILE_NAME = "database-key-v1.wrapper"
    }
}
