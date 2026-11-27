package vn.vigo.nepnha.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.lunar.CanChi
import vn.vigo.nepnha.ui.components.ChoiceChips
import vn.vigo.nepnha.ui.components.ColorDot
import vn.vigo.nepnha.ui.components.LinkButton
import vn.vigo.nepnha.ui.components.LoadingState
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.OccurrenceRow
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.VnFormat
import vn.vigo.nepnha.ui.components.nepViewModel
import vn.vigo.nepnha.ui.theme.NepColors
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun CalendarScreen(
    container: AppContainer,
    onAddEvent: (LocalDate) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val vm = nepViewModel { CalendarViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            ScreenHeader("Lịch âm dương", action = { LinkButton("Hôm nay") { vm.goToday() } })
        }
        item(key = "mode") {
            ChoiceChips(
                options = CalendarMode.entries,
                selected = { it == ui.mode },
                label = { it.label },
                onToggle = vm::setMode,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item(key = "filter") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Lọc theo loại", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
                ChoiceChips(
                    options = EventType.entries,
                    selected = { it in ui.filters },
                    label = { it.label },
                    onToggle = vm::toggleFilter,
                )
                if (ui.filters.isNotEmpty()) LinkButton("Bỏ lọc") { vm.clearFilters() }
            }
        }
        if (!ui.ready) {
            item(key = "loading") { LoadingState() }
            return@LazyColumn
        }
        when (ui.mode) {
            CalendarMode.MONTH -> {
                item(key = "month-nav") {
                    PeriodNav(
                        title = "Tháng ${ui.month.monthValue}/${ui.month.year}",
                        prevLabel = "Tháng trước",
                        nextLabel = "Tháng sau",
                        onPrev = { vm.shiftMonth(-1) },
                        onNext = { vm.shiftMonth(1) },
                    )
                }
                item(key = "grid") { MonthGrid(ui, vm::select) }
                item(key = "day-detail") {
                    DayDetail(ui, onAddEvent, onOpenEvent, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            CalendarMode.DAY -> {
                item(key = "day-nav") {
                    PeriodNav(
                        title = VnFormat.solarLong(ui.selected),
                        prevLabel = "Ngày trước",
                        nextLabel = "Ngày sau",
                        onPrev = { vm.shiftDay(-1) },
                        onNext = { vm.shiftDay(1) },
                    )
                }
                item(key = "day-big") { BigDay(ui.selected) }
                item(key = "day-detail") {
                    DayDetail(ui, onAddEvent, onOpenEvent, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            CalendarMode.YEAR -> {
                item(key = "year-nav") {
                    PeriodNav(
                        title = "Năm ${ui.month.year} (${CanChi.year(ui.month.year)})",
                        prevLabel = "Năm trước",
                        nextLabel = "Năm sau",
                        onPrev = { vm.shiftYear(-1) },
                        onNext = { vm.shiftYear(1) },
                    )
                }
                ui.yearSummary.forEach { (ym, occ) ->
                    item(key = "y-$ym", contentType = "year-month") {
                        NepCard(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            onClick = { vm.openMonth(ym) },
                            onClickLabel = "Mở tháng ${ym.monthValue}",
                            framed = false,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Tháng ${ym.monthValue}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NepColors.Wood,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (occ.isEmpty()) "Không có sự kiện" else "${occ.size} sự kiện",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NepColors.InkSoft,
                                )
                            }
                            occ.take(4).forEach {
                                Text(
                                    "• ${it.date.dayOfMonth}/${it.date.monthValue} – ${it.event.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                            }
                            if (occ.size > 4) {
                                Text("… và ${occ.size - 4} sự kiện khác", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodNav(title: String, prevLabel: String, nextLabel: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = prevLabel, tint = NepColors.DeepRed)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextLabel, tint = NepColors.DeepRed)
        }
    }
}

@Composable
private fun MonthGrid(ui: CalendarUi, onSelect: (LocalDate) -> Unit) {
    val weekdays = remember { DayOfWeek.entries.map { VnFormat.weekdayShort(it) } }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .background(NepColors.WarmWhite, RoundedCornerShape(16.dp))
            .border(1.dp, NepColors.Hairline, RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEachIndexed { i, w ->
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == 6) NepColors.DeepRed else NepColors.Wood,
                )
            }
        }
        ui.cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { c ->
                    DayCellView(c, c.date == ui.selected, c.date == ui.today, onSelect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCellView(c: DayCell, selected: Boolean, isToday: Boolean, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val lunarText = if (c.lunar.day == 1) "${c.lunar.day}/${c.lunar.month}" else "${c.lunar.day}"
    val bg = if (selected) NepColors.DeepRed else Color.Transparent
    val main = when {
        selected -> NepColors.WarmWhite
        !c.inMonth -> NepColors.InkSoft.copy(alpha = 0.5f)
        c.isHoliday || c.date.dayOfWeek == DayOfWeek.SUNDAY -> NepColors.DeepRed
        else -> NepColors.Ink
    }
    val sub = when {
        selected -> NepColors.WarmWhite
        c.lunar.day == 1 || c.lunar.day == 15 -> NepColors.DeepRed
        else -> NepColors.InkSoft
    }
    val desc = "Ngày ${c.date.dayOfMonth} tháng ${c.date.monthValue}, âm lịch ${VnFormat.lunar(c.lunar)}" +
        (if (c.eventTypes.isNotEmpty()) ", có ${c.eventTypes.joinToString { it.label }}" else "")
    Column(
        modifier
            .heightIn(min = 58.dp)
            .padding(2.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .border(if (isToday && !selected) 1.5.dp else 0.dp, if (isToday && !selected) NepColors.DeepRed else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onSelect(c.date) }
            .semantics(mergeDescendants = true) {
                contentDescription = desc
                this.selected = selected
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${c.date.dayOfMonth}",
            fontSize = 19.sp,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Medium,
            color = main,
            maxLines = 1,
        )
        Text(lunarText, fontSize = 12.sp, color = sub, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp)) {
            c.eventTypes.forEach { ColorDot(if (selected) NepColors.WarmWhite else Color(it.colorHex), size = 6.dp) }
        }
    }
}

@Composable
private fun BigDay(d: LocalDate) {
    val l = remember(d) { vn.vigo.nepnha.lunar.VietLunarCalendar.solarToLunar(d) }
    NepCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("${d.dayOfMonth}", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = NepColors.DeepRed,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Tháng ${d.monthValue} năm ${d.year}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Âm lịch: ${l.day} tháng ${VnFormat.lunarMonthName(l.month)}${if (l.isLeapMonth) " (nhuận)" else ""} năm ${CanChi.year(l.year)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            "Ngày ${CanChi.day(d)} · Tháng ${CanChi.month(l.month, l.year)} · Tiết ${CanChi.solarTerm(d)}",
            style = MaterialTheme.typography.bodySmall,
            color = NepColors.InkSoft,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayDetail(ui: CalendarUi, onAdd: (LocalDate) -> Unit, onOpen: (String) -> Unit, modifier: Modifier) {
    NepCard(modifier) {
        SectionTitle(VnFormat.solarLong(ui.selected))
        Text("Âm lịch ${VnFormat.lunarOf(ui.selected)}", style = MaterialTheme.typography.bodyMedium, color = NepColors.InkSoft)
        ui.selectedHolidays.forEach {
            Text("Lễ: ${it.name}", style = MaterialTheme.typography.titleSmall, color = NepColors.DeepRed)
        }
        if (ui.selectedOccurrences.isEmpty()) {
            Text("Không có sự kiện trong ngày này.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            ui.selectedOccurrences.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(color = NepColors.BronzeLight)
                OccurrenceRow(o, ui.today, onClick = { onOpen(o.event.id) })
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Thêm sự kiện ngày này", { onAdd(ui.selected) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
    }
}
