package vn.vigo.nepnha.ui.clan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.vigo.nepnha.data.remote.ClanRepository
import vn.vigo.nepnha.ui.components.ChoiceChips
import vn.vigo.nepnha.ui.components.InfoBox
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.NepTextField
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SecondaryButton
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.components.nepViewModel
import vn.vigo.nepnha.ui.theme.NepColors

@Composable
fun ClanCreateScreen(repo: ClanRepository, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val vm = nepViewModel(key = "create-clan") { CreateClanViewModel(repo) }
    val f by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(f.created) { f.created?.let { onCreated(it.id) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tạo gia tộc", onBack = onBack)
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoBox("Bạn sẽ là Chủ gia tộc. Hệ thống cấp một mã định danh riêng; sau đó bạn tạo mã mời để mời người thân.")
            NepTextField("Tên gia tộc (ví dụ: Họ Nguyễn Văn – Hành Thiện)", f.name, { v -> vm.edit { copy(name = v.take(120)) } })
            NepTextField("Quê quán", f.hometown, { v -> vm.edit { copy(hometown = v.take(200)) } })
            NepTextField("Giới thiệu ngắn", f.description, { v -> vm.edit { copy(description = v.take(1000)) } }, singleLine = false)
            f.error?.let { StatusBanner(it, isError = true) }
            PrimaryButton(if (f.busy) "Đang tạo…" else "Tạo gia tộc", vm::submit, Modifier.fillMaxWidth(), enabled = !f.busy)
        }
    }
}

@Composable
fun ClanJoinScreen(repo: ClanRepository, onBack: () -> Unit) {
    val vm = nepViewModel(key = "join-clan") { JoinClanViewModel(repo) }
    val f by vm.form.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tham gia gia tộc", onBack = onBack)
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sent = f.sentTo
            if (sent != null) {
                StatusBanner("Đã gửi yêu cầu tham gia \"$sent\".", isError = false)
                InfoBox(
                    "Người quản lý sẽ xét duyệt. Bạn sẽ nhận thông báo khi có kết quả; trước khi được duyệt, " +
                        "bạn chưa xem được dữ liệu riêng tư của gia tộc.",
                )
                PrimaryButton("Xong", onBack, Modifier.fillMaxWidth())
                return@Column
            }
            NepTextField(
                "Mã mời (ví dụ ABCD-EF23)",
                f.code,
                { v -> vm.edit { copy(code = v.uppercase().take(12), preview = null, branchId = null) } },
            )
            val p = f.preview
            if (p == null) {
                f.error?.let { StatusBanner(it, isError = true) }
                PrimaryButton(if (f.busy) "Đang kiểm tra…" else "Kiểm tra mã", vm::check, Modifier.fillMaxWidth(), enabled = !f.busy)
                InfoBox("Mã mời không phải mật khẩu: mã có hạn dùng và người quản lý có thể thu hồi bất cứ lúc nào.")
                return@Column
            }
            NepCard {
                Text("Gia tộc", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
                Text(p.clanName, style = MaterialTheme.typography.titleLarge, color = NepColors.DeepRed)
                p.hometown?.let { Text("Quê quán: $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (p.branches.isNotEmpty()) {
                SectionTitle("Bạn thuộc chi/nhánh nào?")
                ChoiceChips(
                    listOf<String?>(null) + p.branches.map { it.first },
                    { it == f.branchId },
                    { id -> if (id == null) "Chưa rõ" else p.branches.first { it.first == id }.second },
                    { id -> vm.edit { copy(branchId = id) } },
                )
            }
            NepTextField("Họ và tên của bạn", f.claimedName, { v -> vm.edit { copy(claimedName = v.take(120)) } })
            NepTextField(
                "Bạn là người thân của ai? (ví dụ: con trai ông Nguyễn Văn An)",
                f.relation,
                { v -> vm.edit { copy(relation = v.take(200)) } },
            )
            NepTextField("Lời nhắn cho người quản lý", f.note, { v -> vm.edit { copy(note = v.take(500)) } }, singleLine = false)
            f.error?.let { StatusBanner(it, isError = true) }
            PrimaryButton(if (f.busy) "Đang gửi…" else "Gửi yêu cầu tham gia", vm::send, Modifier.fillMaxWidth(), enabled = !f.busy)
            SecondaryButton("Nhập mã khác", { vm.edit { copy(preview = null, code = "") } }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}
