package vn.vigo.nepnha.ui.calendar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.data.repo.EventRepository
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.domain.Occurrence
import vn.vigo.nepnha.lunar.Holiday
import vn.vigo.nepnha.lunar.Holidays
import vn.vigo.nepnha.lunar.LunarDate
import vn.vigo.nepnha.lunar.LunarSpan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarMode(val label: String) { DAY("Ngày"), MONTH("Tháng"), YEAR("Năm") }

@Immutable
data class DayCell(
    val date: LocalDate,
    val lunar: LunarDate,
    val inMonth: Boolean,
    val eventTypes: List<EventType>,
    val isHoliday: Boolean,
)

@Immutable
data class CalendarUi(
    val mode: CalendarMode = CalendarMode.MONTH,
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val filters: Set<EventType> = emptySet(),
    val cells: List<DayCell> = emptyList(),
    val selectedOccurrences: List<Occurrence> = emptyList(),
    val selectedHolidays: List<Holiday> = emptyList(),
    val yearSummary: List<Pair<YearMonth, List<Occurrence>>> = emptyList(),
    val ready: Boolean = false,
)

private data class Input(val mode: CalendarMode, val month: YearMonth, val selected: LocalDate, val filters: Set<EventType>)

class CalendarViewModel(container: AppContainer) : ViewModel() {

    private val input = MutableStateFlow(Input(CalendarMode.MONTH, YearMonth.now(), LocalDate.now(), emptySet()))

    // Bảng âm lịch của tháng đang xem được giữ lại để lướt qua lại không phải tính lại.
    private val spanCache = object : LinkedHashMap<YearMonth, LunarSpan>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<YearMonth, LunarSpan>?) = size > 6
    }

    private fun spanFor(m: YearMonth): LunarSpan = synchronized(spanCache) {
        spanCache.getOrPut(m) {
            val first = m.atDay(1)
            val start = first.minusDays(((first.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7L) % 7)
            LunarSpan(start, start.plusDays(41))
        }
    }

    val ui: StateFlow<CalendarUi> = combine(input, container.events.events, container.settings.settings) { inp, events, settings ->
        val filtered = if (inp.filters.isEmpty()) events else events.filter { it.type in inp.filters }
        val today = LocalDate.now()
        val span = spanFor(inp.month)
        val occ = EventRepository.computeOccurrences(filtered, span.from, span.to)
        val byDate = occ.groupBy { it.date }
        val holidays = if (settings.showHolidays) Holidays.between(span.from, span.to) else emptyList()
        val holidayDates = holidays.map { it.date }.toHashSet()
        val cells = (0 until 42).map { i ->
            val d = span.from.plusDays(i.toLong())
            DayCell(
                date = d,
                lunar = span.lunarOf(d)!!,
                inMonth = YearMonth.from(d) == inp.month,
                eventTypes = byDate[d].orEmpty().map { it.event.type }.distinct().take(3),
                isHoliday = d in holidayDates,
            )
        }
        val sel = inp.selected
        val selOcc = byDate[sel] ?: EventRepository.computeOccurrences(filtered, sel, sel)
        val selHol = holidays.filter { it.date == sel }.ifEmpty {
            if (settings.showHolidays && sel !in span.from..span.to) Holidays.between(sel, sel) else emptyList()
        }
        val yearSummary = if (inp.mode == CalendarMode.YEAR) {
            val y = inp.month.year
            val all = EventRepository.computeOccurrences(filtered, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))
            (1..12).map { m -> YearMonth.of(y, m) to all.filter { it.date.monthValue == m } }
        } else emptyList()
        CalendarUi(inp.mode, inp.month, sel, today, inp.filters, cells, selOcc, selHol, yearSummary, ready = true)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUi())

    fun setMode(m: CalendarMode) = input.update { it.copy(mode = m) }

    fun shiftMonth(delta: Long) = input.update {
        val m = it.month.plusMonths(delta)
        it.copy(month = m, selected = if (YearMonth.from(it.selected) == m) it.selected else m.atDay(1))
    }

    fun shiftYear(delta: Long) = input.update { it.copy(month = it.month.plusYears(delta)) }

    fun shiftDay(delta: Long) = input.update {
        val d = it.selected.plusDays(delta)
        it.copy(selected = d, month = YearMonth.from(d))
    }

    fun select(d: LocalDate) = input.update { it.copy(selected = d, month = YearMonth.from(d)) }

    fun goToday() = select(LocalDate.now())

    fun openMonth(m: YearMonth) = input.update { it.copy(mode = CalendarMode.MONTH, month = m, selected = m.atDay(1)) }

    fun toggleFilter(t: EventType) = input.update {
        it.copy(filters = if (t in it.filters) it.filters - t else it.filters + t)
    }

    fun clearFilters() = input.update { it.copy(filters = emptySet()) }
}
