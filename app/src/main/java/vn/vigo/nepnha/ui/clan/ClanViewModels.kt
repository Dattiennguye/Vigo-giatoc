package vn.vigo.nepnha.ui.clan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.vigo.nepnha.data.remote.Activity
import vn.vigo.nepnha.data.remote.Branch
import vn.vigo.nepnha.data.remote.ClanInfo
import vn.vigo.nepnha.data.remote.ClanMember
import vn.vigo.nepnha.data.remote.ClanRepository
import vn.vigo.nepnha.data.remote.ClanRole
import vn.vigo.nepnha.data.remote.Invite
import vn.vigo.nepnha.data.remote.InvitePreview
import vn.vigo.nepnha.data.remote.MemberStatus
import vn.vigo.nepnha.data.remote.Membership
import vn.vigo.nepnha.data.remote.NewInvite
import vn.vigo.nepnha.data.remote.SupabaseClient

/** Trạng thái tải dữ liệu chung: đang tải / lỗi / có dữ liệu. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Data<T>(val value: T) : Load<T>
}

private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Có lỗi xảy ra. Vui lòng thử lại."

// ======================================================================= Đăng nhập

enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT }

data class AuthForm(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class AuthViewModel(private val api: SupabaseClient) : ViewModel() {
    private val _f = MutableStateFlow(AuthForm())
    val form: StateFlow<AuthForm> = _f.asStateFlow()

    fun edit(b: AuthForm.() -> AuthForm) = _f.update { it.b().copy(error = null) }

    fun submit() {
        val f = _f.value
        if (f.busy) return
        val email = f.email.trim()
        val err = when {
            !email.contains('@') || email.length < 5 -> "Vui lòng nhập email hợp lệ"
            f.mode != AuthMode.FORGOT && f.password.length < 6 -> "Mật khẩu cần ít nhất 6 ký tự"
            f.mode == AuthMode.SIGN_UP && f.displayName.isBlank() -> "Vui lòng nhập họ tên"
            else -> null
        }
        if (err != null) {
            _f.update { it.copy(error = err) }
            return
        }
        _f.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                when (f.mode) {
                    AuthMode.SIGN_IN -> api.signIn(email, f.password)
                    AuthMode.SIGN_UP -> {
                        val signedIn = api.signUp(email, f.password, f.displayName.trim())
                        if (!signedIn) {
                            _f.update {
                                it.copy(
                                    mode = AuthMode.SIGN_IN,
                                    info = "Đã tạo tài khoản. Hãy mở email xác nhận rồi quay lại đăng nhập.",
                                )
                            }
                        }
                    }
                    AuthMode.FORGOT -> {
                        api.sendPasswordReset(email)
                        _f.update { it.copy(mode = AuthMode.SIGN_IN, info = "Đã gửi email đặt lại mật khẩu (nếu email đã đăng ký).") }
                    }
                }
            } catch (e: Exception) {
                _f.update { it.copy(error = e.userMessage()) }
            } finally {
                _f.update { it.copy(busy = false, password = if (it.mode == AuthMode.SIGN_IN && it.info != null) "" else it.password) }
            }
        }
    }
}

// ======================================================================= Danh sách gia tộc của tôi

class MyClansViewModel(private val repo: ClanRepository, private val api: SupabaseClient) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<Membership>>>(Load.Loading)
    val state: StateFlow<Load<List<Membership>>> = _state.asStateFlow()

    fun refresh() {
        if (api.session.value == null) return
        viewModelScope.launch {
            if (_state.value !is Load.Data) _state.value = Load.Loading
            _state.value = try {
                Load.Data(repo.myMemberships())
            } catch (e: Exception) {
                Load.Error(e.userMessage())
            }
        }
    }

    fun signOut() = viewModelScope.launch {
        api.signOut()
        _state.value = Load.Loading
    }
}

// ======================================================================= Tạo gia tộc

data class CreateClanForm(
    val name: String = "",
    val hometown: String = "",
    val description: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val created: ClanInfo? = null,
)

class CreateClanViewModel(private val repo: ClanRepository) : ViewModel() {
    private val _f = MutableStateFlow(CreateClanForm())
    val form = _f.asStateFlow()

    fun edit(b: CreateClanForm.() -> CreateClanForm) = _f.update { it.b().copy(error = null) }

    fun submit() {
        val f = _f.value
        if (f.busy) return
        if (f.name.trim().length < 2) {
            _f.update { it.copy(error = "Tên gia tộc cần ít nhất 2 ký tự") }
            return
        }
        _f.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val c = repo.createClan(f.name.trim(), f.hometown.trim().ifBlank { null }, f.description.trim().ifBlank { null })
                _f.update { it.copy(busy = false, created = c) }
            } catch (e: Exception) {
                _f.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}

// ======================================================================= Tham gia bằng mã

data class JoinForm(
    val code: String = "",
    val preview: InvitePreview? = null,
    val branchId: String? = null,
    val claimedName: String = "",
    val relation: String = "",
    val note: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val sentTo: String? = null,
)

class JoinClanViewModel(private val repo: ClanRepository) : ViewModel() {
    private val _f = MutableStateFlow(JoinForm())
    val form = _f.asStateFlow()

    fun edit(b: JoinForm.() -> JoinForm) = _f.update { it.b().copy(error = null) }

    fun check() {
        val f = _f.value
        if (f.busy) return
        if (f.code.count { it.isLetterOrDigit() } < 6) {
            _f.update { it.copy(error = "Mã mời gồm 8 ký tự, ví dụ ABCD-EF23") }
            return
        }
        _f.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val p = repo.previewInvite(f.code)
                _f.update { it.copy(busy = false, preview = p) }
            } catch (e: Exception) {
                _f.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }

    fun send() {
        val f = _f.value
        if (f.busy) return
        if (f.claimedName.isBlank()) {
            _f.update { it.copy(error = "Vui lòng nhập họ tên của bạn") }
            return
        }
        if (f.relation.isBlank()) {
            _f.update { it.copy(error = "Vui lòng cho biết bạn là người thân của ai trong gia tộc") }
            return
        }
        _f.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val name = repo.requestJoin(f.code, f.claimedName.trim(), f.note.trim().ifBlank { null }, f.branchId, f.relation.trim())
                _f.update { it.copy(busy = false, sentTo = name.ifBlank { f.preview?.clanName ?: "gia tộc" }) }
            } catch (e: Exception) {
                _f.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}

// ======================================================================= Chi tiết gia tộc

enum class ClanSection(val label: String) {
    MEMBERS("Thành viên"),
    REQUESTS("Chờ duyệt"),
    INVITES("Mã mời"),
    BRANCHES("Chi nhánh"),
    ACTIVITY("Lịch sử"),
}

data class ClanDetail(
    val info: ClanInfo,
    val me: Membership,
    val members: List<ClanMember>,
    val branches: List<Branch>,
) {
    val myRank: Int get() = me.role.rank
    val isAdmin: Boolean get() = myRank >= ClanRole.ADMIN.rank
    val canManageMembers: Boolean get() = isAdmin || me.role == ClanRole.BRANCH_HEAD
    val active: List<ClanMember> get() = members.filter { it.status == MemberStatus.ACTIVE }
    val requests: List<ClanMember> get() = members.filter { it.status == MemberStatus.PENDING || it.status == MemberStatus.NEEDS_INFO }
    val locked: List<ClanMember> get() = members.filter { it.status == MemberStatus.LOCKED }

    fun sections(): List<ClanSection> = buildList {
        add(ClanSection.MEMBERS)
        if (canManageMembers) {
            add(ClanSection.REQUESTS)
            add(ClanSection.INVITES)
        }
        add(ClanSection.BRANCHES)
        if (isAdmin) add(ClanSection.ACTIVITY)
    }

    /** Vai trò mình được phép gán: thấp hơn hạng của chính mình (máy chủ kiểm tra lại). */
    fun assignableRoles(): List<ClanRole> = if (!isAdmin) emptyList() else ClanRole.entries.filter { it.rank < myRank }
}

