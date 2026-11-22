package vn.vigo.nepnha.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import vn.vigo.nepnha.data.db.EventDao
import vn.vigo.nepnha.data.db.EventEntity
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.domain.FamilyEvent
import vn.vigo.nepnha.domain.Occurrence
import vn.vigo.nepnha.domain.formatReminderDays
import vn.vigo.nepnha.domain.toDomain
import vn.vigo.nepnha.lunar.CalendarKind
import vn.vigo.nepnha.lunar.LunarDate
import vn.vigo.nepnha.lunar.LunarSpan
import vn.vigo.nepnha.lunar.Recurrence
import vn.vigo.nepnha.lunar.RecurrenceRule
import vn.vigo.nepnha.lunar.RepeatKind
import vn.vigo.nepnha.lunar.VietLunarCalendar
import vn.vigo.nepnha.reminder.ReminderScheduler
import java.time.LocalDate
import java.util.UUID

/** Dữ liệu người dùng nhập ở màn hình tạo/sửa sự kiện. */
data class EventInput(
    val id: String?,
    val title: String,
    val type: EventType,
    val calendar: CalendarKind,
    val day: Int,
    val month: Int,
    val year: Int,
    val leapMonth: Boolean,
    val repeat: RepeatKind,
    val intervalDays: Int,
    val hour: Int,
    val minute: Int,
    val reminderDays: Set<Int>,
    val personName: String?,
    val location: String?,
    val note: String?,
)

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Invalid(val message: String) : SaveResult
}

class EventRepository(
    private val dao: EventDao,
    private val scheduler: ReminderScheduler,
) {
    val events: Flow<List<FamilyEvent>> = dao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.Default)

    suspend fun get(id: String): EventEntity? = dao.get(id)

    fun validate(input: EventInput): String? {
        if (input.title.isBlank()) return "Vui lòng nhập tên sự kiện"
        if (input.title.length > 120) return "Tên sự kiện quá dài (tối đa 120 ký tự)"
        if (input.year !in 1800..2199) return "Năm phải trong khoảng 1800–2199"
        if (input.repeat == RepeatKind.EVERY_N_DAYS && input.intervalDays !in 1..3650) {
            return "Số ngày lặp phải từ 1 đến 3650"
        }
        when (input.calendar) {
            CalendarKind.SOLAR -> runCatching { LocalDate.of(input.year, input.month, input.day) }
                .onFailure { return "Ngày dương lịch không hợp lệ" }
            CalendarKind.LUNAR -> {
                val ok = VietLunarCalendar.lunarToSolar(LunarDate(input.day, input.month, input.year, input.leapMonth))
                if (ok == null) {
                    if (input.leapMonth) return "Năm âm ${input.year} không có tháng ${input.month} nhuận"
                    // Ngày 30 ở tháng thiếu: vẫn cho lưu nếu lặp hằng năm/hằng tháng (sẽ lấy ngày cuối tháng)
                    if (!(input.day == 30 && input.repeat != RepeatKind.NONE)) {
                        return "Tháng ${input.month} âm năm ${input.year} không có ngày ${input.day}"
                    }
                }
            }
        }
        return null
    }

    suspend fun save(input: EventInput): SaveResult {
        validate(input)?.let { return SaveResult.Invalid(it) }
        val now = System.currentTimeMillis()
        val old = input.id?.let { dao.get(it) }
        val entity = EventEntity(
            id = old?.id ?: UUID.randomUUID().toString(),
            title = input.title.trim(),
            type = input.type.name,
            calendarKind = input.calendar.name,
            day = input.day,
            month = input.month,
            year = input.year,
            leapMonth = input.leapMonth,
            repeat = input.repeat.name,
            intervalDays = input.intervalDays.coerceAtLeast(1),
            hour = input.hour,
            minute = input.minute,
            reminderDays = formatReminderDays(input.reminderDays),
            personName = input.personName?.trim()?.takeIf { it.isNotEmpty() },
            location = input.location?.trim()?.takeIf { it.isNotEmpty() },
            note = input.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            clanId = old?.clanId,
            remoteVersion = old?.remoteVersion ?: 0,
            syncState = old?.syncState ?: 0,
        )
        dao.upsert(entity)
        // Cập nhật lịch nhắc ngay khi sửa sự kiện
        scheduler.rescheduleAll()
        return SaveResult.Saved(entity.id)
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
        scheduler.rescheduleAll()
    }

    /** Ngày diễn ra của mọi sự kiện trong [from, to], sắp xếp theo ngày. Tính trên luồng nền. */
    suspend fun occurrences(events: List<FamilyEvent>, from: LocalDate, to: LocalDate): List<Occurrence> =
        withContext(Dispatchers.Default) { computeOccurrences(events, from, to) }

    companion object {
        fun computeOccurrences(events: List<FamilyEvent>, from: LocalDate, to: LocalDate): List<Occurrence> {
            if (events.isEmpty()) return emptyList()
            val span = if (events.any { it.isLunar }) LunarSpan(from, to) else null
            val out = ArrayList<Occurrence>()
            for (e in events) {
                val dates = if (span != null && e.isLunar) Recurrence.occurrencesIn(e.rule, span)
                else Recurrence.occurrencesBetween(e.rule, from, to)
                dates.forEach { out += Occurrence(e, it) }
            }
            out.sortWith(compareBy<Occurrence> { it.date }.thenBy { it.event.hour * 60 + it.event.minute })
            return out
        }

        fun ruleOf(input: EventInput) = RecurrenceRule(
            input.calendar, input.repeat, input.day, input.month, input.year, input.leapMonth, input.intervalDays,
        )
    }
}
