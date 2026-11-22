package vn.vigo.nepnha.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VietLunarCalendarTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    /** Các mốc đối chiếu thủ công với lịch Việt Nam đã công bố. */
    @Test
    fun knownVietnameseDates() {
        val cases = mapOf(
            d(2025, 1, 29) to LunarDate(1, 1, 2025),
            d(2024, 2, 10) to LunarDate(1, 1, 2024),
            d(2026, 2, 17) to LunarDate(1, 1, 2026),
            d(2023, 1, 22) to LunarDate(1, 1, 2023),
            d(2020, 1, 25) to LunarDate(1, 1, 2020),
            d(2027, 2, 6) to LunarDate(1, 1, 2027),
            // Tháng nhuận
            d(2020, 5, 23) to LunarDate(1, 4, 2020, true),
            d(2023, 3, 22) to LunarDate(1, 2, 2023, true),
            d(2025, 7, 25) to LunarDate(1, 6, 2025, true),
            d(2023, 2, 20) to LunarDate(1, 2, 2023),
            // Năm lịch Việt khác lịch Trung Quốc
            d(1985, 1, 21) to LunarDate(1, 1, 1985),
            d(2007, 2, 17) to LunarDate(1, 1, 2007),
            // Sóc sát nửa đêm: 08/05/2054 là mùng 1, ngày trước đó là 30 tháng trước
            d(2054, 5, 7) to LunarDate(30, 3, 2054),
            // 1/9/1945 = 25/7 Ất Dậu
            d(1945, 9, 1) to LunarDate(25, 7, 1945),
        )
        for ((solar, lunar) in cases) {
            assertEquals("Âm của $solar", lunar, VietLunarCalendar.solarToLunar(solar))
            assertEquals("Dương của $lunar", solar, VietLunarCalendar.lunarToSolar(lunar))
        }
    }

    /** Hồi quy với bộ dữ liệu 2.486 đầu tháng âm 1900–2100 sinh từ bản tham chiếu độc lập. */
    @Test
    fun monthStartsMatchReference() {
        val rows = javaClass.getResource("/lunar_month_starts_1900_2100.csv")!!.readText()
            .lines().drop(1).filter { it.isNotBlank() }
        assertTrue(rows.size > 2400)
        var prev: Pair<LocalDate, LunarDate>? = null
        for (row in rows) {
            val p = row.split(",")
            val solar = LocalDate.parse(p[0])
            val lunar = LunarDate(p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4] == "1")
            assertEquals(row, lunar, VietLunarCalendar.solarToLunar(solar))
            assertEquals(row, solar, VietLunarCalendar.lunarToSolar(lunar))
            prev?.let { (ps, pl) ->
                val len = (solar.toEpochDay() - ps.toEpochDay()).toInt()
                assertTrue("Độ dài tháng $pl = $len", len == 29 || len == 30)
                assertEquals(len, VietLunarCalendar.daysInLunarMonth(pl.month, pl.year, pl.isLeapMonth))
            }
            prev = solar to lunar
        }
    }

    @Test
    fun roundTripEveryDay1900to2100() {
        var day = d(1900, 3, 1)
        val end = d(2100, 12, 31)
        val failures = ArrayList<String>()
        while (!day.isAfter(end)) {
            val lunar = VietLunarCalendar.solarToLunar(day)
            val back = VietLunarCalendar.lunarToSolar(lunar)
            if (back != day || lunar.day !in 1..30) failures += "$day -> $lunar -> $back"
            day = day.plusDays(1)
        }
        assertTrue("${failures.size} ngày sai, ví dụ: ${failures.take(8)}", failures.isEmpty())
    }

    @Test
    fun invalidLunarDatesReturnNull() {
        // 2024 không có tháng nhuận
        assertNull(VietLunarCalendar.lunarToSolar(LunarDate(1, 4, 2024, true)))
        // 2023 nhuận tháng 2, không nhuận tháng 3
        assertNull(VietLunarCalendar.lunarToSolar(LunarDate(1, 3, 2023, true)))
        assertEquals(2, VietLunarCalendar.leapMonthOf(2023))
        assertEquals(6, VietLunarCalendar.leapMonthOf(2025))
        assertEquals(4, VietLunarCalendar.leapMonthOf(2020))
        assertNull(VietLunarCalendar.leapMonthOf(2024))
        // Ngày 30 ở tháng thiếu
        val m = (1..12).first { VietLunarCalendar.daysInLunarMonth(it, 2024) == 29 }
        assertNull(VietLunarCalendar.lunarToSolar(LunarDate(30, m, 2024)))
    }

    @Test
    fun canChi() {
        assertEquals("Giáp Thìn", CanChi.year(2024))
        assertEquals("Ất Tỵ", CanChi.year(2025))
        assertEquals("Bính Ngọ", CanChi.year(2026))
        assertEquals("Bính Dần", CanChi.month(1, 2024))
        assertEquals("Giáp Thìn", CanChi.day(d(2024, 2, 10)))
        assertEquals("Mèo", CanChi.zodiacOfYear(2023))
    }
}
