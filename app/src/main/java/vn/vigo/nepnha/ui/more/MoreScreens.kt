package vn.vigo.nepnha.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.BuildConfig
import vn.vigo.nepnha.data.repo.AppSettings
import vn.vigo.nepnha.kinship.Dialect
import vn.vigo.nepnha.reminder.DeviceHealth
import vn.vigo.nepnha.ui.components.ChoiceChips
import vn.vigo.nepnha.ui.components.InfoBox
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.NumberStepper
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SecondaryButton
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.components.SwitchRow
import vn.vigo.nepnha.ui.theme.NepColors

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NepColors.Bronze)
    }
    HorizontalDivider(color = NepColors.BronzeLight, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun MoreScreen(onOpenTasks: () -> Unit, onOpenSettings: () -> Unit, onOpenHealth: () -> Unit, onOpenAbout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Thêm")
        MenuRow("Việc gia đình", "Công việc, phân công, danh sách chuẩn bị lễ", onOpenTasks)
        MenuRow("Cỡ chữ và hiển thị", "Phóng to chữ, cách xưng hô vùng miền, hiệu ứng", onOpenSettings)
        MenuRow("Nhắc lịch không bị chặn", "Hướng dẫn cho Xiaomi/HyperOS và các máy Android", onOpenHealth)
        MenuRow("Giới thiệu Nếp Nhà", "Phiên bản, tình trạng chức năng", onOpenAbout)
    }
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val s by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val repo = container.settings
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Cỡ chữ và hiển thị", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NepCard {
                SectionTitle("Cỡ chữ")
                ChoiceChips(
                    listOf(1f, 1.15f, 1.3f, 1.5f),
                    { kotlin.math.abs(it - s.fontScale) < 0.01f },
                    { "${(it * 100).toInt()}%" },
                    { v -> scope.launch { repo.setFontScale(v) } },
                )
                Text("Chữ mẫu: Giỗ ông nội – 15 tháng 7 âm lịch", style = MaterialTheme.typography.bodyLarge)
            }
            NepCard {
                SectionTitle("Cách xưng hô")
                ChoiceChips(
                    Dialect.entries,
                    { it == s.dialect },
                    { if (it == Dialect.NORTH) "Miền Bắc" else "Miền Trung – Nam" },
                    { v -> scope.launch { repo.setDialect(v) } },
                )
                InfoBox("Dùng khi gia phả tự gọi vai vế (ví dụ anh trai của mẹ: miền Bắc gọi Bác, miền Nam gọi Cậu).")
            }
            NepCard {
                SectionTitle("Nhắc lịch")
                NumberStepper("Giờ nhắc mặc định cho sự kiện mới", s.defaultHour, 0..23,
                    { v -> scope.launch { repo.setDefaultHour(v) } }, format = { "%02d:00".format(it) })
                SwitchRow("Hiện ngày lễ, Tết", s.showHolidays, { v -> scope.launch { repo.setShowHolidays(v) } })
            }
            NepCard {
                SectionTitle("Hiệu năng")
                SwitchRow(
                    "Giảm hiệu ứng chuyển cảnh",
                    s.reduceMotion,
                    { v -> scope.launch { repo.setReduceMotion(v) } },
                    description = "Nên bật trên máy cũ hoặc yếu. Máy RAM thấp được tự động bật.",
                )
            }
        }
    }
}

