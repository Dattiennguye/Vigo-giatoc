package vn.vigo.nepnha.lunar

import java.time.LocalDate

data class Holiday(val date: LocalDate, val name: String, val isLunar: Boolean)

/** Ngày lễ, Tết phổ biến của Việt Nam. */
object Holidays {

    private val SOLAR = listOf(
        Triple(1, 1, "Tết Dương lịch"),
        Triple(3, 2, "Thành lập Đảng Cộng sản Việt Nam"),
        Triple(14, 2, "Lễ Tình nhân"),
        Triple(8, 3, "Quốc tế Phụ nữ"),
        Triple(30, 4, "Giải phóng miền Nam"),
        Triple(1, 5, "Quốc tế Lao động"),
        Triple(19, 5, "Ngày sinh Chủ tịch Hồ Chí Minh"),
        Triple(1, 6, "Quốc tế Thiếu nhi"),
        Triple(28, 6, "Ngày Gia đình Việt Nam"),
        Triple(27, 7, "Thương binh Liệt sĩ"),
        Triple(2, 9, "Quốc khánh"),
        Triple(20, 10, "Phụ nữ Việt Nam"),
        Triple(20, 11, "Nhà giáo Việt Nam"),
        Triple(22, 12, "Quân đội Nhân dân Việt Nam"),
        Triple(24, 12, "Lễ Giáng sinh"),
    )

    private val LUNAR = listOf(
        Triple(1, 1, "Tết Nguyên Đán"),
        Triple(2, 1, "Mùng 2 Tết"),
        Triple(3, 1, "Mùng 3 Tết"),
        Triple(15, 1, "Rằm tháng Giêng"),
        Triple(3, 3, "Tết Hàn thực"),
        Triple(10, 3, "Giỗ Tổ Hùng Vương"),
        Triple(15, 4, "Lễ Phật đản"),
        Triple(5, 5, "Tết Đoan ngọ"),
        Triple(15, 7, "Lễ Vu Lan"),
        Triple(15, 8, "Tết Trung thu"),
        Triple(23, 12, "Ông Công ông Táo"),
    )

    /** Ngày lễ trong khoảng [from, to]. */
    fun between(from: LocalDate, to: LocalDate): List<Holiday> {
        val out = ArrayList<Holiday>()
        for ((d, m, name) in SOLAR) {
            val rule = RecurrenceRule(CalendarKind.SOLAR, RepeatKind.YEARLY, d, m, 1900)
            Recurrence.occurrencesBetween(rule, from, to).forEach { out += Holiday(it, name, false) }
        }
        // Âm lịch: duyệt từng ngày một lần cho mọi ngày lễ.
        var day = from
        var lunar = VietLunarCalendar.solarToLunar(day)
        while (!day.isAfter(to)) {
            val next = day.plusDays(1)
            val nextLunar = VietLunarCalendar.solarToLunar(next)
            if (!lunar.isLeapMonth) {
                LUNAR.firstOrNull { it.first == lunar.day && it.second == lunar.month }
                    ?.let { out += Holiday(day, it.third, true) }
                if (lunar.month == 12 && nextLunar.day == 1 && nextLunar.month == 1) {
                    out += Holiday(day, "Giao thừa", true)
                }
            }
            day = next
            lunar = nextLunar
        }
        return out.sortedBy { it.date }
    }
}
