package tech.quantanex.taskmanager.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.quantanex.taskmanager.data.InboxStoreOpenOutcome
import tech.quantanex.taskmanager.data.EncryptedInboxTaskStoreFactory
import java.time.Instant

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dueAt = Instant.now()
        when (val outcome = EncryptedInboxTaskStoreFactory.open(context.applicationContext)) {
            is InboxStoreOpenOutcome.Opened -> outcome.store.use { store ->
                val alarmReminderId = intent.data?.lastPathSegment?.toIntOrNull()
                val dueTasks = store.listInbox().filter { task ->
                    val reminderAt = task.reminderAt?.instant
                    reminderAt != null &&
                        !reminderAt.isAfter(dueAt) &&
                        alarmReminderId == OpaqueReminderIds.forTask(task)
                }
                val renderer = AndroidReminderNotificationRenderer(context.applicationContext)
                if (dueTasks.isEmpty()) {
                    renderer.render(task = null, dueAt = dueAt)
                } else {
                    dueTasks.forEach { renderer.render(task = it, dueAt = it.reminderAt!!.instant) }
                }
            }
            is InboxStoreOpenOutcome.Unavailable -> AndroidReminderNotificationRenderer(context.applicationContext)
                .render(task = null, dueAt = dueAt)
        }
    }
}
