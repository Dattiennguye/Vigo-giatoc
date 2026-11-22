package vn.vigo.nepnha.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun lunarYearlyAnniversary() {
        val gio = RecurrenceRule(CalendarKind.LUNAR, RepeatKind.YEARLY, 15, 7, 2010)
        assertEquals(listOf(d(2025, 9, 6)), Recurrence.occurrencesBetween(gio, d(2025, 1, 1), d(2025, 12, 31)))
        assertEquals(d(2026, 8, 27), Recurrence.nextOccurrence(gio, d(2025, 9, 7)))
    }

    @Test
    fun day30FallsBackToLastDayOfShortMonth() {
        val gio = RecurrenceRule(CalendarKind.LUNAR, RepeatKind.YEARLY, 30, 12, 2000)
        // Tháng Chạp năm Giáp Thìn (2024) chỉ có 29 ngày -> cúng 29 Tết = 28/01/2025
        assertEquals(listOf(d(2025, 1, 28)), Recurrence.occurrencesBetween(gio, d(2024, 6, 1), d(2025, 6, 1)))
        // Tháng Chạp năm Quý Mão (2023) đủ 30 ngày -> 09/02/2024
        assertEquals(listOf(d(2024, 2, 9)), Recurrence.occurrencesBetween(gio, d(2023, 6, 1), d(2024, 6, 1)))
    }

    @Test
    fun leapMonthOriginIsObservedInRegularMonth() {
        val gio = RecurrenceRule(CalendarKind.LUNAR, RepeatKind.YEARLY, 10, 4, 2020, leapMonth = true)
        assertEquals(d(2020, 6, 1), gio.startDate())
        assertEquals(listOf(d(2021, 5, 21)), Recurrence.occurrencesBetween(gio, d(2021, 1, 1), d(2021, 12, 31)))
        assertEquals(listOf(d(2023, 5, 28)), Recurrence.occurrencesBetween(gio, d(2023, 1, 1), d(2023, 12, 31)))
        // Năm 2020: chỉ tính từ ngày gốc trở đi, tháng 4 thường (trước ngày gốc) bị bỏ qua.
        assertEquals(emptyList<LocalDate>(), Recurrence.occurrencesBetween(gio, d(2020, 1, 1), d(2020, 12, 31)))
    }

    @Test
    fun lunarMonthlyIncludesLeapMonth() {
        val mung1 = RecurrenceRule(CalendarKind.LUNAR, RepeatKind.MONTHLY, 1, 1, 2025)
        assertEquals(
            listOf(d(2025, 5, 27), d(2025, 6, 25), d(2025, 7, 25), d(2025, 8, 23)),
            Recurrence.occurrencesBetween(mung1, d(2025, 5, 1), d(2025, 8, 31)),
        )
    }

    @Test
    fun solarRules() {
        val leapBirthday = RecurrenceRule(CalendarKind.SOLAR, RepeatKind.YEARLY, 29, 2, 2000)
        assertEquals(listOf(d(2025, 2, 28)), Recurrence.occurrencesBetween(leapBirthday, d(2025, 1, 1), d(2025, 12, 31)))
        assertEquals(listOf(d(2028, 2, 29)), Recurrence.occurrencesBetween(leapBirthday, d(2028, 1, 1), d(2028, 12, 31)))

        val monthly31 = RecurrenceRule(CalendarKind.SOLAR, RepeatKind.MONTHLY, 31, 1, 2025)
        assertEquals(
            listOf(d(2025, 1, 31), d(2025, 2, 28), d(2025, 3, 31), d(2025, 4, 30)),
            Recurrence.occurrencesBetween(monthly31, d(2025, 1, 1), d(2025, 4, 30)),
        )

        val every10 = RecurrenceRule(CalendarKind.SOLAR, RepeatKind.EVERY_N_DAYS, 1, 1, 2025, intervalDays = 10)
        assertEquals(
            listOf(d(2025, 1, 21), d(2025, 1, 31)),
            Recurrence.occurrencesBetween(every10, d(2025, 1, 15), d(2025, 2, 5)),
        )

        val once = RecurrenceRule(CalendarKind.SOLAR, RepeatKind.NONE, 5, 3, 2025)
        assertTrue(Recurrence.occurrencesBetween(once, d(2025, 3, 6), d(2026, 1, 1)).isEmpty())
    }

    @Test
    fun manyYearsInARowAlwaysOnePerYear() {
        val gio = RecurrenceRule(CalendarKind.LUNAR, RepeatKind.YEARLY, 30, 11, 1990)
        for (y in 2000..2060) {
            val occ = Recurrence.occurrencesBetween(gio, d(y, 1, 1), d(y, 12, 31))
            assertTrue("Năm $y: $occ", occ.size <= 2)
        }
        // Liên tiếp 40 năm, mỗi năm âm đúng một lần.
        var from = d(2000, 1, 1)
        repeat(40) {
            val next = Recurrence.nextOccurrence(gio, from)!!
            val l = VietLunarCalendar.solarToLunar(next)
            assertEquals(11, l.month)
            assertTrue(l.day == 30 || l.day == 29)
            from = next.plusDays(1)
        }
    }

    @Test
    fun holidays() {
        val h = Holidays.between(d(2025, 1, 1), d(2025, 12, 31))
        assertTrue(h.any { it.name == "Tết Nguyên Đán" && it.date == d(2025, 1, 29) })
        assertTrue(h.any { it.name == "Giao thừa" && it.date == d(2025, 1, 28) })
        assertTrue(h.any { it.name == "Quốc khánh" && it.date == d(2025, 9, 2) })
    }

    @Test
    fun spanGivesSameResultAsDirect() {
        val span = LunarSpan(d(2024, 1, 1), d(2026, 12, 31))
        val rules = listOf(
            RecurrenceRule(CalendarKind.LUNAR, RepeatKind.YEARLY, 30, 12, 2000),
            RecurrenceRule(CalendarKind.LUNAR, RepeatKind.MONTHLY, 15, 1, 2020),
            RecurrenceRule(CalendarKind.LUNAR, RepeatKind.YEARLY, 10, 4, 2020, leapMonth = true),
        )
        for (r in rules) {
            assertEquals(Recurrence.occurrencesBetween(r, span.from, span.to), Recurrence.occurrencesIn(r, span))
        }
    }
}
