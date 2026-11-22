package vn.vigo.nepnha.ui.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.reminder.DeviceHealth
import vn.vigo.nepnha.ui.components.EmptyState
import vn.vigo.nepnha.ui.components.LinkButton
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SecondaryButton
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.theme.NepColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")

@Composable
fun NotificationsScreen(container: AppContainer, onOpenHealth: () -> Unit) {
    val context = LocalContext.current
    val logFlow = remember { container.db.notifications().observe(200) }
    val log by logFlow.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val health = remember(refresh) { DeviceHealth.check(context, container.scheduler) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            ScreenHeader("Thông báo", action = {
                if (!log.isNullOrEmpty()) LinkButton("Đã đọc hết") { scope.launch { container.db.notifications().markAllRead() } }
            })
        }
        item(key = "health") {
            NepCard(Modifier.padding(16.dp)) {
                Text("Tình trạng nhắc lịch", style = MaterialTheme.typography.titleLarge, color = NepColors.Wood)
                if (health.allGood) {
                    StatusBanner("Mọi quyền cần thiết đã bật. Nhắc lịch sẽ hoạt động bình thường.", isError = false)
                } else {
                    StatusBanner(
                        "Một số cài đặt có thể làm thông báo đến trễ hoặc không đến. " +
                            "Nếp Nhà không thể bảo đảm nhắc đúng giờ khi hệ điều hành chặn quyền.",
                        isError = true,
                    )
                    if (!health.notificationsAllowed) {
                        PrimaryButton("Cho phép thông báo", {
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else DeviceHealth.openNotificationSettings(context)
                        }, Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
                SecondaryButton("Xem hướng dẫn chi tiết", onOpenHealth, Modifier.fillMaxWidth().padding(top = 8.dp))
                SecondaryButton("Gửi thông báo thử", {
                    testResult = container.scheduler.postTest()
                }, Modifier.fillMaxWidth().padding(top = 8.dp))
                testResult?.let {
                    StatusBanner(
                        if (it) "Đã gửi. Hãy kéo thanh trạng thái xuống để kiểm tra."
                        else "Không gửi được: thông báo đang bị tắt. Hãy bấm \"Cho phép thông báo\".",
                        isError = !it,
                    )
                }
            }
        }
        val logList = log
        when {
            logList == null -> Unit
            logList.isEmpty() -> item(key = "empty") {
                EmptyState("Chưa có thông báo", "Các lần nhắc ngày giỗ, sinh nhật, sự kiện sẽ được lưu lại ở đây.")
            }
            else -> items(logList, key = { it.id }, contentType = { "log" }) { n ->
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(
                        n.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                        color = if (n.read) NepColors.InkSoft else NepColors.Ink,
                    )
                    Text(n.body, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        TIME_FMT.format(Instant.ofEpochMilli(n.createdAt).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodySmall,
                        color = NepColors.InkSoft,
                    )
                }
                HorizontalDivider(color = NepColors.BronzeLight, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
