package vn.vigo.nepnha.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.data.repo.SaveResult
import vn.vigo.nepnha.data.repo.TaskInput
import vn.vigo.nepnha.data.repo.TaskStatus
import vn.vigo.nepnha.ui.components.ChoiceChips
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
import java.time.LocalDate

/** Danh sách chuẩn bị gợi ý (người dùng sửa thoải mái). */
private val PRESETS = mapOf(
    "Chuẩn bị giỗ" to listOf(
        "Báo tin, mời họ hàng", "Dọn dẹp, lau bàn thờ", "Mua hương, hoa, nến", "Chuẩn bị mâm cỗ",
        "Chuẩn bị văn khấn", "Sắp xếp chỗ ngồi", "Thắp hương tạ ơn sau lễ",
    ),
    "Chuẩn bị Tết" to listOf(
        "Dọn nhà, lau bàn thờ", "Mua cây, hoa Tết", "Gói/đặt bánh chưng", "Sắm mâm ngũ quả",
        "Cúng ông Công ông Táo (23 tháng Chạp)", "Chuẩn bị lì xì", "Cúng tất niên", "Cúng giao thừa",
    ),
    "Họp mặt gia đình" to listOf(
        "Chốt ngày giờ, địa điểm", "Gọi điện mời từng nhà", "Phân công món ăn", "Chuẩn bị chỗ ngồi", "Chụp ảnh lưu niệm",
    ),
)

data class TaskForm(
    val loading: Boolean = true,
    val id: String? = null,
    val title: String = "",
    val assignee: String = "",
    val hasDue: Boolean = false,
    val due: LocalDate = LocalDate.now().plusDays(7),
    val status: String = TaskStatus.TODO,
    val isTemplate: Boolean = false,
    val note: String = "",
    val items: List<Pair<String, Boolean>> = emptyList(),
    val eventId: String? = null,
    val error: String? = null,
    val done: Boolean = false,
)

class TaskEditViewModel(private val container: AppContainer, taskId: String?, asTemplate: Boolean) : ViewModel() {
    private val _f = MutableStateFlow(TaskForm())
    val form = _f.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = taskId?.let { container.tasks.load(it) }
            _f.value = if (loaded != null) {
                val (t, items) = loaded
                TaskForm(
                    loading = false, id = t.id, title = t.title, assignee = t.assignee.orEmpty(),
                    hasDue = t.dueEpochDay != null, due = t.dueEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusDays(7),
                    status = t.status, isTemplate = t.isTemplate, note = t.note.orEmpty(),
                    items = items.map { it.text to it.done }, eventId = t.eventId,
                )
            } else TaskForm(loading = false, isTemplate = asTemplate)
        }
    }

    fun edit(b: TaskForm.() -> TaskForm) = _f.update { it.b().copy(error = null) }

    fun save() = viewModelScope.launch {
        val f = _f.value
        val r = container.tasks.save(
            TaskInput(f.id, f.title, f.assignee, if (f.hasDue) f.due else null, f.status, f.eventId, f.isTemplate, f.note, f.items),
        )
        _f.update { if (r is SaveResult.Invalid) it.copy(error = r.message) else it.copy(done = true) }
    }

    fun delete() = viewModelScope.launch {
        _f.value.id?.let { container.tasks.delete(it) }
        _f.update { it.copy(done = true) }
    }
}

@Composable
fun TaskEditScreen(container: AppContainer, taskId: String?, asTemplate: Boolean, onDone: () -> Unit) {
    val vm = nepViewModel(key = "task-$taskId-$asTemplate") { TaskEditViewModel(container, taskId, asTemplate) }
    val f by vm.form.collectAsStateWithLifecycle()
    var newItem by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(f.done) { if (f.done) onDone() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            when {
                f.id == null && f.isTemplate -> "Tạo mẫu việc"
                f.id == null -> "Thêm việc"
                else -> "Sửa việc"
            },
            onBack = onDone,
        )
        if (f.loading) {
            LoadingState()
            return@Column
        }
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NepTextField("Tên công việc", f.title, { v -> vm.edit { copy(title = v.take(120)) } })
            NepTextField("Người phụ trách", f.assignee, { v -> vm.edit { copy(assignee = v.take(80)) } })
            if (!f.isTemplate) {
                SectionTitle("Tiến độ")
                ChoiceChips(
                    listOf(TaskStatus.TODO, TaskStatus.DOING, TaskStatus.DONE),
                    { it == f.status }, TaskStatus::label, { s -> vm.edit { copy(status = s) } },
                )
                SwitchRow("Có hạn hoàn thành", f.hasDue, { v -> vm.edit { copy(hasDue = v) } })
                if (f.hasDue) {
                    NepCard {
                        Text("Hạn: ${VnFormat.solarLong(f.due)} (${VnFormat.lunarOf(f.due)} âm)", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberStepper("Ngày", f.due.dayOfMonth, 1..f.due.lengthOfMonth(), { d -> vm.edit { copy(due = due.withDayOfMonth(d)) } })
                            NumberStepper("Tháng", f.due.monthValue, 1..12, { m ->
                                vm.edit { copy(due = due.withDayOfMonth(1).withMonth(m).let { it.withDayOfMonth(minOf(due.dayOfMonth, it.lengthOfMonth())) }) }
                            })
                        }
                        NumberStepper("Năm", f.due.year, LocalDate.now().year - 1..LocalDate.now().year + 5, { y ->
                            vm.edit { copy(due = due.withDayOfMonth(1).withYear(y).let { it.withDayOfMonth(minOf(due.dayOfMonth, it.lengthOfMonth())) }) }
                        })
                    }
                }
            }

            NepCard {
                SectionTitle("Danh sách chuẩn bị")
                if (f.items.isEmpty()) {
                    Text("Chọn nhanh một danh sách gợi ý:", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
                    PRESETS.forEach { (name, list) ->
                        SecondaryButton(name, { vm.edit { copy(items = list.map { it to false }, title = title.ifBlank { name }) } },
                            Modifier.fillMaxWidth().padding(vertical = 2.dp))
                    }
                }
                f.items.forEachIndexed { i, (text, done) ->
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                            .toggleable(done, role = Role.Checkbox) { v ->
                                vm.edit { copy(items = items.toMutableList().also { it[i] = text to v }) }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(done, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = NepColors.DeepRed))
                        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconButton(onClick = { vm.edit { copy(items = items.filterIndexed { j, _ -> j != i }) } }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Xóa mục \"$text\"", tint = NepColors.InkSoft)
                        }
                    }
                }
                NepTextField("Mục mới", newItem, { newItem = it.take(200) })
                SecondaryButton("Thêm mục", {
                    if (newItem.isNotBlank()) {
                        vm.edit { copy(items = items + (newItem.trim() to false)) }
                        newItem = ""
                    }
                }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            }
            NepTextField("Ghi chú", f.note, { v -> vm.edit { copy(note = v.take(2000)) } }, singleLine = false)
            if (f.id == null) {
                SwitchRow("Lưu làm mẫu dùng lại", f.isTemplate, { v -> vm.edit { copy(isTemplate = v) } },
                    description = "Mẫu xuất hiện ở mục \"Mẫu đã lưu\" để tạo nhanh mỗi năm.")
            }
            f.error?.let { StatusBanner(it, isError = true) }
            PrimaryButton("Lưu", { vm.save() }, Modifier.fillMaxWidth())
            if (f.id != null) SecondaryButton("Xóa", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa công việc?") },
            text = { Text("\"${f.title}\" sẽ bị xóa.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete() }) { Text("Xóa", color = NepColors.Error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Không xóa") } },
        )
    }
}
