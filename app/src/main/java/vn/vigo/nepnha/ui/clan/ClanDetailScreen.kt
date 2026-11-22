package vn.vigo.nepnha.ui.clan

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.vigo.nepnha.data.remote.Branch
import vn.vigo.nepnha.data.remote.ClanMember
import vn.vigo.nepnha.data.remote.ClanRepository
import vn.vigo.nepnha.data.remote.ClanRole
import vn.vigo.nepnha.data.remote.Invite
import vn.vigo.nepnha.data.remote.MemberStatus
import vn.vigo.nepnha.data.remote.NewInvite
import vn.vigo.nepnha.ui.components.ChoiceChips
import vn.vigo.nepnha.ui.components.EmptyState
import vn.vigo.nepnha.ui.components.ErrorState
import vn.vigo.nepnha.ui.components.InfoBox
import vn.vigo.nepnha.ui.components.LinkButton
import vn.vigo.nepnha.ui.components.LoadingState
import vn.vigo.nepnha.ui.components.NepCard
import vn.vigo.nepnha.ui.components.NepTextField
import vn.vigo.nepnha.ui.components.NumberStepper
import vn.vigo.nepnha.ui.components.PrimaryButton
import vn.vigo.nepnha.ui.components.ScreenHeader
import vn.vigo.nepnha.ui.components.SecondaryButton
import vn.vigo.nepnha.ui.components.SectionTitle
import vn.vigo.nepnha.ui.components.StatusBanner
import vn.vigo.nepnha.ui.components.nepViewModel
import vn.vigo.nepnha.ui.theme.NepColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TS = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.systemDefault())

@Composable
fun ClanDetailScreen(repo: ClanRepository, clanId: String, onBack: () -> Unit) {
    val vm = nepViewModel(key = "clan-$clanId") { ClanDetailViewModel(repo, clanId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val section by vm.section.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val newInvite by vm.newInvite.collectAsStateWithLifecycle()
    val left by vm.left.collectAsStateWithLifecycle()
    var actingOn by remember { mutableStateOf<ClanMember?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(left) { if (left) onBack() }

    val s = state
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            (s as? Load.Data)?.value?.info?.name ?: "Gia tộc",
            onBack = onBack,
            subtitle = (s as? Load.Data)?.value?.let { "Mã ${it.info.code} · ${it.me.role.label}" },
            action = { LinkButton("Tải lại", vm::refresh) },
        )
        when (s) {
            Load.Loading -> LoadingState()
            is Load.Error -> ErrorState(s.message, onRetry = vm::refresh)
            is Load.Data -> {
                val d = s.value
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "sections") {
                        ChoiceChips(
                            d.sections(),
                            { it == section },
                            { if (it == ClanSection.REQUESTS && d.requests.isNotEmpty()) "${it.label} (${d.requests.size})" else it.label },
                            vm::select,
                        )
                    }
                    message?.let { (text, isError) ->
                        item(key = "msg") {
                            Column {
                                StatusBanner(text, isError)
                                LinkButton("Đóng thông báo", vm::dismissMessage)
                            }
                        }
                    }
                    when (section) {
                        ClanSection.MEMBERS -> membersSection(d, onTap = { actingOn = it })
                        ClanSection.REQUESTS -> requestsSection(d, busy, vm)
                        ClanSection.INVITES -> item(key = "inv") { InvitesSection(d, vm) }
                        ClanSection.BRANCHES -> item(key = "br") { BranchesSection(d, vm) }
                        ClanSection.ACTIVITY -> item(key = "act") { ActivitySection(vm) }
                    }
                    if (d.me.role != ClanRole.OWNER) {
                        item(key = "leave") {
                            SecondaryButton("Rời gia tộc", { confirmLeave = true }, Modifier.fillMaxWidth().padding(top = 16.dp))
                        }
                    }
                }
                actingOn?.let { m -> MemberDialog(m, d, busy, vm, onClose = { actingOn = null }) }
            }
        }
    }

    newInvite?.let { NewInviteDialog(it, onClose = vm::dismissInvite) }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Rời gia tộc?") },
            text = { Text("Bạn sẽ không xem được dữ liệu của gia tộc nữa. Muốn quay lại cần mã mời mới và được duyệt lại.") },
            confirmButton = { TextButton({ confirmLeave = false; vm.leave() }) { Text("Rời gia tộc", color = NepColors.Error) } },
            dismissButton = { TextButton({ confirmLeave = false }) { Text("Ở lại") } },
        )
    }
}

