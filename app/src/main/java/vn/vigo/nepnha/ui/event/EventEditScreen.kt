package vn.vigo.nepnha.ui.event

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.domain.PRESET_REMINDER_DAYS
import vn.vigo.nepnha.domain.reminderLabel
import vn.vigo.nepnha.lunar.CalendarKind
import vn.vigo.nepnha.lunar.RepeatKind
import vn.vigo.nepnha.lunar.VietLunarCalendar
import vn.vigo.nepnha.ui.components.ChoiceChips
import vn.vigo.nepnha.ui.components.InfoBox
import vn.vigo.nepnha.ui.components.LoadingState
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.NepTextField
import vn.vigo.nepnha.ui.components.NumberStepper
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SecondaryButton
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.components.SwitchRow
import vn.vigo.nepnha.ui.components.VnFormat
import vn.vigo.nepnha.ui.components.nepViewModel
import vn.vigo.nepnha.ui.theme.NepColors

private val REPEAT_OPTIONS = listOf(RepeatKind.NONE, RepeatKind.YEARLY, RepeatKind.MONTHLY, RepeatKind.EVERY_N_DAYS)

private fun repeatLabel(r: RepeatKind) = when (r) {
    RepeatKind.NONE -> "Một lần"
    RepeatKind.YEARLY -> "Hằng năm"
    RepeatKind.MONTHLY -> "Hằng tháng"
    RepeatKind.EVERY_N_DAYS -> "Tùy chỉnh"
}

