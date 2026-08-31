package tech.quantanex.taskmanager.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import tech.quantanex.taskmanager.domain.InboxTask

private const val REMINDER_ALARM_ACTION = "tech.quantanex.taskmanager.reminders.EXACT_REMINDER_DUE"
private const val REMINDER_ALARM_REQUEST_NAMESPACE = "local-reminder"

class AndroidExactReminderScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java),
) : ExactReminderScheduler {
    override fun schedule(task: InboxTask): ReminderDeliveryState {
        val reminder = task.reminderAt ?: return ReminderDeliveryState.NoReminder
        if (!canScheduleExactAlarms()) {
            cancel(task)
            return ReminderDeliveryState.ExactAlarmUnavailable
        }
        val pendingIntent = requireNotNull(pendingIntentFor(task, PendingIntent.FLAG_UPDATE_CURRENT))
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.instant.toEpochMilli(),
            pendingIntent,
        )
        return ReminderDeliveryState.Scheduled
    }

    override fun cancel(task: InboxTask) {
        alarmManager.cancel(pendingIntentFor(task, PendingIntent.FLAG_NO_CREATE) ?: return)
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntentFor(task: InboxTask, lookupFlag: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = REMINDER_ALARM_ACTION
            data = Uri.parse("taskmanager://reminders/$REMINDER_ALARM_REQUEST_NAMESPACE/${OpaqueReminderIds.forTask(task)}")
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or lookupFlag
        return PendingIntent.getBroadcast(context, OpaqueReminderIds.forTask(task), intent, flags)
    }
}
