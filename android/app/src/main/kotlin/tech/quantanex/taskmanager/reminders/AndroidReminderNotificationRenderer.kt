package tech.quantanex.taskmanager.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tech.quantanex.taskmanager.R
import tech.quantanex.taskmanager.domain.InboxTask
import java.time.Instant

private const val REMINDER_CHANNEL_ID = "local_exact_reminders"
private const val GENERIC_REMINDER_TITLE = "Task reminder"
private const val GENERIC_REMINDER_TEXT = "Open Task Manager to view this reminder."

class AndroidReminderNotificationRenderer(
    private val context: Context,
) : ReminderNotificationRenderer {
    override fun render(task: InboxTask?, dueAt: Instant) {
        ensureChannel()
        val privateNotification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task_notification)
            .setContentTitle(GENERIC_REMINDER_TITLE)
            .setContentText(GENERIC_REMINDER_TEXT)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setLocalOnly(true)
            .build()
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task_notification)
            .setContentTitle(task?.title?.value ?: GENERIC_REMINDER_TITLE)
            .setContentText(if (task == null) GENERIC_REMINDER_TEXT else "Reminder due")
            .setPublicVersion(privateNotification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setLocalOnly(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(task, dueAt), notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked after scheduling. Fail closed without logging content.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Local exact reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Local-only task reminders with private lock-screen presentation."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(task: InboxTask?, dueAt: Instant): Int =
        task?.id?.value?.hashCode() ?: dueAt.toEpochMilli().hashCode()
}
