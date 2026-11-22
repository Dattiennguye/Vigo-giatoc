package vn.vigo.nepnha.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.data.repo.EventRepository
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.domain.Occurrence
import vn.vigo.nepnha.lunar.CanChi
import vn.vigo.nepnha.lunar.Holiday
import vn.vigo.nepnha.lunar.Holidays
import vn.vigo.nepnha.lunar.LunarDate
import vn.vigo.nepnha.lunar.VietLunarCalendar
import java.time.LocalDate

@Immutable
data class TodayInfo(
    val date: LocalDate,
    val lunar: LunarDate,
    val yearCanChi: String,
    val monthCanChi: String,
    val dayCanChi: String,
    val solarTerm: String,
)

sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String) : HomeState

    @Immutable
    data class Ready(
        val today: TodayInfo,
        val deathAnniversaries: List<Occurrence>,
        val birthdays: List<Occurrence>,
        val others: List<Occurrence>,
        val holidays: List<Holiday>,
        val openTasks: Int,
        val totalEvents: Int,
    ) : HomeState
}

class HomeViewModel(container: AppContainer) : ViewModel() {

    /** Phát ngày hiện tại, tự đổi khi qua nửa đêm. */
    private val todayFlow = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<HomeState> = combine(
        container.events.events,
        container.tasks.openCount,
        container.settings.settings.map { it.showHolidays }.distinctUntilChanged(),
        todayFlow,
    ) { events, openTasks, showHolidays, today ->
        val l = VietLunarCalendar.solarToLunar(today)
        val info = TodayInfo(
            date = today,
            lunar = l,
            yearCanChi = CanChi.year(l.year),
            monthCanChi = CanChi.month(l.month, l.year),
            dayCanChi = CanChi.day(today),
            solarTerm = CanChi.solarTerm(today),
        )
        val occ = EventRepository.computeOccurrences(events, today, today.plusDays(365))
        // Mỗi sự kiện chỉ hiện lần gần nhất
        val nearest = occ.distinctBy { it.event.id }
        HomeState.Ready(
            today = info,
            deathAnniversaries = nearest.filter { it.event.type == EventType.DEATH_ANNIVERSARY }.take(5),
            birthdays = nearest.filter { it.event.type == EventType.BIRTHDAY }.take(5),
            others = nearest.filter {
                it.event.type != EventType.DEATH_ANNIVERSARY && it.event.type != EventType.BIRTHDAY
            }.take(5),
            holidays = if (showHolidays) Holidays.between(today, today.plusDays(60)).take(4) else emptyList(),
            openTasks = openTasks,
            totalEvents = events.size,
        ) as HomeState
    }
        .flowOn(Dispatchers.Default)
        .catch { emit(HomeState.Error(it.message ?: "Không đọc được dữ liệu")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)
}
