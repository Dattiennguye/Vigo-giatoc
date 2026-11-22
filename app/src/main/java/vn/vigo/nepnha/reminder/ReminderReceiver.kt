package vn.vigo.nepnha.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.vigo.nepnha.NepNhaApp

/** Nhận báo thức và hiện thông báo; chống trùng bằng khóa trong nhật ký thông báo. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY) ?: return
        val r = PlannedReminder(
            key = key,
            eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID).orEmpty(),
            triggerAt = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER, 0L),
            title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty(),
            body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY).orEmpty(),
        )
        val pending = goAsync()
        val container = (context.applicationContext as NepNhaApp).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Sự kiện có thể đã bị xóa/sửa sau khi đặt báo thức: kiểm tra lại trước khi hiện.
                val ev = container.db.events().get(r.eventId)
                if (ev != null && !ev.deleted) container.scheduler.deliver(r)
                container.db.alarms().delete(r.key)
                container.scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
