package vn.vigo.nepnha.lunar

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Ngày âm lịch Việt Nam.
 * @property isLeapMonth true nếu là tháng nhuận.
 */
data class LunarDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val isLeapMonth: Boolean = false,
) {
    override fun toString(): String =
        "$day/$month${if (isLeapMonth) " nhuận" else ""}/$year"
}

/**
 * Lịch âm Việt Nam theo thuật toán thiên văn của Hồ Ngọc Đức, tính theo múi giờ UTC+7.
 *
 * Không dùng lịch âm Trung Quốc (UTC+8): hai lịch lệch nhau ở một số năm
 * (ví dụ Tết 1985: Việt Nam 21/01, Trung Quốc 20/02; Tết 2007: VN 17/02, TQ 18/02).
 *
 * Mọi hàm là thuần tính toán, không phụ thuộc Android, an toàn đa luồng.
 */
object VietLunarCalendar {

    /** Múi giờ Việt Nam. */
    const val VIETNAM_TIME_ZONE: Double = 7.0

    private const val MIN_YEAR = 1800
    private const val MAX_YEAR = 2199

    // ---------------- Chuyển đổi số ngày Julius ----------------

    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    fun jdToDate(jd: Int): LocalDate {
        val b: Int
        val c: Int
        if (jd > 2299160) {
            val a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - (b * 146097) / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = b * 100 + d - 4800 + m / 10
        return LocalDate.of(year, month, day)
    }

    // ---------------- Thiên văn ----------------

    private fun int(x: Double): Int = floor(x).toInt()

    /** Thời điểm sóc (trăng mới) thứ k tính từ 1/1/1900, theo ngày Julius (UTC). */
    internal fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        // Lưu ý dấu: giữ nguyên đúng thứ tự cộng/trừ của công thức gốc.
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 = c1 - 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd1 + c1 - deltaT
    }

    private fun newMoonDay(k: Int, tz: Double): Int = int(newMoon(k) + 0.5 + tz / 24)

    /** Kinh độ mặt trời (radian) tại ngày Julius. */
    private fun sunLongitudeRad(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525
        val t2 = t * t
        val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * int(l / (PI * 2))
        return l
    }

    /** Cung hoàng đạo (0..11, mỗi cung 30°) của mặt trời lúc 0h ngày [dayNumber]. */
    private fun sunSector(dayNumber: Int, tz: Double): Int =
        int(sunLongitudeRad(dayNumber - 0.5 - tz / 24) / PI * 6)

    /** Chỉ số tiết khí (0..23, mỗi tiết 15°) của ngày, 0 = Xuân phân. */
    fun solarTermIndex(date: LocalDate, tz: Double = VIETNAM_TIME_ZONE): Int {
        val jd = jdFromDate(date.dayOfMonth, date.monthValue, date.year)
        return int(sunLongitudeRad(jd + 0.5 - tz / 24) / PI * 12)
    }

    private fun lunarMonth11(yy: Int, tz: Double): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = int(off / 29.530588853)
        var nm = newMoonDay(k, tz)
        if (sunSector(nm, tz) >= 9) nm = newMoonDay(k - 1, tz)
        return nm
    }

    private fun leapMonthOffset(a11: Int, tz: Double): Int {
        val k = int((a11 - 2415021.076998695) / 29.530588853 + 0.5)
        var i = 1
        var arc = sunSector(newMoonDay(k + i, tz), tz)
        var last: Int
        do {
            last = arc
            i++
            arc = sunSector(newMoonDay(k + i, tz), tz)
        } while (arc != last && i < 14)
        return i - 1
    }

    // ---------------- API công khai ----------------

    private fun checkYear(year: Int) {
        require(year in MIN_YEAR..MAX_YEAR) { "Năm $year nằm ngoài phạm vi hỗ trợ $MIN_YEAR–$MAX_YEAR" }
    }

    fun solarToLunar(date: LocalDate, tz: Double = VIETNAM_TIME_ZONE): LunarDate {
        val yy = date.year
        checkYear(yy)
        val dayNumber = jdFromDate(date.dayOfMonth, date.monthValue, yy)
        val k = int((dayNumber - 2415021.076998695) / 29.530588853)
        var monthStart = newMoonDay(k + 1, tz)
        if (monthStart > dayNumber) monthStart = newMoonDay(k, tz)
        // Sóc rơi sát nửa đêm có thể làm k lệch một tháng (ví dụ 07/05/2054): lùi thêm một kỳ.
        if (monthStart > dayNumber) monthStart = newMoonDay(k - 1, tz)
        var a11 = lunarMonth11(yy, tz)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = lunarMonth11(yy - 1, tz)
        } else {
            lunarYear = yy + 1
            b11 = lunarMonth11(yy + 1, tz)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = int((monthStart - a11) / 29.0)
        var leap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapDiff = leapMonthOffset(a11, tz)
            if (diff >= leapDiff) {
                lunarMonth = diff + 10
                if (diff == leapDiff) leap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return LunarDate(lunarDay, lunarMonth, lunarYear, leap)
    }

    /**
     * Đổi âm sang dương. Trả về null nếu ngày âm không tồn tại
     * (tháng nhuận không có trong năm đó, hoặc ngày 30 ở tháng thiếu).
     */
    fun lunarToSolar(lunar: LunarDate, tz: Double = VIETNAM_TIME_ZONE): LocalDate? {
        val (lunarDay, lunarMonth, lunarYear, leap) = lunar
        if (lunarMonth !in 1..12 || lunarDay !in 1..30) return null
        checkYear(lunarYear)
        val a11: Int
        val b11: Int
        if (lunarMonth < 11) {
            a11 = lunarMonth11(lunarYear - 1, tz)
            b11 = lunarMonth11(lunarYear, tz)
        } else {
            a11 = lunarMonth11(lunarYear, tz)
            b11 = lunarMonth11(lunarYear + 1, tz)
        }
        var off = lunarMonth - 11
        if (off < 0) off += 12
        if (b11 - a11 > 365) {
            val leapOff = leapMonthOffset(a11, tz)
            var leapMonth = leapOff - 2
            if (leapMonth < 0) leapMonth += 12
            if (leap && lunarMonth != leapMonth) return null
            if (leap || off >= leapOff) off += 1
        } else if (leap) {
            return null
        }
        val k = int(0.5 + (a11 - 2415021.076998695) / 29.530588853)
        val monthStart = newMoonDay(k + off, tz)
        val nextMonthStart = newMoonDay(k + off + 1, tz)
        if (lunarDay > nextMonthStart - monthStart) return null
        return jdToDate(monthStart + lunarDay - 1)
    }

    /** Số ngày của tháng âm (29 hoặc 30); null nếu tháng không tồn tại. */
    fun daysInLunarMonth(month: Int, year: Int, leap: Boolean = false): Int? {
        lunarToSolar(LunarDate(1, month, year, leap)) ?: return null
        return if (lunarToSolar(LunarDate(30, month, year, leap)) != null) 30 else 29
    }

    /** Tháng nhuận của năm âm [year] (1..12), hoặc null nếu năm không nhuận. */
    fun leapMonthOf(year: Int): Int? {
        for (m in 1..12) {
            if (lunarToSolar(LunarDate(1, m, year, true)) != null) return m
        }
        return null
    }
}
