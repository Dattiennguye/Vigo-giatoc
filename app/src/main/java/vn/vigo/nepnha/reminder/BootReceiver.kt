package vn.vigo.nepnha.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Khôi phục lịch nhắc sau khi khởi động lại, đổi giờ, đổi múi giờ hoặc cập nhật ứng dụng. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RescheduleWorker.runOnce(context)
    }
}
