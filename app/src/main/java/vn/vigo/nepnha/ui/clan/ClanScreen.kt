package vn.vigo.nepnha.ui.clan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.data.remote.ClanRepository
import vn.vigo.nepnha.data.remote.MemberStatus
import vn.vigo.nepnha.data.remote.Membership
import vn.vigo.nepnha.data.remote.SupabaseClient
import vn.vigo.nepnha.ui.components.ChoiceChips
import vn.vigo.nepnha.ui.components.EmptyState
import vn.vigo.nepnha.ui.components.ErrorState
import vn.vigo.nepnha.ui.components.InfoBox
import vn.vigo.nepnha.ui.components.LinkButton
import vn.vigo.nepnha.ui.components.LoadingState
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.NepTextField
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SecondaryButton
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.components.nepViewModel
import vn.vigo.nepnha.ui.theme.NepColors

/** Tab Gia tộc: chưa cấu hình máy chủ → giải thích; chưa đăng nhập → đăng nhập/đăng ký; đã đăng nhập → gia tộc của tôi. */
@Composable
fun ClanScreen(
    container: AppContainer,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onOpenClan: (String) -> Unit,
) {
    val api = container.supabase
    val repo = container.clans
    if (api == null || repo == null) {
        NotConfigured()
        return
    }
    val session by api.session.collectAsStateWithLifecycle()
    if (session == null) {
        AuthPanel(api)
    } else {
        MyClans(api, repo, session?.email, onCreate, onJoin, onOpenClan)
    }
}

@Composable
private fun NotConfigured() {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Gia tộc", subtitle = "Gia phả, chi nhánh, thành viên")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusBanner("Bản cài đặt này chưa được kết nối máy chủ gia tộc.", isError = true)
            InfoBox(
                "Người quản lý ứng dụng cần cấu hình máy chủ (xem tài liệu HUONG_DAN_BACKEND). " +
                    "Phần Gia đình vẫn dùng đầy đủ và lưu trên máy.",
            )
        }
    }
}

@Composable
private fun AuthPanel(api: SupabaseClient) {
    val vm = nepViewModel(key = "auth") { AuthViewModel(api) }
    val f by vm.form.collectAsStateWithLifecycle()
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Gia tộc", subtitle = "Đăng nhập để kết nối với gia tộc")
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceChips(
                listOf(AuthMode.SIGN_IN, AuthMode.SIGN_UP),
                { it == f.mode || (it == AuthMode.SIGN_IN && f.mode == AuthMode.FORGOT) },
                { if (it == AuthMode.SIGN_IN) "Đăng nhập" else "Tạo tài khoản" },
                { m -> vm.edit { copy(mode = m, info = null) } },
            )
            f.info?.let { StatusBanner(it, isError = false) }
            if (f.mode == AuthMode.SIGN_UP) {
                NepTextField("Họ và tên", f.displayName, { v -> vm.edit { copy(displayName = v.take(80)) } })
            }
            OutlinedTextField(
                value = f.email,
                onValueChange = { v -> vm.edit { copy(email = v.trim().take(120)) } },
                label = { Text("Email") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.mode != AuthMode.FORGOT) {
                OutlinedTextField(
                    value = f.password,
                    onValueChange = { v -> vm.edit { copy(password = v.take(72)) } },
                    label = { Text("Mật khẩu (ít nhất 6 ký tự)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LinkButton(if (showPassword) "Ẩn mật khẩu" else "Hiện mật khẩu") { showPassword = !showPassword }
            }
            f.error?.let { StatusBanner(it, isError = true) }
            PrimaryButton(
                when {
                    f.busy -> "Đang xử lý…"
                    f.mode == AuthMode.SIGN_UP -> "Tạo tài khoản"
                    f.mode == AuthMode.FORGOT -> "Gửi email đặt lại mật khẩu"
                    else -> "Đăng nhập"
                },
                vm::submit,
                Modifier.fillMaxWidth(),
                enabled = !f.busy,
            )
            if (f.mode == AuthMode.SIGN_IN) {
                LinkButton("Quên mật khẩu?") { vm.edit { copy(mode = AuthMode.FORGOT, info = null) } }
            } else if (f.mode == AuthMode.FORGOT) {
                LinkButton("Quay lại đăng nhập") { vm.edit { copy(mode = AuthMode.SIGN_IN) } }
            }
            InfoBox(
                "Tài khoản dùng để tham gia gia tộc và đồng bộ giữa nhiều điện thoại. " +
                    "Dữ liệu gia tộc chỉ người đã được duyệt mới xem được.",
            )
        }
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NepColors.DeepRed,
    unfocusedBorderColor = NepColors.Bronze,
    focusedLabelColor = NepColors.DeepRed,
    focusedContainerColor = NepColors.WarmWhite,
    unfocusedContainerColor = NepColors.WarmWhite,
)

@Composable
private fun MyClans(
    api: SupabaseClient,
    repo: ClanRepository,
    email: String?,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onOpenClan: (String) -> Unit,
) {
    val vm = nepViewModel(key = "my-clans") { MyClansViewModel(repo, api) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Gia tộc", subtitle = email, action = { LinkButton("Đăng xuất") { vm.signOut() } })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Tạo gia tộc", onCreate, Modifier.weight(1f), icon = Icons.Filled.Add)
                SecondaryButton("Nhập mã mời", onJoin, Modifier.weight(1f), icon = Icons.Filled.Search)
            }
            when (val s = state) {
                Load.Loading -> LoadingState()
                is Load.Error -> ErrorState(s.message, onRetry = vm::refresh)
                is Load.Data -> if (s.value.isEmpty()) {
                    EmptyState(
                        "Bạn chưa thuộc gia tộc nào",
                        "Hãy tạo gia tộc mới nếu bạn là người khởi lập, hoặc nhập mã mời do người quản lý gia tộc gửi.",
                    )
                } else {
                    SectionTitle("Gia tộc của tôi")
                    s.value.forEach { m -> MembershipCard(m, onOpen = { onOpenClan(m.clanId) }, onJoin = onJoin) }
                }
            }
        }
    }
}

@Composable
private fun MembershipCard(m: Membership, onOpen: () -> Unit, onJoin: () -> Unit) {
    val active = m.status == MemberStatus.ACTIVE
    NepCard(onClick = if (active) onOpen else null, onClickLabel = "Mở gia tộc ${m.clanName}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.clanName, style = MaterialTheme.typography.titleLarge, color = NepColors.DeepRed, modifier = Modifier.weight(1f))
            Text(
                if (active) m.role.label else m.status.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) NepColors.Wood else NepColors.Error,
            )
        }
        if (active) {
            m.clanCode?.let { Text("Mã định danh: $it", style = MaterialTheme.typography.bodyMedium) }
            m.hometown?.let { Text("Quê quán: $it", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft) }
        } else {
            Text(
                when (m.status) {
                    MemberStatus.PENDING -> "Yêu cầu của bạn đang chờ người quản lý xét duyệt."
                    MemberStatus.NEEDS_INFO -> "Người quản lý cần bạn bổ sung thông tin."
                    MemberStatus.REJECTED -> "Yêu cầu tham gia đã bị từ chối."
                    MemberStatus.LOCKED -> "Tài khoản của bạn trong gia tộc này đang bị khóa."
                    else -> m.status.label
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            m.adminNote?.let { Text("Lời nhắn: $it", style = MaterialTheme.typography.bodySmall, color = NepColors.Wood) }
            if (m.status == MemberStatus.NEEDS_INFO || m.status == MemberStatus.REJECTED) {
                LinkButton("Gửi lại yêu cầu bằng mã mời", onJoin)
            }
        }
    }
}
