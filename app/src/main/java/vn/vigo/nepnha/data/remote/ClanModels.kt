package vn.vigo.nepnha.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/** Vai trò trong gia tộc – độc lập với vai vế huyết thống. */
enum class ClanRole(val key: String, val label: String, val rank: Int) {
    OWNER("owner", "Chủ gia tộc", 100),
    PATRIARCH("patriarch", "Trưởng tộc", 90),
    VICE_PATRIARCH("vice_patriarch", "Phó trưởng tộc", 80),
    ADMIN("admin", "Quản trị viên", 70),
    BRANCH_HEAD("branch_head", "Trưởng chi", 50),
    GENEALOGY_MANAGER("genealogy_manager", "Quản lý gia phả", 40),
    MEMBER("member", "Thành viên", 10),
    GUEST("guest", "Khách được mời", 1),
    ;

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: MEMBER
    }
}

enum class MemberStatus(val key: String, val label: String) {
    PENDING("pending", "Chờ duyệt"),
    NEEDS_INFO("needs_info", "Cần bổ sung"),
    ACTIVE("active", "Đang hoạt động"),
    REJECTED("rejected", "Bị từ chối"),
    LOCKED("locked", "Bị khóa"),
    LEFT("left", "Đã rời"),
    ;

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: PENDING
    }
}

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }

private fun JSONObject.time(k: String): Instant? = str(k)?.let { parseTs(it) }

/** PostgREST trả dạng "2026-11-22T10:00:00.123456+00:00". */
fun parseTs(s: String): Instant? {
    var t = s.trim().replace(' ', 'T')
    if (Regex("[+-]\\d\\d$").containsMatchIn(t)) t += ":00"
    if (!t.endsWith("Z") && !Regex("[+-]\\d\\d:\\d\\d$").containsMatchIn(t)) t += "Z"
    return runCatching { OffsetDateTime.parse(t).toInstant() }.getOrNull()
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

data class Membership(
    val memberId: String,
    val clanId: String,
    val clanName: String,
    val clanCode: String?,
    val hometown: String?,
    val status: MemberStatus,
    val role: ClanRole,
    val branchId: String?,
    val adminNote: String?,
) {
    companion object {
        fun from(o: JSONObject) = Membership(
            memberId = o.getString("member_id"),
            clanId = o.getString("clan_id"),
            clanName = o.optString("clan_name"),
            clanCode = o.str("clan_code"),
            hometown = o.str("hometown"),
            status = MemberStatus.of(o.str("status")),
            role = ClanRole.of(o.str("role")),
            branchId = o.str("branch_id"),
            adminNote = o.str("admin_note"),
        )
    }
}

data class ClanInfo(
    val id: String,
    val code: String,
    val name: String,
    val hometown: String?,
    val description: String?,
) {
    companion object {
        fun from(o: JSONObject) = ClanInfo(
            id = o.getString("id"),
            code = o.optString("code"),
            name = o.optString("name"),
            hometown = o.str("hometown"),
            description = o.str("description"),
        )
    }
}

data class ClanMember(
    val memberId: String,
    val userId: String,
    val displayName: String,
    val claimedName: String?,
    val status: MemberStatus,
    val role: ClanRole,
    val branchId: String?,
    val branchName: String?,
    val joinNote: String?,
    val relatedRelation: String?,
    val adminNote: String?,
    val requestedAt: Instant?,
) {
    companion object {
        fun from(o: JSONObject) = ClanMember(
            memberId = o.getString("member_id"),
            userId = o.getString("user_id"),
            displayName = o.optString("display_name"),
            claimedName = o.str("claimed_name"),
            status = MemberStatus.of(o.str("status")),
            role = ClanRole.of(o.str("role")),
            branchId = o.str("branch_id"),
            branchName = o.str("branch_name"),
            joinNote = o.str("join_note"),
            relatedRelation = o.str("related_relation"),
            adminNote = o.str("admin_note"),
            requestedAt = o.time("requested_at"),
        )
    }
}

data class Branch(val id: String, val name: String, val orderIndex: Int, val parentId: String?) {
    companion object {
        fun from(o: JSONObject) = Branch(o.getString("id"), o.optString("name"), o.optInt("order_index", 1), o.str("parent_branch_id"))
    }
}

data class Invite(
    val id: String,
    val hint: String,
    val branchId: String?,
    val expiresAt: Instant?,
    val maxUses: Int?,
    val usedCount: Int,
    val revoked: Boolean,
) {
    fun isUsable(now: Instant = Instant.now()): Boolean =
        !revoked && (expiresAt == null || expiresAt.isAfter(now)) && (maxUses == null || usedCount < maxUses)

    companion object {
        fun from(o: JSONObject) = Invite(
            id = o.getString("id"),
            hint = o.optString("code_hint"),
            branchId = o.str("branch_id"),
            expiresAt = o.time("expires_at"),
            maxUses = if (o.isNull("max_uses")) null else o.optInt("max_uses"),
            usedCount = o.optInt("used_count"),
            revoked = !o.isNull("revoked_at"),
        )
    }
}

data class NewInvite(val id: String, val code: String, val expiresAt: Instant?)

data class InvitePreview(val clanName: String, val hometown: String?, val branches: List<Pair<String, String>>)

data class Activity(val id: Long, val at: Instant?, val actorName: String, val action: String, val entity: String) {
    /** Diễn giải thao tác bằng tiếng Việt. */
    val description: String
        get() {
            val what = when (entity) {
                "clans" -> "gia tộc"
                "clan_members" -> "thành viên"
                "clan_branches" -> "chi/nhánh"
                "invite_codes" -> "mã mời"
                "persons" -> "hồ sơ gia phả"
                "parent_links" -> "quan hệ cha mẹ – con"
                "marriages" -> "quan hệ hôn nhân"
                "clan_events" -> "sự kiện"
                "seniority_confirmations" -> "xác nhận thứ bậc"
                "kinship_overrides" -> "cách xưng hô"
                else -> entity
            }
            val verb = when (action) {
                "insert", "create" -> "Thêm"
                "update" -> "Sửa"
                "delete" -> "Xóa"
                "create_invite" -> "Tạo"
                "revoke_invite" -> "Thu hồi"
                "join_attempt" -> "Thử nhập"
                else -> action
            }
            return "$verb $what"
        }

    companion object {
        fun from(o: JSONObject) = Activity(o.getLong("id"), o.time("at"), o.optString("actor_name"), o.optString("action"), o.optString("entity"))
    }
}
