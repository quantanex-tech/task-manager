package tech.quantanex.taskmanager.persistence.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class TaskMigrationsInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = TaskManagerDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun validatesFreshSchemaVersion() {
        val databaseName = "fresh-schema-${System.nanoTime()}.db"
        helper.createDatabase(databaseName, TaskManagerDatabase.SCHEMA_VERSION).apply {
            execSQL("INSERT INTO inbox_tasks (id, title, is_completed, reminder_epoch_millis) VALUES ('fixture-fresh', 'Synthetic fresh fixture', 0, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, TaskManagerDatabase.SCHEMA_VERSION, true)
    }

    @Test
    fun migratesVersionOneTasksToVersionTwoReminderColumn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-1-2-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        helper.createDatabase(databaseName, 1).apply {
            execSQL("CREATE TABLE IF NOT EXISTS inbox_tasks (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `is_completed` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            execSQL("INSERT INTO inbox_tasks (id, title, is_completed) VALUES ('fixture-migrated', 'Synthetic migration fixture', 1)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(databaseName, 2, true, TaskMigrations.MIGRATION_1_2)
        migrated.query("SELECT title, is_completed, reminder_epoch_millis FROM inbox_tasks WHERE id = 'fixture-migrated'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Synthetic migration fixture", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertNull(cursor.getString(2))
        }
        migrated.close()
    }
}