// --------------------------------------------------------------------------- Thành viên

private fun LazyListScope.membersSection(d: ClanDetail, onTap: (ClanMember) -> Unit) {
    item(key = "m-title") { SectionTitle("Đang hoạt động (${d.active.size})") }
    items(d.active, key = { "m-" + it.memberId }, contentType = { "member" }) { m ->
        MemberRow(m, isMe = m.memberId == d.me.memberId, canAct = canActOn(d, m), onTap = { onTap(m) })
    }
    if (d.locked.isNotEmpty()) {
        item(key = "l-title") { SectionTitle("Bị khóa (${d.locked.size})") }
        items(d.locked, key = { "l-" + it.memberId }, contentType = { "member" }) { m ->
            MemberRow(m, isMe = false, canAct = canActOn(d, m), onTap = { onTap(m) })
        }
    }
}

/** Chỉ thao tác với người có hạng thấp hơn mình (máy chủ cũng kiểm tra điều này). */
private fun canActOn(d: ClanDetail, m: ClanMember): Boolean =
    d.canManageMembers && m.memberId != d.me.memberId && m.role.rank < d.myRank

@Composable
private fun MemberRow(m: ClanMember, isMe: Boolean, canAct: Boolean, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .let { if (canAct) it.clickable(onClickLabel = "Quản lý ${m.displayName}", role = Role.Button, onClick = onTap) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.displayName + if (isMe) " (bạn)" else "", style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(m.role.label, m.branchName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NepColors.InkSoft,
            )
        }
        if (canAct) Text("Quản lý", style = MaterialTheme.typography.labelMedium, color = NepColors.DeepRed)
    }
    HorizontalDivider(color = NepColors.BronzeLight)
}

@Composable
private fun MemberDialog(m: ClanMember, d: ClanDetail, busy: Boolean, vm: ClanDetailViewModel, onClose: () -> Unit) {
    var role by remember { mutableStateOf(m.role) }
    var branchId by remember { mutableStateOf(m.branchId) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(m.displayName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vai trò hiện tại: ${m.role.label}", style = MaterialTheme.typography.bodyMedium)
                val roles = d.assignableRoles()
                if (roles.isNotEmpty() && m.status == MemberStatus.ACTIVE) {
                    Text("Đổi vai trò", style = MaterialTheme.typography.titleSmall)
                    ChoiceChips(roles, { it == role }, { it.label }, { role = it })
                    if (role == ClanRole.BRANCH_HEAD) {
                        if (d.branches.isEmpty()) {
                            Text("Cần tạo chi/nhánh trước khi giao Trưởng chi.", color = NepColors.Error)
                        } else {
                            ChoiceChips(d.branches.map { it.id }, { it == branchId }, { id -> d.branches.first { it.id == id }.name }, { branchId = it })
                        }
                    }
                    PrimaryButton(
                        "Lưu vai trò",
                        { vm.setRole(m, role, branchId); onClose() },
                        Modifier.fillMaxWidth(),
                        enabled = !busy && (role != m.role || branchId != m.branchId) &&
                            (role != ClanRole.BRANCH_HEAD || branchId != null),
                    )
                }
                if (m.status == MemberStatus.LOCKED) {
                    SecondaryButton("Mở khóa", { vm.decide(m, "unlock", null); onClose() }, Modifier.fillMaxWidth(), enabled = !busy)
                } else {
                    SecondaryButton("Khóa thành viên", { vm.decide(m, "lock", null); onClose() }, Modifier.fillMaxWidth(), enabled = !busy)
                    Text(
                        "Khóa sẽ thu hồi ngay quyền xem và sửa dữ liệu gia tộc của người này.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NepColors.InkSoft,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Đóng") } },
    )
}

// --------------------------------------------------------------------------- Chờ duyệt

private fun LazyListScope.requestsSection(d: ClanDetail, busy: Boolean, vm: ClanDetailViewModel) {
    if (d.requests.isEmpty()) {
        item(key = "r-empty") { EmptyState("Không có yêu cầu nào", "Khi có người nhập mã mời, yêu cầu sẽ hiện ở đây để bạn xét duyệt.") }
        return
    }
    items(d.requests, key = { "r-" + it.memberId }, contentType = { "request" }) { m -> RequestCard(m, busy, vm) }
}

