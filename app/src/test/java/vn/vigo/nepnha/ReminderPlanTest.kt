package vn.vigo.nepnha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.domain.FamilyEvent
import vn.vigo.nepnha.domain.formatReminderDays
import vn.vigo.nepnha.domain.parseReminderDays
import vn.vigo.nepnha.lunar.CalendarKind
import vn.vigo.nepnha.lunar.RecurrenceRule
import vn.vigo.nepnha.lunar.RepeatKind
import vn.vigo.nepnha.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId

class ReminderPlanTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun gio(reminders: List<Int>) = FamilyEvent(
        id = "e1",
        title = "Giỗ ông nội",
        type = EventType.DEATH_ANNIVERSARY,
        rule = RecurrenceRule(CalendarKind.LUNAR, RepeatKind.YEARLY, 15, 7, 1990),
        hour = 8,
        minute = 0,
        reminderDays = reminders,
        personName = "Ông nội",
        location = null,
        note = null,
    )

    @Test
    fun multipleRemindersForLunarAnniversary() {
        val today = LocalDate.of(2025, 8, 1)
        val now = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val plan = ReminderScheduler.plan(listOf(gio(listOf(30, 7, 1, 0))), today, now, zone)
        // Giỗ 15/7 âm năm 2025 = 06/09/2025
        val keys = plan.map { it.key }
        assertTrue(keys.contains("e1|2025-09-06|30"))
        assertTrue(keys.contains("e1|2025-09-06|7"))
        assertTrue(keys.contains("e1|2025-09-06|1"))
        assertTrue(keys.contains("e1|2025-09-06|0"))
        // Không trùng khóa, sắp theo thời gian
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(plan.sortedBy { it.triggerAt }, plan)
        val dayOf = plan.first { it.key == "e1|2025-09-06|0" }
        assertEquals(
            LocalDate.of(2025, 9, 6).atTime(8, 0).atZone(zone).toInstant().toEpochMilli(),
            dayOf.triggerAt,
        )
        assertTrue(dayOf.title.startsWith("Hôm nay"))
    }

    @Test
    fun pastRemindersAreSkipped() {
        val today = LocalDate.of(2025, 9, 3)
        val now = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val plan = ReminderScheduler.plan(listOf(gio(listOf(30, 7, 1, 0))), today, now, zone)
        val thisYear = plan.filter { it.key.contains("2025-09-06") }.map { it.key.substringAfterLast('|') }
        assertEquals(listOf("1", "0"), thisYear)
    }

    @Test
    fun noRemindersNoPlan() {
        val today = LocalDate.of(2025, 8, 1)
        val now = today.atStartOfDay(zone).toInstant().toEpochMilli()
        assertTrue(ReminderScheduler.plan(listOf(gio(emptyList())), today, now, zone).isEmpty())
    }

    @Test
    fun reminderDaysRoundTrip() {
        assertEquals(listOf(30, 7, 1, 0), parseReminderDays(formatReminderDays(listOf(0, 7, 30, 1, 7))))
        assertEquals(emptyList<Int>(), parseReminderDays(""))
    }
}
