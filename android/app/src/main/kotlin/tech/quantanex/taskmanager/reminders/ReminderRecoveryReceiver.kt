package tech.quantanex.taskmanager.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.quantanex.taskmanager.data.EncryptedInboxTaskStoreFactory
import tech.quantanex.taskmanager.data.InboxStoreOpenOutcome
import tech.quantanex.taskmanager.data.InboxTaskStore
import tech.quantanex.taskmanager.domain.InboxTask

class ReminderRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RECOVERY_ACTIONS) return
        val appContext = context.applicationContext
        when (val outcome = EncryptedInboxTaskStoreFactory.open(appContext)) {
            is InboxStoreOpenOutcome.Opened -> outcome.store.use { store ->
                val coordinator = LocalReminderCoordinator(
                    notificationPermissionGate = AndroidNotificationPermissionGate(appContext),
                    scheduler = AndroidExactReminderScheduler(appContext),
                )
                LocalReminderRecovery(
                    coordinator = coordinator,
                    store = InboxReminderStateStore(store),
                ).recover()
            }
            is InboxStoreOpenOutcome.Unavailable -> Unit
        }
    }

    private companion object {
        val RECOVERY_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}

private class InboxReminderStateStore(
    private val store: InboxTaskStore,
) : ReminderStateStore {
    override fun listReminderTasks() = store.listInbox().filter { it.reminderAt != null }

    override fun persistReminderDeliveryState(task: InboxTask, state: ReminderDeliveryState) {
        store.setReminderDeliveryState(task, state)
    }
}