@Composable
private fun RequestCard(m: ClanMember, busy: Boolean, vm: ClanDetailViewModel) {
    var note by rememberSaveable(m.memberId) { mutableStateOf("") }
    NepCard(framed = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.claimedName ?: m.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(m.status.label, style = MaterialTheme.typography.labelMedium, color = NepColors.Wood)
        }
        m.relatedRelation?.let { Text("Quan hệ: $it", style = MaterialTheme.typography.bodyMedium) }
        m.branchName?.let { Text("Chi/nhánh: $it", style = MaterialTheme.typography.bodyMedium) }
        m.joinNote?.let { Text("Lời nhắn: $it", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft) }
        m.requestedAt?.let { Text("Gửi lúc ${TS.format(it)}", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft) }
        NepTextField("Ghi chú gửi người này (không bắt buộc)", note, { note = it.take(300) }, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            PrimaryButton("Chấp thuận", { vm.decide(m, "approve", note) }, Modifier.weight(1f), enabled = !busy)
            SecondaryButton("Từ chối", { vm.decide(m, "reject", note) }, Modifier.weight(1f), enabled = !busy)
        }
        SecondaryButton("Yêu cầu bổ sung thông tin", { vm.decide(m, "needs_info", note) }, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = !busy)
    }
}

// --------------------------------------------------------------------------- Mã mời

private val EXPIRY = listOf(24 to "1 ngày", 168 to "7 ngày", 720 to "30 ngày", null to "Không hạn")
private val USES = listOf(1 to "1 người", 5 to "5 người", 20 to "20 người", null to "Không giới hạn")

@Composable
private fun InvitesSection(d: ClanDetail, vm: ClanDetailViewModel) {
    val invites by vm.invites.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var hours by rememberSaveable { mutableStateOf<Int?>(168) }
    var uses by rememberSaveable { mutableStateOf<Int?>(20) }
    var branchId by rememberSaveable { mutableStateOf<String?>(if (d.isAdmin) null else d.me.branchId) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NepCard {
            SectionTitle("Tạo mã mời")
            Text("Hiệu lực", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
            ChoiceChips(EXPIRY.map { it.first }, { it == hours }, { h -> EXPIRY.first { it.first == h }.second }, { hours = it })
            Text("Số người được dùng", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
            ChoiceChips(USES.map { it.first }, { it == uses }, { u -> USES.first { it.first == u }.second }, { uses = it })
            if (d.branches.isNotEmpty()) {
                Text("Mời vào chi/nhánh", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
                val options: List<String?> = (if (d.isAdmin) listOf<String?>(null) else emptyList()) + d.branches.map { it.id }
                ChoiceChips(options, { it == branchId }, { id -> if (id == null) "Toàn gia tộc" else d.branches.first { it.id == id }.name }, { branchId = it })
            }
            PrimaryButton("Tạo mã mời", { vm.createInvite(branchId, hours, uses) }, Modifier.fillMaxWidth(), enabled = !busy)
            InfoBox("Vì lý do bảo mật, mã chỉ hiện đầy đủ MỘT lần ngay sau khi tạo. Hãy gửi ngay cho người thân.")
        }
        SectionTitle("Mã đã tạo")
        when (val s = invites) {
            Load.Loading -> LoadingState()
            is Load.Error -> ErrorState(s.message)
            is Load.Data -> if (s.value.isEmpty()) {
                Text("Chưa có mã mời nào.", style = MaterialTheme.typography.bodyMedium)
            } else {
                s.value.forEach { InviteRow(it, d.branches, busy, onRevoke = { vm.revokeInvite(it) }) }
            }
        }
    }
}

@Composable
private fun InviteRow(i: Invite, branches: List<Branch>, busy: Boolean, onRevoke: () -> Unit) {
    val usable = i.isUsable()
    NepCard(framed = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mã …${i.hint}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                when {
                    i.revoked -> "Đã thu hồi"
                    usable -> "Đang dùng được"
                    else -> "Hết hạn/hết lượt"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (usable) NepColors.Success else NepColors.InkSoft,
            )
        }
        Text(
            listOfNotNull(
                "Đã dùng ${i.usedCount}" + (i.maxUses?.let { "/$it" } ?: ""),
                i.expiresAt?.let { "hết hạn ${TS.format(it)}" } ?: "không hạn",
                i.branchId?.let { id -> branches.firstOrNull { it.id == id }?.name },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = NepColors.InkSoft,
        )
        if (usable) LinkButton(if (busy) "Đang xử lý…" else "Thu hồi mã này", onRevoke)
    }
}

@Composable
private fun NewInviteDialog(inv: NewInvite, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val shareText = "Mời bạn tham gia gia tộc trên ứng dụng Nếp Nhà. Mở tab Gia tộc → Nhập mã mời: ${inv.code}" +
        (inv.expiresAt?.let { " (hiệu lực đến ${TS.format(it)})" } ?: "")
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Mã mời mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    inv.code,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = NepColors.DeepRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Mã này chỉ hiện một lần. Hãy sao chép hoặc chia sẻ ngay.", style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(if (copied) "Đã sao chép" else "Sao chép mã", {
                    clipboard.setText(AnnotatedString(inv.code))
                    copied = true
                }, Modifier.fillMaxWidth())
                PrimaryButton("Gửi qua Zalo, Messenger…", {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, "Gửi mã mời").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClose) { Text("Tôi đã lưu mã") } },
    )
}