class ClanDetailViewModel(private val repo: ClanRepository, private val clanId: String) : ViewModel() {
    private val _state = MutableStateFlow<Load<ClanDetail>>(Load.Loading)
    val state = _state.asStateFlow()

    private val _section = MutableStateFlow(ClanSection.MEMBERS)
    val section = _section.asStateFlow()

    private val _invites = MutableStateFlow<Load<List<Invite>>>(Load.Loading)
    val invites = _invites.asStateFlow()

    private val _activity = MutableStateFlow<Load<List<Activity>>>(Load.Loading)
    val activity = _activity.asStateFlow()

    /** Mã mời vừa tạo – chỉ hiển thị MỘT lần (máy chủ chỉ lưu bản băm). */
    private val _newInvite = MutableStateFlow<NewInvite?>(null)
    val newInvite = _newInvite.asStateFlow()

    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    val message = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _left = MutableStateFlow(false)
    val left = _left.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val me = repo.myMemberships().firstOrNull { it.clanId == clanId && it.status == MemberStatus.ACTIVE }
                    ?: throw IllegalStateException("Bạn chưa là thành viên đã được duyệt của gia tộc này")
                val info = repo.clan(clanId) ?: throw IllegalStateException("Không tìm thấy gia tộc")
                _state.value = Load.Data(ClanDetail(info, me, repo.members(clanId), repo.branches(clanId)))
                when (_section.value) {
                    ClanSection.INVITES -> loadInvites()
                    ClanSection.ACTIVITY -> loadActivity()
                    else -> Unit
                }
            } catch (e: Exception) {
                if (_state.value !is Load.Data) _state.value = Load.Error(e.userMessage())
                else _message.value = e.userMessage() to true
            }
        }
    }

    fun select(s: ClanSection) {
        _section.value = s
        when (s) {
            ClanSection.INVITES -> loadInvites()
            ClanSection.ACTIVITY -> loadActivity()
            else -> Unit
        }
    }

    private fun loadInvites() = viewModelScope.launch {
        _invites.value = try {
            Load.Data(repo.invites(clanId))
        } catch (e: Exception) {
            Load.Error(e.userMessage())
        }
    }

    private fun loadActivity() = viewModelScope.launch {
        _activity.value = try {
            Load.Data(repo.activity(clanId, null))
        } catch (e: Exception) {
            Load.Error(e.userMessage())
        }
    }

    fun loadMoreActivity() = viewModelScope.launch {
        val cur = (_activity.value as? Load.Data)?.value ?: return@launch
        val last = cur.lastOrNull()?.at ?: return@launch
        try {
            val more = repo.activity(clanId, last)
            _activity.value = Load.Data(cur + more)
            if (more.isEmpty()) _message.value = "Đã hiển thị toàn bộ lịch sử" to false
        } catch (e: Exception) {
            _message.value = e.userMessage() to true
        }
    }

    private fun act(success: String, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
                _message.value = success to false
                refresh()
            } catch (e: Exception) {
                _message.value = e.userMessage() to true
            } finally {
                _busy.value = false
            }
        }
    }

    fun decide(m: ClanMember, decision: String, note: String?) = act(
        when (decision) {
            "approve" -> "Đã chấp thuận ${m.displayName}"
            "reject" -> "Đã từ chối yêu cầu"
            "needs_info" -> "Đã yêu cầu bổ sung thông tin"
            "lock" -> "Đã khóa thành viên"
            else -> "Đã mở khóa thành viên"
        },
    ) { repo.decide(m.memberId, decision, note?.trim()?.ifBlank { null }) }

    fun setRole(m: ClanMember, role: ClanRole, branchId: String?) =
        act("Đã đổi vai trò của ${m.displayName} thành ${role.label}") { repo.setRole(m.memberId, role, branchId) }

    fun createInvite(branchId: String?, hours: Int?, maxUses: Int?) = act("Đã tạo mã mời") {
        _newInvite.value = repo.createInvite(clanId, branchId, hours, maxUses)
        _section.value = ClanSection.INVITES
        loadInvites()
    }

    fun revokeInvite(i: Invite) = act("Đã thu hồi mã …${i.hint}") {
        repo.revokeInvite(i.id)
        loadInvites()
    }

    fun createBranch(name: String, order: Int, parentId: String?) {
        if (name.isBlank()) {
            _message.value = "Vui lòng nhập tên chi/nhánh" to true
            return
        }
        act("Đã tạo \"${name.trim()}\"") { repo.createBranch(clanId, name.trim(), order, parentId) }
    }

    fun leave() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                repo.leave(clanId)
                _left.value = true
            } catch (e: Exception) {
                _message.value = e.userMessage() to true
            } finally {
                _busy.value = false
            }
        }
    }

    fun dismissInvite() {
        _newInvite.value = null
    }

    fun dismissMessage() {
        _message.value = null
    }
}
