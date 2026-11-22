package vn.vigo.nepnha.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.vigo.nepnha.lunar.LunarDate
import vn.vigo.nepnha.lunar.VietLunarCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Tạo ViewModel với hàm khởi tạo tùy ý (DI thủ công). */
@Composable
inline fun <reified VM : ViewModel> nepViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        },
    )

object VnFormat {
    fun weekday(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "Thứ Hai"
        DayOfWeek.TUESDAY -> "Thứ Ba"
        DayOfWeek.WEDNESDAY -> "Thứ Tư"
        DayOfWeek.THURSDAY -> "Thứ Năm"
        DayOfWeek.FRIDAY -> "Thứ Sáu"
        DayOfWeek.SATURDAY -> "Thứ Bảy"
        DayOfWeek.SUNDAY -> "Chủ Nhật"
    }

    fun weekdayShort(d: DayOfWeek): String = when (d) {
        DayOfWeek.SUNDAY -> "CN"
        else -> "T${d.value + 1}"
    }

    fun solar(d: LocalDate): String = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)

    fun solarLong(d: LocalDate): String = "${weekday(d.dayOfWeek)}, ${solar(d)}"

    fun lunarMonthName(m: Int): String = when (m) {
        1 -> "Giêng"
        11 -> "Mười Một"
        12 -> "Chạp"
        else -> m.toString()
    }

    fun lunar(l: LunarDate): String = "${l.day}/${l.month}${if (l.isLeapMonth) " nhuận" else ""}"

    fun lunarOf(d: LocalDate): String = lunar(VietLunarCalendar.solarToLunar(d))

    /** "Hôm nay", "Ngày mai", "Còn 5 ngày". */
    fun relative(today: LocalDate, d: LocalDate): String {
        val n = ChronoUnit.DAYS.between(today, d)
        return when {
            n == 0L -> "Hôm nay"
            n == 1L -> "Ngày mai"
            n > 1 -> "Còn $n ngày"
            n == -1L -> "Hôm qua"
            else -> "${-n} ngày trước"
        }
    }
}
