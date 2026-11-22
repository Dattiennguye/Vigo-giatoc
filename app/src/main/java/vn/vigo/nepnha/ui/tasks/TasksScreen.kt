package vn.vigo.nepnha.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.data.db.TaskWithProgress
import vn.vigo.nepnha.data.repo.SaveResult
import vn.vigo.nepnha.data.repo.TaskStatus
import vn.vigo.nepnha.ui.components.ChoiceChips
import vn.vigo.nepnha.ui.components.EmptyState
import vn.vigo.nepnha.ui.components.LinkButton
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.components.VnFormat
import vn.vigo.nepnha.ui.theme.NepColors
import java.time.LocalDate

@Composable
fun TasksScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpen: (String, Boolean) -> Unit,
    onAdd: (Boolean) -> Unit,
) {
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    val flow = remember(showTemplates) { container.tasks.tasks(showTemplates) }
    val list by flow.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Việc gia đình", onBack = onBack)
        ChoiceChips(
            listOf(false, true),
            { it == showTemplates },
            { if (it) "Mẫu đã lưu" else "Việc cần làm" },
            { showTemplates = it },
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        message?.let { StatusBanner(it, isError = false, modifier = Modifier.padding(horizontal = 16.dp)) }
        val taskList = list
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                taskList == null -> Unit
                taskList.isEmpty() -> item {
                    EmptyState(
                        if (showTemplates) "Chưa có mẫu" else "Chưa có việc nào",
                        if (showTemplates) "Lưu một danh sách chuẩn bị (ví dụ: chuẩn bị giỗ) làm mẫu để dùng lại mỗi năm."
                        else "Tạo việc, phân công người làm và đánh dấu từng mục đã chuẩn bị.",
                    )
                }
                else -> items(taskList, key = { it.id }, contentType = { "task" }) { t ->
                    TaskCard(
                        t,
                        onOpen = { onOpen(t.id, showTemplates) },
                        onUseTemplate = {
                            scope.launch {
                                val r = container.tasks.createFromTemplate(t.id, LocalDate.now().plusDays(7))
                                message = if (r is SaveResult.Saved) "Đã tạo việc mới từ mẫu \"${t.title}\"" else "Không tạo được"
                                showTemplates = false
                            }
                        },
                        onToggleDone = {
                            scope.launch {
                                container.tasks.setStatus(t.id, if (t.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE)
                            }
                        },
                    )
                }
            }
        }
        PrimaryButton(
            if (showTemplates) "Tạo mẫu mới" else "Thêm việc",
            { onAdd(showTemplates) },
            Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
            icon = Icons.Filled.Add,
        )
    }
}

@Composable
private fun TaskCard(t: TaskWithProgress, onOpen: () -> Unit, onUseTemplate: () -> Unit, onToggleDone: () -> Unit) {
    NepCard(onClick = onOpen, onClickLabel = "Mở công việc", framed = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                color = if (t.status == TaskStatus.DONE) NepColors.InkSoft else NepColors.Ink,
            )
            if (!t.isTemplate) {
                Text(
                    TaskStatus.label(t.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (t.status == TaskStatus.DONE) NepColors.Success else NepColors.DeepRed,
                )
            }
        }
        val meta = buildList {
            t.assignee?.let { add("Phụ trách: $it") }
            t.dueEpochDay?.let { add("Hạn: ${VnFormat.solar(LocalDate.ofEpochDay(it))}") }
        }.joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
        if (t.total > 0) {
            LinearProgressIndicator(
                progress = { t.done.toFloat() / t.total },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = NepColors.DeepRed,
                trackColor = NepColors.BronzeLight,
            )
            Text("Đã chuẩn bị ${t.done}/${t.total} mục", style = MaterialTheme.typography.bodySmall)
        }
        if (t.isTemplate) {
            LinkButton("Dùng mẫu này", onUseTemplate)
        } else {
            LinkButton(if (t.status == TaskStatus.DONE) "Đánh dấu chưa xong" else "Đánh dấu đã xong", onToggleDone)
        }
    }
}
