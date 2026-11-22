package vn.vigo.nepnha.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vn.vigo.nepnha.data.db.AppDatabase
import vn.vigo.nepnha.data.db.NotificationLogEntity
import vn.vigo.nepnha.data.db.ScheduledAlarmEntity
import vn.vigo.nepnha.data.repo.EventRepository
import vn.vigo.nepnha.domain.FamilyEvent
import vn.vigo.nepnha.domain.toDomain
import vn.vigo.nepnha.lunar.VietLunarCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Một lần nhắc cụ thể. [key] là khóa chống trùng: sự kiện | ngày diễn ra | số ngày nhắc trước. */
data class PlannedReminder(
    val key: String,
    val eventId: String,
    val triggerAt: Long,
    val title: String,
    val body: String,
)

/**
 * Lập lịch nhắc cục bộ bằng AlarmManager.
 *  - Chỉ đặt các báo thức trong cửa sổ gần (giới hạn số lượng để không vượt hạn mức hệ thống).
 *  - Gọi lại khi: sửa/xóa sự kiện, khởi động máy, đổi giờ/múi giờ, cập nhật app, và định kỳ mỗi 12 giờ.
 *  - Lần nhắc bị lỡ (máy tắt) trong vòng 12 giờ sẽ được hiện bù một lần.
 */
class ReminderScheduler(private val context: Context, private val db: AppDatabase) {

    private val mutex = Mutex()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    enum class Mode { EXACT, INEXACT }

    @Volatile var lastMode: Mode = Mode.EXACT
        private set

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    suspend fun rescheduleAll() = withContext(Dispatchers.Default) {
        mutex.withLock {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val events = db.events().getAll().map { it.toDomain() }
            val planned = plan(events, LocalDate.now(zone), now, zone)

            // Bù các lần nhắc vừa lỡ (máy tắt/hệ thống trì hoãn) trong 12 giờ qua.
            planned.filter { it.triggerAt in (now - LATE_WINDOW_MS)..now }.forEach { deliver(it) }

            val upcoming = planned.filter { it.triggerAt > now }.take(MAX_ALARMS)
            val wanted = upcoming.associateBy { it.key }
            val existing = db.alarms().all()

            val stale = existing.filter { it.dedupeKey !in wanted }
            stale.forEach { cancel(it.requestCode) }
            if (stale.isNotEmpty()) db.alarms().delete(stale.map { it.dedupeKey })

            val existingKeys = existing.associateBy { it.dedupeKey }
            val toSet = upcoming.filter { existingKeys[it.key]?.triggerAt != it.triggerAt }
            toSet.forEach { set(it) }
            db.alarms().insertAll(toSet.map { ScheduledAlarmEntity(it.key, requestCodeOf(it.key), it.eventId, it.triggerAt) })
        }
    }

    /** Hiện thông báo (nếu chưa từng hiện cho khóa này). Trả về true nếu là lần đầu. */
    suspend fun deliver(r: PlannedReminder): Boolean {
        val inserted = db.notifications().insert(
            NotificationLogEntity(
                dedupeKey = r.key,
                title = r.title,
                body = r.body,
                eventId = r.eventId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (inserted == -1L) return false
        NotificationHelper.post(context, requestCodeOf(r.key), r.title, r.body)
        return true
    }

    private fun intentFor(requestCode: Int, r: PlannedReminder?): PendingIntent? {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        if (r != null) {
            i.putExtra(EXTRA_KEY, r.key)
                .putExtra(EXTRA_EVENT_ID, r.eventId)
                .putExtra(EXTRA_TITLE, r.title)
                .putExtra(EXTRA_BODY, r.body)
                .putExtra(EXTRA_TRIGGER, r.triggerAt)
        }
        val mode = if (r == null) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        val flags = PendingIntent.FLAG_IMMUTABLE or mode
        return PendingIntent.getBroadcast(context, requestCode, i, flags)
    }

    // Quyền được kiểm tra bằng canScheduleExact(); nếu không có quyền thì dùng báo thức không chính xác.
    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    private fun set(r: PlannedReminder) {
        val am = alarmManager ?: return
        val pi = intentFor(requestCodeOf(r.key), r) ?: return
        try {
            if (canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAt, pi)
                lastMode = Mode.EXACT
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAt, pi)
                lastMode = Mode.INEXACT
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAt, pi)
            lastMode = Mode.INEXACT
        }
    }

    private fun cancel(requestCode: Int) {
        val pi = intentFor(requestCode, null) ?: return
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    /** Gửi một thông báo thử ngay để người dùng kiểm tra. */
    fun postTest(): Boolean = NotificationHelper.post(
        context, 999_001, "Nếp Nhà – Thông báo thử",
        "Nếu bạn thấy dòng này, thông báo đang hoạt động. Hãy làm theo hướng dẫn để nhắc lịch không bị chặn khi chạy nền.",
    )

    companion object {
        const val ACTION_REMIND = "vn.vigo.nepnha.REMIND"
        const val EXTRA_KEY = "key"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_TRIGGER = "trigger"

        const val MAX_ALARMS = 150
        const val HORIZON_DAYS = 120L
        const val LATE_WINDOW_MS = 12 * 60 * 60 * 1000L

        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun requestCodeOf(key: String): Int = key.hashCode() and 0x7FFFFFFF

        /** Thuần tính toán (kiểm thử được): danh sách lần nhắc, tăng dần theo thời điểm. */
        fun plan(events: List<FamilyEvent>, today: LocalDate, nowMs: Long, zone: ZoneId): List<PlannedReminder> {
            val maxLead = events.flatMap { it.reminderDays }.maxOrNull() ?: 0
            val from = today.minusDays(1)
            val to = today.plusDays(HORIZON_DAYS + maxLead)
            val occ = EventRepository.computeOccurrences(events.filter { it.reminderDays.isNotEmpty() }, from, to)
            val out = ArrayList<PlannedReminder>()
            for (o in occ) {
                for (lead in o.event.reminderDays) {
                    val day = o.date.minusDays(lead.toLong())
                    val at = LocalDateTime.of(day.year, day.monthValue, day.dayOfMonth, o.event.hour, o.event.minute)
                        .atZone(zone).toInstant().toEpochMilli()
                    if (at < nowMs - LATE_WINDOW_MS) continue
                    out += PlannedReminder(
                        key = "${o.event.id}|${o.date}|$lead",
                        eventId = o.event.id,
                        triggerAt = at,
                        title = if (lead == 0) "Hôm nay: ${o.event.title}" else "Còn $lead ngày: ${o.event.title}",
                        body = describe(o.event, o.date),
                    )
                }
            }
            out.sortBy { it.triggerAt }
            return out
        }

        fun describe(e: FamilyEvent, date: LocalDate): String {
            val l = VietLunarCalendar.solarToLunar(date)
            val lunar = "${l.day}/${l.month}${if (l.isLeapMonth) " nhuận" else ""} âm lịch"
            val time = "%02d:%02d".format(e.hour, e.minute)
            val who = e.personName?.let { " – $it" } ?: ""
            return "${e.type.label}$who. Ngày ${date.format(DATE_FMT)} ($lunar), $time" +
                (e.location?.let { ". Tại $it" } ?: "")
        }
    }
}
