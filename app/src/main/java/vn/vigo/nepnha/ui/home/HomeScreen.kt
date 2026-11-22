package vn.vigo.nepnha.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.domain.Occurrence
import vn.vigo.nepnha.lunar.Holiday
import vn.vigo.nepnha.ui.components.EmptyState
import vn.vigo.nepnha.ui.components.ErrorState
import vn.vigo.nepnha.ui.components.KeyPatternBand
import vn.vigo.nepnha.ui.components.LinkButton
import vn.vigo.nepnha.ui.components.LoadingState
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.OccurrenceRow
import vn.vigo.nepnha.ui.components.OrnamentDivider
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.SecondaryButton
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.VnFormat
import vn.vigo.nepnha.ui.components.nepViewModel
import vn.vigo.nepnha.ui.theme.NepColors
import java.time.LocalDate

@Composable
fun HomeScreen(
    container: AppContainer,
    onAddEvent: () -> Unit,
    onAddTask: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val vm = nepViewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        item(key = "brand", contentType = "brand") { Brand() }
        when (val s = state) {
            HomeState.Loading -> item(key = "loading") { LoadingState() }
            is HomeState.Error -> item(key = "error") { ErrorState(s.message) }
            is HomeState.Ready -> {
                item(key = "today", contentType = "today") {
                    TodayCard(s, onOpenCalendar, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                item(key = "quick", contentType = "quick") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PrimaryButton("Thêm sự kiện", onAddEvent, Modifier.weight(1f), icon = Icons.Filled.Add)
                        SecondaryButton("Thêm việc", onAddTask, Modifier.weight(1f), icon = Icons.Filled.Check)
                    }
                }
                if (s.totalEvents == 0) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Chưa có ngày nào được ghi",
                            message = "Hãy thêm ngày giỗ, sinh nhật của người thân để Nếp Nhà nhắc bạn trước nhiều ngày.",
                            actionText = "Thêm ngày giỗ đầu tiên",
                            onAction = onAddEvent,
                        )
                    }
                } else {
                    item(key = "gio") {
                        OccurrenceSection("Ngày giỗ sắp tới", s.deathAnniversaries, s.today.date, onOpenEvent,
                            "Chưa có ngày giỗ nào trong năm tới.")
                    }
                    item(key = "sn") {
                        OccurrenceSection("Sinh nhật sắp tới", s.birthdays, s.today.date, onOpenEvent,
                            "Chưa có sinh nhật nào trong năm tới.")
                    }
                    if (s.others.isNotEmpty()) {
                        item(key = "other") {
                            OccurrenceSection("Sự kiện gia đình", s.others, s.today.date, onOpenEvent, "")
                        }
                    }
                }
                if (s.holidays.isNotEmpty()) {
                    item(key = "holidays") { HolidaySection(s.holidays, s.today.date) }
                }
                item(key = "tasks") {
                    NepCard(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        onClick = onOpenTasks,
                        onClickLabel = "Mở việc gia đình",
                    ) {
                        Text("Việc gia đình", style = MaterialTheme.typography.titleLarge, color = NepColors.Wood)
                        Text(
                            if (s.openTasks == 0) "Không có việc nào đang chờ." else "${s.openTasks} việc chưa hoàn thành",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        LinkButton("Xem danh sách việc", onOpenTasks)
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "NẾP NHÀ",
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 4.sp),
            color = NepColors.DeepRed,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Gìn giữ ngày tháng – Kết nối các thế hệ",
            style = MaterialTheme.typography.bodySmall,
            color = NepColors.Wood,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        KeyPatternBand(height = 10.dp)
    }
}

@Composable
private fun TodayCard(s: HomeState.Ready, onOpenCalendar: () -> Unit, modifier: Modifier) {
    val t = s.today
    NepCard(modifier, onClick = onOpenCalendar, onClickLabel = "Mở lịch") {
        Text(VnFormat.weekday(t.date.dayOfWeek), style = MaterialTheme.typography.titleMedium, color = NepColors.Wood)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${t.date.dayOfMonth}",
                fontSize = 64.sp,
                lineHeight = 68.sp,
                fontWeight = FontWeight.Bold,
                color = NepColors.DeepRed,
                style = MaterialTheme.typography.displaySmall,
            )
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Tháng ${t.date.monthValue}, ${t.date.year}", style = MaterialTheme.typography.titleMedium)
                Text("Dương lịch", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
            }
        }
        OrnamentDivider(Modifier.padding(vertical = 6.dp))
        Text(
            "Âm lịch: ngày ${t.lunar.day} tháng ${VnFormat.lunarMonthName(t.lunar.month)}" +
                (if (t.lunar.isLeapMonth) " (nhuận)" else ""),
            style = MaterialTheme.typography.titleMedium,
            color = NepColors.Ink,
        )
        Text(
            "Năm ${t.yearCanChi} · Tháng ${t.monthCanChi} · Ngày ${t.dayCanChi}",
            style = MaterialTheme.typography.bodyMedium,
            color = NepColors.InkSoft,
        )
        Text("Tiết ${t.solarTerm}", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
    }
}

@Composable
private fun OccurrenceSection(
    title: String,
    items: List<Occurrence>,
    today: LocalDate,
    onOpen: (String) -> Unit,
    emptyText: String,
) {
    NepCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(title)
        if (items.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = NepColors.InkSoft)
        } else {
            items.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(color = NepColors.BronzeLight)
                OccurrenceRow(o, today, onClick = { onOpen(o.event.id) })
            }
        }
    }
}

@Composable
private fun HolidaySection(items: List<Holiday>, today: LocalDate) {
    NepCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle("Ngày lễ sắp tới")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { h ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            VnFormat.solarLong(h.date) + if (h.isLunar) " · ${VnFormat.lunarOf(h.date)} âm" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = NepColors.InkSoft,
                        )
                    }
                    Text(VnFormat.relative(today, h.date), style = MaterialTheme.typography.labelMedium, color = NepColors.Wood)
                }
            }
        }
    }
}