@Composable
fun ReminderHealthScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val h = remember(refresh) { DeviceHealth.check(context, container.scheduler) }
    var tested by remember { mutableStateOf<Boolean?>(null) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Nhắc lịch không bị chặn", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoBox(
                "Một số điện thoại (đặc biệt Xiaomi, Redmi, POCO chạy HyperOS/MIUI) tự tắt ứng dụng chạy nền để tiết kiệm pin. " +
                    "Khi đó thông báo nhắc có thể đến trễ hoặc không đến. Hãy làm lần lượt các bước dưới đây.",
            )
            CheckItem(
                "1. Cho phép thông báo", h.notificationsAllowed,
                "Bật thông báo cho Nếp Nhà, kể cả kênh \"Nhắc ngày giỗ, sinh nhật, sự kiện\".",
                "Mở cài đặt thông báo",
            ) { DeviceHealth.openNotificationSettings(context) }
            CheckItem(
                "2. Cho phép báo thức chính xác", h.exactAlarmAllowed,
                "Để nhắc đúng giờ đã hẹn.",
                "Mở cài đặt báo thức",
            ) { DeviceHealth.openExactAlarmSettings(context) }
            CheckItem(
                "3. Không giới hạn pin", h.ignoringBatteryOptimization,
                if (h.isXiaomiFamily) "Xiaomi/HyperOS: Tiết kiệm pin → chọn \"Không giới hạn\"."
                else "Chọn \"Không tối ưu hóa\" hoặc \"Không giới hạn\" cho Nếp Nhà.",
                "Mở cài đặt pin",
            ) { DeviceHealth.openBatterySettings(context) }
            if (h.isXiaomiFamily) {
                CheckItem(
                    "4. Bật Tự khởi động (Xiaomi)", null,
                    "Bảo mật → Quyền → Tự khởi động → bật Nếp Nhà. Hệ thống không cho ứng dụng tự kiểm tra mục này.",
                    "Mở Tự khởi động",
                ) { DeviceHealth.openAutostartSettings(context) }
                CheckItem(
                    "5. Khóa ứng dụng trong đa nhiệm", null,
                    "Mở màn đa nhiệm, nhấn giữ thẻ Nếp Nhà và chọn biểu tượng ổ khóa để hệ thống không đóng ứng dụng.",
                    null,
                ) {}
            }
            PrimaryButton("Gửi thông báo thử", { tested = container.scheduler.postTest() }, Modifier.fillMaxWidth())
            tested?.let {
                StatusBanner(
                    if (it) "Đã gửi thông báo thử." else "Không gửi được – thông báo đang bị tắt.",
                    isError = !it,
                )
            }
            InfoBox(
                "Nếp Nhà không thể bảo đảm thông báo đúng giờ nếu hệ điều hành đã chặn quyền hoặc hạn chế chạy nền. " +
                    "Các lần nhắc bị lỡ trong vòng 12 giờ sẽ được hiện bù khi bạn mở lại máy hoặc ứng dụng.",
            )
        }
    }
}

@Composable
private fun CheckItem(title: String, ok: Boolean?, detail: String, action: String?, onAction: () -> Unit) {
    NepCard(framed = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                when (ok) {
                    true -> "Đã bật"
                    false -> "Chưa bật"
                    null -> "Tự kiểm tra"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (ok) {
                    true -> NepColors.Success
                    false -> NepColors.Error
                    null -> NepColors.Wood
                },
            )
        }
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        if (action != null && ok != true) {
            SecondaryButton(action, onAction, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
fun AboutScreen(container: AppContainer, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Giới thiệu", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NepCard {
                Text("NẾP NHÀ – Lịch gia đình & Gia tộc", style = MaterialTheme.typography.titleLarge, color = NepColors.DeepRed)
                Text("Gìn giữ ngày tháng – Kết nối các thế hệ", style = MaterialTheme.typography.bodyMedium)
                Text("Phiên bản ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
            }
            NepCard {
                SectionTitle("Lịch âm")
                Text(
                    "Tính theo thuật toán thiên văn lịch Việt Nam, múi giờ UTC+7 (không dùng lịch Trung Quốc). " +
                        "Đã kiểm thử với các mốc Tết, tháng nhuận và 2.486 tháng âm từ 1900 đến 2100.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            NepCard {
                SectionTitle("Dữ liệu")
                Text(
                    "Dữ liệu gia đình lưu trên điện thoại, ghi an toàn khi ứng dụng bị đóng đột ngột và được đưa vào " +
                        "bản sao lưu của Android (Google) nếu bạn bật sao lưu trên máy.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (container.isBackendConfigured) "Máy chủ gia tộc: đã cấu hình." else "Máy chủ gia tộc: chưa cấu hình.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NepColors.InkSoft,
                )
            }
        }
    }
}
