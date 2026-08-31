package tech.quantanex.taskmanager.persistence.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(task: TaskEntity)

    @Update
    fun update(task: TaskEntity)

    @Delete
    fun delete(task: TaskEntity)

    @Query("SELECT * FROM inbox_tasks WHERE id = :id LIMIT 1")
    fun get(id: String): TaskEntity?

    @Query("SELECT * FROM inbox_tasks ORDER BY rowid ASC")
    fun listInbox(): List<TaskEntity>
}
