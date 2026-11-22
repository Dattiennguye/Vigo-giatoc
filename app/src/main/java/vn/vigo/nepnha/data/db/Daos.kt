package vn.vigo.nepnha.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY month, day")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deleted = 0")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: String): EventEntity?

    @Upsert
    suspend fun upsert(e: EventEntity)

    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM events WHERE deleted = 0")
    suspend fun count(): Int
}

data class TaskWithProgress(
    val id: String,
    val title: String,
    val assignee: String?,
    val dueEpochDay: Long?,
    val status: String,
    val eventId: String?,
    val isTemplate: Boolean,
    val total: Int,
    val done: Int,
)

@Dao
abstract class TaskDao {
    @Query(
        """SELECT t.id, t.title, t.assignee, t.dueEpochDay, t.status, t.eventId, t.isTemplate,
           (SELECT COUNT(*) FROM checklist_items c WHERE c.taskId = t.id) AS total,
           (SELECT COUNT(*) FROM checklist_items c WHERE c.taskId = t.id AND c.done = 1) AS done
           FROM tasks t WHERE t.deleted = 0 AND t.isTemplate = :templates
           ORDER BY CASE t.status WHEN 'done' THEN 1 ELSE 0 END, t.dueEpochDay IS NULL, t.dueEpochDay, t.updatedAt DESC""",
    )
    abstract fun observe(templates: Boolean): Flow<List<TaskWithProgress>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId ORDER BY position")
    abstract suspend fun items(taskId: String): List<ChecklistItemEntity>

    @Upsert
    abstract suspend fun upsert(t: TaskEntity)

    @Query("DELETE FROM checklist_items WHERE taskId = :taskId")
    abstract suspend fun clearItems(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertItems(items: List<ChecklistItemEntity>)

    @Transaction
    open suspend fun save(t: TaskEntity, items: List<ChecklistItemEntity>) {
        upsert(t)
        clearItems(t.id)
        insertItems(items)
    }

    @Query("UPDATE tasks SET deleted = 1, updatedAt = :now WHERE id = :id")
    abstract suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE tasks SET status = :status, updatedAt = :now WHERE id = :id")
    abstract suspend fun setStatus(id: String, status: String, now: Long)

    @Query("SELECT COUNT(*) FROM tasks WHERE deleted = 0 AND isTemplate = 0 AND status != 'done'")
    abstract fun observeOpenCount(): Flow<Int>
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_log ORDER BY createdAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_log WHERE read = 0")
    fun observeUnread(): Flow<Int>

    /** Trả về -1 nếu đã tồn tại (chống trùng). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(n: NotificationLogEntity): Long

    @Query("UPDATE notification_log SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notification_log WHERE createdAt < :before")
    suspend fun prune(before: Long)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM scheduled_alarms")
    suspend fun all(): List<ScheduledAlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ScheduledAlarmEntity>)

    @Query("DELETE FROM scheduled_alarms WHERE dedupeKey IN (:keys)")
    suspend fun delete(keys: List<String>)

    @Query("DELETE FROM scheduled_alarms WHERE dedupeKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface OutboxDao {
    @Insert
    suspend fun enqueue(item: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt LIMIT :limit")
    suspend fun next(limit: Int): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>
}
