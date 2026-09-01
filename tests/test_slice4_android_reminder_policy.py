from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP_MAIN = ROOT / "android" / "app" / "src" / "main"


def read(relative: str) -> str:
    return (APP_MAIN / relative).read_text(encoding="utf-8")


class Slice4AndroidReminderPolicyTest(unittest.TestCase):
    def test_reminder_manifest_declares_contextual_notification_exact_alarm_and_recovery_receivers(self):
        manifest = read("AndroidManifest.xml")

        self.assertIn("android.permission.POST_NOTIFICATIONS", manifest)
        self.assertIn("android.permission.SCHEDULE_EXACT_ALARM", manifest)
        self.assertIn("android.permission.RECEIVE_BOOT_COMPLETED", manifest)
        self.assertIn(".reminders.ReminderAlarmReceiver", manifest)
        self.assertIn(".reminders.ReminderRecoveryReceiver", manifest)
        self.assertIn("android.intent.action.BOOT_COMPLETED", manifest)
        self.assertIn("android.intent.action.LOCKED_BOOT_COMPLETED", manifest)
        self.assertIn("android.intent.action.TIMEZONE_CHANGED", manifest)
        self.assertIn("android.intent.action.TIME_SET", manifest)

    def test_alarm_intent_does_not_persist_protected_task_payloads_or_due_times(self):
        scheduler = read("kotlin/tech/quantanex/taskmanager/reminders/AndroidExactReminderScheduler.kt")

        self.assertNotIn("putExtra", scheduler)
        self.assertIn("setExactAndAllowWhileIdle", scheduler)
        self.assertIn("canScheduleExactAlarms", scheduler)
        self.assertIn("cancel(task)", scheduler)
        self.assertNotIn("setWindow", scheduler)
        self.assertNotIn("setAndAllowWhileIdle", scheduler)
        self.assertNotIn("setAlarmClock", scheduler)

    def test_recovery_receivers_fail_closed_through_encrypted_store_before_rescheduling_or_rendering(self):
        recovery = read("kotlin/tech/quantanex/taskmanager/reminders/ReminderRecoveryReceiver.kt")
        alarm = read("kotlin/tech/quantanex/taskmanager/reminders/ReminderAlarmReceiver.kt")

        self.assertIn("EncryptedInboxTaskStoreFactory.open", recovery)
        self.assertIn("InboxStoreOpenOutcome.Opened", recovery)
        self.assertIn("InboxStoreOpenOutcome.Unavailable -> Unit", recovery)
        self.assertIn("LocalReminderCoordinator", recovery)
        self.assertIn("EncryptedInboxTaskStoreFactory.open", alarm)
        self.assertIn("InboxStoreOpenOutcome.Unavailable", alarm)
        self.assertNotIn("render(task = null", alarm)
        self.assertIn("LocalReminderAlarmDelivery", alarm)

    def test_delivery_state_is_encrypted_canonical_state_not_plaintext_sidecar(self):
        task_entity = (ROOT / "android" / "persistence" / "src" / "main" / "kotlin" / "tech" / "quantanex" / "taskmanager" / "persistence" / "db" / "TaskEntity.kt").read_text(encoding="utf-8")
        migrations = (ROOT / "android" / "persistence" / "src" / "main" / "kotlin" / "tech" / "quantanex" / "taskmanager" / "persistence" / "db" / "TaskMigrations.kt").read_text(encoding="utf-8")
        app_sources = "\n".join(path.read_text(encoding="utf-8") for path in APP_MAIN.glob("kotlin/**/*.kt"))

        self.assertIn("reminder_delivery_state", task_entity)
        self.assertIn("reminder_delivery_state", migrations)
        self.assertNotIn("getSharedPreferences", app_sources)
        self.assertNotIn("SharedPreferences", app_sources)

    def test_notification_renderer_has_generic_private_fallback_and_no_remote_egress_or_logs(self):
        renderer = read("kotlin/tech/quantanex/taskmanager/reminders/AndroidReminderNotificationRenderer.kt")
        reminders_dir = APP_MAIN / "kotlin" / "tech" / "quantanex" / "taskmanager" / "reminders"
        reminder_sources = "\n".join(path.read_text(encoding="utf-8") for path in reminders_dir.glob("*.kt"))

        self.assertIn("GENERIC_REMINDER_TITLE", renderer)
        self.assertIn("GENERIC_REMINDER_TEXT", renderer)
        self.assertIn("setPublicVersion", renderer)
        self.assertIn("VISIBILITY_PRIVATE", renderer)
        self.assertIn("VISIBILITY_SECRET", renderer)
        for forbidden in ("Log.", "println", "printStackTrace", "http://", "https://", "SharedPreferences"):
            self.assertNotIn(forbidden, reminder_sources)


if __name__ == "__main__":
    unittest.main()
