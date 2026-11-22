package vn.vigo.nepnha.ui.clan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.ui.components.InfoBox
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.OrnamentDivider
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.theme.NepColors

/**
 * Tab Gia tộc – bản 0.1: CHƯA có chức năng trực tuyến trong ứng dụng.
 * Màn hình nói rõ tình trạng thay vì hiện nút giả.
 */
@Composable
fun ClanScreen(container: AppContainer) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Gia tộc", subtitle = "Gia phả, chi nhánh, thành viên")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (container.isBackendConfigured) {
                StatusBanner("Đã cấu hình địa chỉ máy chủ gia tộc.", isError = false)
            } else {
                StatusBanner("Bản này chưa kết nối máy chủ gia tộc.", isError = true)
            }
            NepCard {
                SectionTitle("Đang hoàn thiện")
                Text(
                    "Gia tộc trực tuyến (tạo gia tộc, mã mời, xét duyệt thành viên, gia phả nhiều thế hệ, " +
                        "chi nhánh, phân quyền và đồng bộ nhiều điện thoại) sẽ có ở bản cập nhật tiếp theo.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OrnamentDivider(Modifier.padding(vertical = 8.dp))
                Text("Đã sẵn sàng phía sau:", style = MaterialTheme.typography.titleSmall, color = NepColors.Wood)
                listOf(
                    "Cơ sở dữ liệu gia tộc trên máy chủ, tách tài khoản với hồ sơ gia phả",
                    "Phân quyền kiểm tra ở máy chủ cho từng bản ghi (8 cấp quyền)",
                    "Mã mời có hạn dùng, giới hạn lượt, thu hồi được",
                    "Bộ suy vai vế theo huyết thống: ông bà, cô dì chú bác, anh chị em họ, con nuôi, thông gia…",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            InfoBox(
                "Trong lúc chờ, bạn vẫn dùng đầy đủ phần Gia đình: lịch âm dương, ngày giỗ, sinh nhật, " +
                    "nhắc trước nhiều mốc và việc gia đình. Dữ liệu được lưu trên máy và không bị mất khi cập nhật.",
            )
        }
    }
}
