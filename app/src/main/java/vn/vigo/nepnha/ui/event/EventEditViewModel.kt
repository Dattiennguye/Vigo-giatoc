package vn.vigo.nepnha.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.data.repo.EventInput
import vn.vigo.nepnha.data.repo.EventRepository
import vn.vigo.nepnha.data.repo.SaveResult
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.domain.parseReminderDays
import vn.vigo.nepnha.lunar.CalendarKind
import vn.vigo.nepnha.lunar.LunarDate
import vn.vigo.nepnha.lunar.Recurrence
import vn.vigo.nepnha.lunar.RepeatKind
import vn.vigo.nepnha.lunar.VietLunarCalendar
import java.time.LocalDate

data class EventForm(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val id: String? = null,
    val title: String = "",
    val type: EventType = EventType.DEATH_ANNIVERSARY,
    val calendar: CalendarKind = CalendarKind.LUNAR,
    val day: Int = 1,
    val month: Int = 1,
    val yearText: String = LocalDate.now().year.toString(),
    val leapMonth: Boolean = false,
    val repeat: RepeatKind = RepeatKind.YEARLY,
    val intervalDays: Int = 7,
    val hour: Int = 8,
    val minute: Int = 0,
    val reminderDays: Set<Int> = setOf(7, 1, 0),
    val customReminder: Int = 10,
    val personName: String = "",
    val location: String = "",
    val note: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val year: Int get() = yearText.toIntOrNull() ?: 0
}

class EventEditViewModel(
    private val container: AppContainer,
    private val eventId: String?,
    initialEpochDay: Long?,
) : ViewModel() {

    private val _form = MutableStateFlow(EventForm())
    val form: StateFlow<EventForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val e = eventId?.let { container.events.get(it) }
            if (e != null && !e.deleted) {
                _form.value = EventForm(
                    loading = false,
                    isNew = false,
                    id = e.id,
                    title = e.title,
                    type = EventType.of(e.type),
                    calendar = runCatching { CalendarKind.valueOf(e.calendarKind) }.getOrDefault(CalendarKind.SOLAR),
                    day = e.day,
                    month = e.month,
                    yearText = e.year.toString(),
                    leapMonth = e.leapMonth,
                    repeat = runCatching { RepeatKind.valueOf(e.repeat) }.getOrDefault(RepeatKind.NONE),
                    intervalDays = e.intervalDays,
                    hour = e.hour,
                    minute = e.minute,
                    reminderDays = parseReminderDays(e.reminderDays).toSet(),
                    personName = e.personName.orEmpty(),
                    location = e.location.orEmpty(),
                    note = e.note.orEmpty(),
                )
            } else {
                val defaultHour = container.settings.settings.first().defaultHour
                val date = initialEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
                val l = VietLunarCalendar.solarToLunar(date)
                _form.value = EventForm(
                    loading = false,
                    day = l.day,
                    month = l.month,
                    yearText = l.year.toString(),
                    leapMonth = l.isLeapMonth,
                    hour = defaultHour,
                )
            }
        }
    }

    fun edit(block: EventForm.() -> EventForm) = _form.update { it.block().copy(error = null) }

    /** Đổi loại: với sự kiện mới, chọn sẵn kiểu lịch phù hợp phong tục. */
    fun setType(t: EventType) = edit {
        if (!isNew) copy(type = t) else when (t) {
            EventType.DEATH_ANNIVERSARY -> copy(type = t, repeat = RepeatKind.YEARLY).withCalendar(CalendarKind.LUNAR)
            EventType.BIRTHDAY, EventType.ANNIVERSARY -> copy(type = t, repeat = RepeatKind.YEARLY)
            EventType.WEDDING, EventType.GATHERING, EventType.OTHER -> copy(type = t, repeat = RepeatKind.NONE)
            EventType.HOLIDAY -> copy(type = t, repeat = RepeatKind.YEARLY)
        }
    }

    /** Đổi giữa âm/dương: quy đổi luôn ngày đang chọn để người dùng không phải nhập lại. */
    fun setCalendar(c: CalendarKind) = edit { withCalendar(c) }

    private fun EventForm.withCalendar(c: CalendarKind): EventForm {
        if (c == calendar) return this
        val solar = previewSolar(this) ?: return copy(calendar = c, leapMonth = false)
        return if (c == CalendarKind.LUNAR) {
            val l = VietLunarCalendar.solarToLunar(solar)
            copy(calendar = c, day = l.day, month = l.month, yearText = l.year.toString(), leapMonth = l.isLeapMonth)
        } else {
            copy(calendar = c, day = solar.dayOfMonth, month = solar.monthValue, yearText = solar.year.toString(), leapMonth = false)
        }
    }

    fun toggleReminder(d: Int) = edit {
        copy(reminderDays = if (d in reminderDays) reminderDays - d else reminderDays + d)
    }

    fun save() {
        val f = _form.value
        if (f.saving) return
        _form.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r = try {
                container.events.save(toInput(f))
            } catch (e: Exception) {
                SaveResult.Invalid("Không lưu được: ${e.message ?: "lỗi không xác định"}")
            }
            _form.update {
                when (r) {
                    is SaveResult.Saved -> it.copy(saving = false, saved = true)
                    is SaveResult.Invalid -> it.copy(saving = false, error = r.message)
                }
            }
        }
    }

    fun delete() {
        val id = _form.value.id ?: return
        viewModelScope.launch {
            try {
                container.events.delete(id)
                _form.update { it.copy(deleted = true) }
            } catch (e: Exception) {
                _form.update { it.copy(error = "Không xóa được: ${e.message}") }
            }
        }
    }

    companion object {
        fun toInput(f: EventForm) = EventInput(
            id = f.id,
            title = f.title,
            type = f.type,
            calendar = f.calendar,
            day = f.day,
            month = f.month,
            year = f.year,
            leapMonth = f.calendar == CalendarKind.LUNAR && f.leapMonth,
            repeat = f.repeat,
            intervalDays = f.intervalDays,
            hour = f.hour,
            minute = f.minute,
            reminderDays = f.reminderDays,
            personName = f.personName,
            location = f.location,
            note = f.note,
        )

        /** Ngày dương của ngày gốc (để hiển thị đối chiếu); null nếu không hợp lệ. */
        fun previewSolar(f: EventForm): LocalDate? {
            if (f.year !in 1800..2199) return null
            return when (f.calendar) {
                CalendarKind.SOLAR -> runCatching { LocalDate.of(f.year, f.month, f.day) }.getOrNull()
                CalendarKind.LUNAR -> VietLunarCalendar.lunarToSolar(LunarDate(f.day, f.month, f.year, f.leapMonth))
            }
        }

        fun nextOccurrence(f: EventForm): LocalDate? {
            if (f.year !in 1800..2199) return null
            return runCatching {
                Recurrence.nextOccurrence(EventRepository.ruleOf(toInput(f)), LocalDate.now())
            }.getOrNull()
        }
    }
}
