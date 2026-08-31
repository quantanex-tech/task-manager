package tech.quantanex.taskmanager.persistence.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TaskMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE inbox_tasks ADD COLUMN reminder_epoch_millis INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
