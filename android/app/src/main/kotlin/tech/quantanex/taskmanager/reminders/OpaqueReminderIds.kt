package tech.quantanex.taskmanager.reminders

import tech.quantanex.taskmanager.domain.InboxTask
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object OpaqueReminderIds {
    fun forTask(task: InboxTask): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(task.id.value.toByteArray(StandardCharsets.UTF_8))
        return ((digest[0].toInt() and 0xff) shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or
            (digest[3].toInt() and 0xff)
    }
}
