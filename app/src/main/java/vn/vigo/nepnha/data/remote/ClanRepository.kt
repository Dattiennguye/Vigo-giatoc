package vn.vigo.nepnha.data.remote

import org.json.JSONObject
import java.time.Instant

/** Chuẩn hóa mã người dùng gõ: bỏ khoảng trắng, viết hoa, tự thêm gạch nối (ABCD-EFGH). */
fun normalizeInviteCode(input: String): String {
    val c = input.uppercase().filter { it.isLetterOrDigit() }
    return if (c.length == 8) c.substring(0, 4) + "-" + c.substring(4) else c
}

class ClanRepository(private val api: SupabaseClient) {

    private fun JSONObject.putOpt2(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)

    suspend fun myMemberships(): List<Membership> = api.rpcArray("my_memberships").objects().map(Membership::from)

    suspend fun clan(id: String): ClanInfo? =
        api.select("clans", "select=id,code,name,hometown,description&id=eq.${SupabaseClient.enc(id)}")
            .objects().firstOrNull()?.let(ClanInfo::from)

    suspend fun createClan(name: String, hometown: String?, description: String?): ClanInfo {
        val arr = api.rpcArray(
            "create_clan",
            JSONObject().put("p_name", name).putOpt2("p_hometown", hometown).putOpt2("p_description", description),
        )
        return ClanInfo.from(arr.getJSONObject(0))
    }

    suspend fun previewInvite(code: String): InvitePreview {
        val o = api.rpcArray("preview_invite", JSONObject().put("p_code", normalizeInviteCode(code))).objects().firstOrNull()
            ?: throw ApiException("Mã mời không hợp lệ hoặc đã hết hạn")
        val br = o.optJSONArray("branches")?.objects().orEmpty().map { it.getString("id") to it.optString("name") }
        return InvitePreview(o.optString("clan_name"), o.optString("hometown").takeIf { it.isNotBlank() && it != "null" }, br)
    }

    suspend fun requestJoin(code: String, claimedName: String, note: String?, branchId: String?, relation: String?): String {
        val o = api.rpcArray(
            "request_join",
            JSONObject()
                .put("p_code", normalizeInviteCode(code))
                .put("p_claimed_name", claimedName)
                .putOpt2("p_note", note)
                .putOpt2("p_branch", branchId)
                .putOpt2("p_related_person", null)
                .putOpt2("p_related_relation", relation),
        ).objects().firstOrNull()
        return o?.optString("clan_name").orEmpty()
    }

    suspend fun members(clanId: String): List<ClanMember> =
        api.rpcArray("clan_member_list", JSONObject().put("p_clan", clanId)).objects().map(ClanMember::from)

    /** decision: approve | reject | needs_info | lock | unlock */
    suspend fun decide(memberId: String, decision: String, note: String?) {
        api.rpc("decide_member", JSONObject().put("p_member", memberId).put("p_decision", decision).putOpt2("p_note", note))
    }

    suspend fun setRole(memberId: String, role: ClanRole, branchId: String?) {
        api.rpc(
            "set_member_role",
            JSONObject().put("p_member", memberId).put("p_role", role.key).putOpt2("p_branch", branchId),
        )
    }

    suspend fun invites(clanId: String): List<Invite> =
        api.select(
            "invite_codes",
            "select=id,code_hint,branch_id,expires_at,max_uses,used_count,revoked_at" +
                "&clan_id=eq.${SupabaseClient.enc(clanId)}&order=created_at.desc&limit=50",
        ).objects().map(Invite::from)

    suspend fun createInvite(clanId: String, branchId: String?, expiresHours: Int?, maxUses: Int?): NewInvite {
        val o = api.rpcArray(
            "create_invite",
            JSONObject().put("p_clan", clanId).putOpt2("p_branch", branchId)
                .putOpt2("p_expires_hours", expiresHours).putOpt2("p_max_uses", maxUses),
        ).getJSONObject(0)
        return NewInvite(
            o.getString("invite_id"),
            o.getString("code"),
            if (o.isNull("expires_at")) null else parseTs(o.getString("expires_at")),
        )
    }

    suspend fun revokeInvite(inviteId: String) {
        api.rpc("revoke_invite", JSONObject().put("p_invite", inviteId))
    }

    suspend fun branches(clanId: String): List<Branch> =
        api.select(
            "clan_branches",
            "select=id,name,order_index,parent_branch_id&clan_id=eq.${SupabaseClient.enc(clanId)}" +
                "&deleted_at=is.null&order=order_index.asc,name.asc",
        ).objects().map(Branch::from)

    suspend fun createBranch(clanId: String, name: String, orderIndex: Int, parentId: String?): Branch =
        Branch.from(
            api.insert(
                "clan_branches",
                JSONObject().put("clan_id", clanId).put("name", name).put("order_index", orderIndex)
                    .putOpt2("parent_branch_id", parentId),
            ),
        )

    suspend fun activity(clanId: String, before: Instant?): List<Activity> =
        api.rpcArray(
            "clan_activity",
            JSONObject().put("p_clan", clanId).putOpt2("p_before", before?.toString()).put("p_limit", 50),
        ).objects().map(Activity::from)

    suspend fun leave(clanId: String) {
        api.rpc("leave_clan", JSONObject().put("p_clan", clanId))
    }

    suspend fun setDisplayName(name: String) {
        api.rpc("set_display_name", JSONObject().put("p_name", name))
    }
}
