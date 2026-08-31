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
                val renderer = AndroidReminderNotificationRenderer(context.applicationContext)
                LocalReminderAlarmDelivery().notificationsFor(
                    tasks = store.listInbox(),
                    alarmReminderId = alarmReminderId,
                    dueAt = dueAt,
                ).forEach { renderer.render(task = it.task, dueAt = it.dueAt) }
            }
            is InboxStoreOpenOutcome.Unavailable -> Unit
        }
    }
}
