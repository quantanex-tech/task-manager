package tech.quantanex.taskmanager.persistence.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class],
    version = TaskManagerDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class TaskManagerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val SCHEMA_VERSION = 3
    }
}
