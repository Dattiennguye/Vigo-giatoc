package vn.vigo.nepnha.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Trạng thái đồng bộ của bản ghi cục bộ (offline-first). */
object SyncState {
    const val LOCAL_ONLY = 0      // chỉ thuộc gia đình cá nhân, không đồng bộ
    const val PENDING = 1         // đã sửa offline, chờ đẩy lên
    const val SYNCED = 2
    const val CONFLICT = 3        // máy chủ đã có phiên bản mới hơn – cần người dùng chọn
}

@Entity(
    tableName = "events",
    indices = [Index("type"), Index("deleted"), Index("clanId")],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** EventType.name */
    val type: String,
    /** CalendarKind.name */
    val calendarKind: String,
    val day: Int,
    val month: Int,
    val year: Int,
    val leapMonth: Boolean,
    /** RepeatKind.name */
    val repeat: String,
    val intervalDays: Int,
    val hour: Int,
    val minute: Int,
    /** Mốc nhắc trước, số ngày, phân tách dấu phẩy. 0 = trong ngày. Ví dụ "7,1,0". */
    val reminderDays: String,
    val personName: String?,
    val location: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val clanId: String? = null,
    val remoteVersion: Int = 0,
    val syncState: Int = SyncState.LOCAL_ONLY,
)

@Entity(tableName = "tasks", indices = [Index("eventId"), Index("isTemplate"), Index("deleted")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val assignee: String?,
    /** epochDay của hạn hoàn thành */
    val dueEpochDay: Long?,
    /** todo | doing | done */
    val status: String,
    val eventId: String?,
    val isTemplate: Boolean,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val syncState: Int = SyncState.LOCAL_ONLY,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val text: String,
    val done: Boolean,
    val position: Int,
)

/** Nhật ký thông báo nội bộ; khóa [dedupeKey] chống hiện trùng một lần nhắc. */
@Entity(tableName = "notification_log", indices = [Index(value = ["dedupeKey"], unique = true), Index("createdAt")])
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val title: String,
    val body: String,
    val eventId: String?,
    val createdAt: Long,
    val read: Boolean = false,
)

/** Các báo thức đang đặt trong AlarmManager, để hủy chính xác khi sự kiện thay đổi. */
@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarmEntity(
    @PrimaryKey val dedupeKey: String,
    val requestCode: Int,
    val eventId: String,
    val triggerAt: Long,
)

/** Hàng đợi thay đổi chờ đồng bộ lên máy chủ (dùng cho dữ liệu gia tộc). */
@Entity(tableName = "outbox", indices = [Index("createdAt")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,
    val entityId: String,
    /** upsert | delete */
    val op: String,
    val payloadJson: String,
    val baseVersion: Int,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)
