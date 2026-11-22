package vn.vigo.nepnha

import android.content.Context
import vn.vigo.nepnha.data.db.AppDatabase
import vn.vigo.nepnha.data.remote.ClanRepository
import vn.vigo.nepnha.data.remote.SessionStore
import vn.vigo.nepnha.data.remote.SupabaseClient
import vn.vigo.nepnha.data.repo.EventRepository
import vn.vigo.nepnha.data.repo.SettingsRepository
import vn.vigo.nepnha.data.repo.TaskRepository
import vn.vigo.nepnha.reminder.ReminderScheduler

/**
 * Nơi khởi tạo phụ thuộc (DI thủ công). Chọn DI thủ công thay vì Hilt để giảm thời gian khởi động
 * và dung lượng APK trên máy cấu hình thấp; mọi thành phần được tạo lười (lazy) khi cần lần đầu.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.build(app) }
    val scheduler: ReminderScheduler by lazy { ReminderScheduler(app, db) }
    val events: EventRepository by lazy { EventRepository(db.events(), scheduler) }
    val tasks: TaskRepository by lazy { TaskRepository(db.tasks()) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }

    /** Backend gia tộc chỉ khả dụng khi đã cấu hình Supabase lúc build. */
    val isBackendConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** null khi bản build chưa cấu hình máy chủ. */
    val supabase: SupabaseClient? by lazy {
        if (isBackendConfigured) SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, SessionStore(app)) else null
    }
    val clans: ClanRepository? by lazy { supabase?.let { ClanRepository(it) } }
}
