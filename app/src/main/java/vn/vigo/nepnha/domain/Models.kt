package vn.vigo.nepnha.domain

import androidx.compose.runtime.Immutable
import vn.vigo.nepnha.data.db.EventEntity
import vn.vigo.nepnha.lunar.CalendarKind
import vn.vigo.nepnha.lunar.RecurrenceRule
import vn.vigo.nepnha.lunar.RepeatKind
import java.time.LocalDate

enum class EventType(val label: String, val symbol: String, val colorHex: Long) {
    DEATH_ANNIVERSARY("Ngày giỗ", "Giỗ", 0xFF624735),
    BIRTHDAY("Sinh nhật", "SN", 0xFF8C3438),
    ANNIVERSARY("Kỷ niệm", "KN", 0xFF9A6B2F),
    HOLIDAY("Lễ Tết", "Lễ", 0xFFB0452E),
    WEDDING("Cưới hỏi", "Cưới", 0xFFA8326E),
    GATHERING("Họp mặt", "Họp", 0xFF2F5D62),
    OTHER("Công việc khác", "Việc", 0xFF5C534C),
    ;

    companion object {
        fun of(name: String): EventType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Các mốc nhắc trước có sẵn (ngày). 0 = trong ngày. */
val PRESET_REMINDER_DAYS = listOf(30, 14, 7, 3, 1, 0)

fun reminderLabel(days: Int): String = when (days) {
    0 -> "Trong ngày"
    else -> "Trước $days ngày"
}

fun parseReminderDays(s: String): List<Int> =
    s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..365 }.distinct().sortedDescending()

fun formatReminderDays(days: Collection<Int>): String = days.distinct().sortedDescending().joinToString(",")

@Immutable
data class FamilyEvent(
    val id: String,
    val title: String,
    val type: EventType,
    val rule: RecurrenceRule,
    val hour: Int,
    val minute: Int,
    val reminderDays: List<Int>,
    val personName: String?,
    val location: String?,
    val note: String?,
) {
    val isLunar: Boolean get() = rule.calendar == CalendarKind.LUNAR
}

fun EventEntity.toDomain(): FamilyEvent = FamilyEvent(
    id = id,
    title = title,
    type = EventType.of(type),
    rule = RecurrenceRule(
        calendar = runCatching { CalendarKind.valueOf(calendarKind) }.getOrDefault(CalendarKind.SOLAR),
        repeat = runCatching { RepeatKind.valueOf(repeat) }.getOrDefault(RepeatKind.NONE),
        day = day, month = month, year = year, leapMonth = leapMonth, intervalDays = intervalDays,
    ),
    hour = hour,
    minute = minute,
    reminderDays = parseReminderDays(reminderDays),
    personName = personName,
    location = location,
    note = note,
)

/** Một lần diễn ra cụ thể của sự kiện. */
@Immutable
data class Occurrence(val event: FamilyEvent, val date: LocalDate)
