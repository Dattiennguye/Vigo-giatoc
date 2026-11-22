package vn.vigo.nepnha.lunar

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class CalendarKind { SOLAR, LUNAR }

enum class RepeatKind {
    /** Một lần. */
    NONE,

    /** Hằng năm (giỗ, sinh nhật, kỷ niệm). */
    YEARLY,

    /** Hằng tháng (mùng 1, rằm...). */
    MONTHLY,

    /** Tùy chỉnh: cứ mỗi N ngày (theo dương lịch). */
    EVERY_N_DAYS,
}

/**
 * Quy tắc lặp của một sự kiện.
 *
 * Với [CalendarKind.LUNAR]: [day]/[month]/[year] là ngày âm gốc, [leapMonth] cho biết tháng gốc là tháng nhuận.
 * Với [CalendarKind.SOLAR]: là ngày dương gốc.
 *
 * Quy ước phong tục được áp dụng (ghi rõ để người dùng biết):
 *  - Giỗ/lặp âm hằng năm: cúng vào tháng thường (không nhuận) cùng số tháng, kể cả khi ngày gốc rơi vào tháng nhuận.
 *  - Ngày gốc là 30 mà năm đó tháng chỉ có 29 ngày: lấy ngày cuối tháng (29).
 *  - Lặp dương ngày 29/2: năm không nhuận lấy 28/2. Lặp hằng tháng ngày 31: tháng ngắn lấy ngày cuối tháng.
 */
data class RecurrenceRule(
    val calendar: CalendarKind,
    val repeat: RepeatKind,
    val day: Int,
    val month: Int,
    val year: Int,
    val leapMonth: Boolean = false,
    val intervalDays: Int = 1,
) {
    /** Ngày dương tương ứng với ngày gốc; null nếu ngày âm gốc không hợp lệ. */
    fun startDate(): LocalDate? = when (calendar) {
        CalendarKind.SOLAR -> runCatching { LocalDate.of(year, month, day) }.getOrNull()
        CalendarKind.LUNAR -> VietLunarCalendar.lunarToSolar(LunarDate(day, month, year, leapMonth))
            ?: if (day == 30) VietLunarCalendar.lunarToSolar(LunarDate(29, month, year, leapMonth)) else null
    }
}

object Recurrence {

    private const val MAX_SPAN_DAYS = 3700L

    /** Các ngày diễn ra sự kiện trong đoạn [from, to] (bao gồm hai đầu), tăng dần. */
    fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate> =
        occurrencesBetween(rule, from, to, null)

    /** Như trên nhưng tái sử dụng bảng âm lịch đã tính sẵn [span] (nhanh hơn khi xét nhiều sự kiện cùng lúc). */
    fun occurrencesIn(rule: RecurrenceRule, span: LunarSpan): List<LocalDate> =
        occurrencesBetween(rule, span.from, span.to, span)

    private fun occurrencesBetween(
        rule: RecurrenceRule,
        from: LocalDate,
        to: LocalDate,
        span: LunarSpan?,
    ): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        require(ChronoUnit.DAYS.between(from, to) <= MAX_SPAN_DAYS) { "Khoảng thời gian quá dài" }
        val start = rule.startDate() ?: return emptyList()
        val lo = if (from.isBefore(start)) start else from
        if (to.isBefore(lo)) return emptyList()

        return when (rule.repeat) {
            RepeatKind.NONE -> if (!start.isBefore(from) && !start.isAfter(to)) listOf(start) else emptyList()
            RepeatKind.EVERY_N_DAYS -> {
                val n = rule.intervalDays.coerceAtLeast(1).toLong()
                val passed = ChronoUnit.DAYS.between(start, lo)
                var d = start.plusDays(((passed + n - 1) / n) * n)
                val out = ArrayList<LocalDate>()
                while (!d.isAfter(to)) {
                    out += d
                    d = d.plusDays(n)
                }
                out
            }
            RepeatKind.YEARLY, RepeatKind.MONTHLY -> when (rule.calendar) {
                CalendarKind.SOLAR -> solarRepeats(rule, lo, to)
                CalendarKind.LUNAR -> lunarRepeats(rule, lo, to, span)
            }
        }
    }

    fun nextOccurrence(rule: RecurrenceRule, onOrAfter: LocalDate): LocalDate? =
        occurrencesBetween(rule, onOrAfter, onOrAfter.plusDays(400)).firstOrNull()

    private fun solarRepeats(rule: RecurrenceRule, lo: LocalDate, to: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var ym = YearMonth.from(lo)
        val end = YearMonth.from(to)
        while (!ym.isAfter(end)) {
            if (rule.repeat == RepeatKind.MONTHLY || ym.monthValue == rule.month) {
                val d = ym.atDay(rule.day.coerceAtMost(ym.lengthOfMonth()))
                if (!d.isBefore(lo) && !d.isAfter(to)) out += d
            }
            ym = ym.plusMonths(1)
        }
        return out
    }

    private fun lunarRepeats(rule: RecurrenceRule, lo: LocalDate, to: LocalDate, span: LunarSpan?): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = lo
        var cur = span?.lunarOf(d) ?: VietLunarCalendar.solarToLunar(d)
        while (!d.isAfter(to)) {
            val next = d.plusDays(1)
            val nextLunar = span?.lunarOf(next) ?: VietLunarCalendar.solarToLunar(next)
            val isLastDayOfMonth = nextLunar.day == 1
            val monthMatches = when (rule.repeat) {
                RepeatKind.YEARLY -> cur.month == rule.month && !cur.isLeapMonth
                else -> true // hằng tháng: gồm cả tháng nhuận
            }
            val dayMatches = cur.day == rule.day || (rule.day >= 30 && cur.day == 29 && isLastDayOfMonth)
            if (monthMatches && dayMatches) out += d
            d = next
            cur = nextLunar
        }
        return out
    }
}

/**
 * Bảng âm lịch tính sẵn cho đoạn [from, to] (thêm 1 ngày đệm cuối), dùng chung cho nhiều sự kiện.
 * Bất biến, an toàn đa luồng.
 */
class LunarSpan(val from: LocalDate, val to: LocalDate) {
    private val startEpoch = from.toEpochDay()
    private val table: Array<LunarDate>

    init {
        require(!to.isBefore(from))
        val n = (to.toEpochDay() - startEpoch + 2).toInt()
        require(n <= 3702) { "Khoảng thời gian quá dài" }
        table = Array(n) { VietLunarCalendar.solarToLunar(from.plusDays(it.toLong())) }
    }

    /** Âm lịch của [date]; null nếu nằm ngoài bảng. */
    fun lunarOf(date: LocalDate): LunarDate? {
        val i = (date.toEpochDay() - startEpoch).toInt()
        return if (i in table.indices) table[i] else null
    }
}