// --------------------------------------------------------------------------- Chi nhánh

@Composable
private fun BranchesSection(d: ClanDetail, vm: ClanDetailViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableIntStateOf(d.branches.size + 1) }
    var parent by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBox("Thứ tự chi (chi trưởng, chi hai…) khác với đời trong gia phả. Trưởng chi chỉ quản lý chi được giao.")
        if (d.branches.isEmpty()) {
            Text("Gia tộc chưa chia chi/nhánh.", style = MaterialTheme.typography.bodyMedium)
        } else {
            NepCard(framed = false) {
                orderedTree(d.branches).forEach { (b, depth) ->
                    Text(
                        "  ".repeat(depth) + (if (depth > 0) "└ " else "") + "Chi ${b.orderIndex}: ${b.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = (depth * 12).dp),
                    )
                }
            }
        }
        if (d.isAdmin) {
            NepCard {
                SectionTitle("Thêm chi/nhánh")
                NepTextField("Tên (ví dụ: Chi trưởng – ông Nguyễn Văn Cả)", name, { name = it.take(120) })
                NumberStepper("Thứ tự chi", order, 1..50, { order = it })
                if (d.branches.isNotEmpty()) {
                    Text("Thuộc chi", style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
                    ChoiceChips(
                        listOf<String?>(null) + d.branches.map { it.id },
                        { it == parent },
                        { id -> if (id == null) "Không (chi gốc)" else d.branches.first { it.id == id }.name },
                        { parent = it },
                    )
                }
                PrimaryButton("Thêm", {
                    vm.createBranch(name, order, parent)
                    name = ""
                }, Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank())
            }
        }
    }
}

/** Sắp xếp chi theo cây (cha trước con), kèm độ sâu để thụt lề. */
private fun orderedTree(all: List<Branch>): List<Pair<Branch, Int>> {
    val byParent = all.groupBy { it.parentId }
    val ids = all.map { it.id }.toSet()
    val out = ArrayList<Pair<Branch, Int>>()
    fun walk(parent: String?, depth: Int) {
        if (depth > 10) return
        byParent[parent].orEmpty().sortedWith(compareBy({ it.orderIndex }, { it.name })).forEach {
            out += it to depth
            walk(it.id, depth + 1)
        }
    }
    walk(null, 0)
    // Chi có cha không tồn tại (đã xóa) vẫn được hiển thị
    all.filter { it.parentId != null && it.parentId !in ids }.forEach { out += it to 0 }
    return out
}

// --------------------------------------------------------------------------- Lịch sử

@Composable
private fun ActivitySection(vm: ClanDetailViewModel) {
    val activity by vm.activity.collectAsStateWithLifecycle()
    when (val s = activity) {
        Load.Loading -> LoadingState()
        is Load.Error -> ErrorState(s.message)
        is Load.Data -> Column {
            if (s.value.isEmpty()) Text("Chưa có hoạt động.", style = MaterialTheme.typography.bodyMedium)
            s.value.forEach { a ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("${a.actorName}: ${a.description}", style = MaterialTheme.typography.bodyLarge)
                    a.at?.let { Text(TS.format(it), style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft) }
                }
                HorizontalDivider(color = NepColors.BronzeLight)
            }
            if (s.value.size >= 50) LinkButton("Xem thêm", { vm.loadMoreActivity() })
        }
    }
    Column(Modifier.navigationBarsPadding()) {}
}
