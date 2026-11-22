package vn.vigo.nepnha.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventEntity::class,
        TaskEntity::class,
        ChecklistItemEntity::class,
        NotificationLogEntity::class,
        ScheduledAlarmEntity::class,
        OutboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun tasks(): TaskDao
    abstract fun notifications(): NotificationDao
    abstract fun alarms(): AlarmDao
    abstract fun outbox(): OutboxDao

    companion object {
        const val NAME = "nepnha.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                // WAL: ghi an toàn khi ứng dụng bị đóng đột ngột, đọc không chặn ghi.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // Không dùng fallbackToDestructiveMigration: tuyệt đối không xóa dữ liệu người dùng khi nâng cấp.
                .build()
    }
}