@Composable
fun EventEditScreen(container: AppContainer, eventId: String?, initialEpochDay: Long?, onDone: () -> Unit) {
    val vm = nepViewModel(key = "event-$eventId-$initialEpochDay") { EventEditViewModel(container, eventId, initialEpochDay) }
    val f by vm.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Xin quyền thông báo (Android 13+) ngay sau lần lưu đầu tiên, khi người dùng hiểu vì sao cần.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }

    LaunchedEffect(f.saved, f.deleted) {
        if (f.deleted) onDone()
        if (f.saved) {
            val needAsk = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
            if (needAsk && f.reminderDays.isNotEmpty()) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onDone()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (f.isNew) "Thêm sự kiện" else "Sửa sự kiện", onBack = onDone)
        if (f.loading) {
            LoadingState()
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NepTextField(
                "Tên sự kiện (ví dụ: Giỗ ông nội)",
                f.title,
                { v -> vm.edit { copy(title = v.take(120)) } },
                error = if (f.error != null && f.title.isBlank()) "Bắt buộc" else null,
            )

            SectionTitle("Loại sự kiện")
            ChoiceChips(EventType.entries, { it == f.type }, { it.label }, vm::setType)

            NepCard {
                SectionTitle("Ngày")
                ChoiceChips(
                    listOf(CalendarKind.LUNAR, CalendarKind.SOLAR),
                    { it == f.calendar },
                    { if (it == CalendarKind.LUNAR) "Âm lịch" else "Dương lịch" },
                    vm::setCalendar,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                    NumberStepper("Ngày", f.day, 1..(if (f.calendar == CalendarKind.LUNAR) 30 else 31), { v -> vm.edit { copy(day = v) } })
                    NumberStepper(
                        "Tháng", f.month, 1..12, { v -> vm.edit { copy(month = v) } },
                        format = { if (f.calendar == CalendarKind.LUNAR) VnFormat.lunarMonthName(it) else it.toString() },
                    )
                }
                NepTextField(
                    "Năm (ví dụ 1975)",
                    f.yearText,
                    { v -> vm.edit { copy(yearText = v.filter { it.isDigit() }.take(4)) } },
                    numeric = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (f.calendar == CalendarKind.LUNAR) {
                    val leap = remember(f.yearText) { f.year.takeIf { it in 1800..2199 }?.let { VietLunarCalendar.leapMonthOf(it) } }
                    SwitchRow(
                        "Tháng nhuận",
                        f.leapMonth,
                        { v -> vm.edit { copy(leapMonth = v) } },
                        description = leap?.let { "Năm ${f.yearText} nhuận tháng ${VnFormat.lunarMonthName(it)}" }
                            ?: "Năm này không có tháng nhuận",
                    )
                }
                val preview = remember(f.calendar, f.day, f.month, f.yearText, f.leapMonth) { EventEditViewModel.previewSolar(f) }
                InfoBox(
                    when {
                        preview == null -> "Ngày này không tồn tại theo lịch đã chọn."
                        f.calendar == CalendarKind.LUNAR -> "Tương ứng dương lịch: ${VnFormat.solarLong(preview)}"
                        else -> "Tương ứng âm lịch: ${VnFormat.lunarOf(preview)}"
                    },
                )
            }

            NepCard {
                SectionTitle("Lặp lại")
                ChoiceChips(REPEAT_OPTIONS, { it == f.repeat }, ::repeatLabel, { r -> vm.edit { copy(repeat = r) } })
                if (f.repeat == RepeatKind.EVERY_N_DAYS) {
                    NumberStepper("Cứ mỗi (ngày)", f.intervalDays, 1..365, { v -> vm.edit { copy(intervalDays = v) } })
                }
                if (f.calendar == CalendarKind.LUNAR && f.repeat == RepeatKind.YEARLY) {
                    InfoBox(
                        "Lặp theo âm lịch: nếu năm nào tháng thiếu không có ngày 30 sẽ nhắc vào ngày cuối tháng (29); " +
                            "nếu ngày gốc ở tháng nhuận sẽ nhắc vào tháng thường cùng tên.",
                    )
                }
                val next = remember(f) { EventEditViewModel.nextOccurrence(f) }
                if (next != null && f.repeat != RepeatKind.NONE) {
                    Text(
                        "Lần tới: ${VnFormat.solarLong(next)} (${VnFormat.lunarOf(next)} âm)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NepColors.Wood,
                    )
                }
            }

            NepCard {
                SectionTitle("Nhắc trước")
                ChoiceChips(
                    (PRESET_REMINDER_DAYS + f.reminderDays).distinct().sortedDescending(),
                    { it in f.reminderDays },
                    ::reminderLabel,
                    vm::toggleReminder,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberStepper("Tùy chỉnh (ngày)", f.customReminder, 1..120, { v -> vm.edit { copy(customReminder = v) } })
                    SecondaryButton("Thêm mốc", { vm.edit { copy(reminderDays = reminderDays + customReminder) } })
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NumberStepper("Giờ nhắc", f.hour, 0..23, { v -> vm.edit { copy(hour = v) } }, format = { "%02d".format(it) })
                    NumberStepper(
                        "Phút", f.minute, 0..59, { v -> vm.edit { copy(minute = v) } },
                        format = { "%02d".format(it) },
                    )
                }
                if (f.reminderDays.isEmpty()) {
                    Text("Sẽ không có thông báo nhắc cho sự kiện này.", style = MaterialTheme.typography.bodySmall, color = NepColors.Error)
                }
            }

            NepTextField("Người liên quan (không bắt buộc)", f.personName, { v -> vm.edit { copy(personName = v.take(120)) } })
            NepTextField("Địa điểm (không bắt buộc)", f.location, { v -> vm.edit { copy(location = v.take(200)) } })
            NepTextField("Ghi chú", f.note, { v -> vm.edit { copy(note = v.take(2000)) } }, singleLine = false)

            f.error?.let { StatusBanner(it, isError = true) }

            PrimaryButton(
                if (f.saving) "Đang lưu…" else "Lưu sự kiện",
                vm::save,
                Modifier.fillMaxWidth(),
                enabled = !f.saving,
            )
            if (!f.isNew) {
                SecondaryButton("Xóa sự kiện", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa sự kiện?") },
            text = { Text("Sự kiện \"${f.title}\" và các lịch nhắc của nó sẽ bị xóa.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) {
                    Text("Xóa", color = NepColors.Error, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Không xóa", style = MaterialTheme.typography.labelLarge) }
            },
        )
    }
}
