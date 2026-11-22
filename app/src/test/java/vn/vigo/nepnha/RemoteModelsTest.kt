package vn.vigo.nepnha

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.vigo.nepnha.data.remote.ClanRole
import vn.vigo.nepnha.data.remote.Invite
import vn.vigo.nepnha.data.remote.MemberStatus
import vn.vigo.nepnha.data.remote.Membership
import vn.vigo.nepnha.data.remote.normalizeInviteCode
import vn.vigo.nepnha.data.remote.parseTs
import java.time.Instant

class RemoteModelsTest {
    @Test
    fun inviteCodeNormalization() {
        assertEquals("ABCD-EF23", normalizeInviteCode(" abcd ef23 "))
        assertEquals("ABCD-EF23", normalizeInviteCode("abcd-ef23"))
        assertEquals("ABC", normalizeInviteCode("abc"))
    }

    @Test
    fun timestamps() {
        assertEquals(Instant.parse("2026-11-22T10:00:00.123456Z"), parseTs("2026-11-22T10:00:00.123456+00:00"))
        assertEquals(Instant.parse("2026-11-22T03:00:00Z"), parseTs("2026-11-22 10:00:00+07"))
        assertEquals(Instant.parse("2026-11-22T10:00:00Z"), parseTs("2026-11-22T10:00:00"))
        assertNull(parseTs("khong-phai-ngay"))
    }

    @Test
    fun parseMembershipAndInvite() {
        val m = Membership.from(
            JSONObject(
                """{"member_id":"m1","clan_id":"c1","clan_name":"Họ Nguyễn","clan_code":null,"hometown":null,
                "status":"pending","role":"member","branch_id":null,"admin_note":null,"requested_at":"2026-11-22T10:00:00+00:00"}""",
            ),
        )
        assertEquals(MemberStatus.PENDING, m.status)
        assertEquals(ClanRole.MEMBER, m.role)
        assertNull(m.clanCode)
        assertNull(m.branchId)

        val inv = Invite.from(
            JSONObject(
                """{"id":"i1","code_hint":"7K","branch_id":null,"expires_at":"2099-01-01T00:00:00+00:00",
                "max_uses":2,"used_count":2,"revoked_at":null}""",
            ),
        )
        assertFalse(inv.revoked)
        assertFalse(inv.isUsable()) // đã dùng hết lượt
        assertTrue(inv.copy(usedCount = 0).isUsable())
    }
}
