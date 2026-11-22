package vn.vigo.nepnha

import android.app.Application
import vn.vigo.nepnha.reminder.NotificationHelper
import vn.vigo.nepnha.reminder.RescheduleWorker

class NepNhaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        // Định kỳ làm mới lịch nhắc + chạy ngay một lần (khôi phục nếu hệ thống đã xóa báo thức).
        RescheduleWorker.schedulePeriodic(this)
        RescheduleWorker.runOnce(this)
    }
}
