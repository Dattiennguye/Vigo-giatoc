package vn.vigo.nepnha.kinship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import vn.vigo.nepnha.kinship.Gender.FEMALE as F
import vn.vigo.nepnha.kinship.Gender.MALE as M

class KinshipResolverTest {

    private fun p(id: String, g: Gender, order: Int? = null, born: Int? = null) =
        Person(id, id, g, born?.let { LocalDate.of(it, 1, 1) }, order)

    private val persons = listOf(
        p("ongNoi", M), p("baNoi", F), p("ongNgoai", M), p("baNgoai", F),
        p("bac", M, order = 1), p("bo", M, order = 2), p("co", F, order = 3), p("chu", M, order = 4),
        p("chuKhongRo", M),
        p("me", F, born = 1970), p("di", F, born = 1975), p("cauLon", M, born = 1965),
        p("chiGai", F, order = 1, born = 1992), p("toi", M, order = 2, born = 1995), p("emTrai", M, order = 3, born = 1998),
        p("conBac", M, born = 2000), p("conChu", F, born = 1990),
        p("vo", F), p("boVo", M), p("conTrai", M), p("conDau", F), p("boConDau", M), p("chauNoi", M),
        p("thim", F), p("conNuoi", F), p("voTruocCuaBo", F), p("emCungCha", M, born = 2005),
    )

    private val links = buildList {
        fun child(c: String, vararg ps: String, type: ParentType = ParentType.BIOLOGICAL) =
            ps.forEach { add(ParentLink(c, it, type)) }
        listOf("bac", "bo", "co", "chu", "chuKhongRo").forEach { child(it, "ongNoi", "baNoi") }
        listOf("me", "di", "cauLon").forEach { child(it, "ongNgoai", "baNgoai") }
        listOf("chiGai", "toi", "emTrai").forEach { child(it, "bo", "me") }
        child("conBac", "bac")
        child("conChu", "chu", "thim")
        child("vo", "boVo")
        child("conTrai", "toi", "vo")
        child("conDau", "boConDau")
        child("chauNoi", "conTrai", "conDau")
        child("conNuoi", "toi", type = ParentType.ADOPTIVE)
        child("emCungCha", "bo", "voTruocCuaBo")
    }

    private val marriages = listOf(
        Marriage("bo", "me"), Marriage("toi", "vo"), Marriage("conTrai", "conDau"), Marriage("chu", "thim"),
        Marriage("bo", "voTruocCuaBo"),
    )

    private val graph = FamilyGraph(persons, links, marriages)
    private val r = KinshipResolver(graph)

    private fun term(viewer: String, target: String): String =
        (r.resolve(viewer, target) as? Kinship.Known)?.term ?: "?? ${r.resolve(viewer, target)}"

    @Test
    fun directLine() {
        assertEquals("Bố", term("toi", "bo"))
        assertEquals("Mẹ", term("toi", "me"))
        assertEquals("Ông nội", term("toi", "ongNoi"))
        assertEquals("Bà ngoại", term("toi", "baNgoai"))
        assertEquals("Con", term("toi", "conTrai"))
        assertEquals("Con nuôi", term("toi", "conNuoi"))
        assertEquals("Cháu nội", term("toi", "chauNoi"))
        assertEquals("Cụ ông nội", term("chauNoi", "bo"))
    }

    @Test
    fun siblings() {
        assertEquals("Chị", term("toi", "chiGai"))
        assertEquals("Em trai", term("toi", "emTrai"))
        assertEquals("Anh", term("emTrai", "toi"))
        assertEquals("Em trai (cùng cha khác mẹ)", term("toi", "emCungCha"))
    }

    @Test
    fun unclesAndAuntsNorth() {
        assertEquals("Bác", term("toi", "bac"))
        assertEquals("Chú", term("toi", "chu"))
        assertEquals("Cô", term("toi", "co"))
        assertEquals("Dì", term("toi", "di"))
        // Anh trai của mẹ, miền Bắc gọi là Bác
        assertEquals("Bác", term("toi", "cauLon"))
        assertEquals("Cậu", (KinshipResolver(graph, Dialect.SOUTH).resolve("toi", "cauLon") as Kinship.Known).term)
    }

    @Test
    fun cousinsFollowBranchSeniorityNotAge() {
        // Con bác nhỏ tuổi hơn tôi nhưng vẫn là anh họ
        assertEquals("Anh họ", term("toi", "conBac"))
        // Con chú lớn tuổi hơn tôi nhưng vẫn là em họ
        assertEquals("Em gái họ", term("toi", "conChu"))
    }

    @Test
    fun inLaws() {
        assertEquals("Vợ", term("toi", "vo"))
        assertEquals("Bố vợ", term("toi", "boVo"))
        assertEquals("Con dâu", term("toi", "conDau"))
        assertEquals("Thím", term("toi", "thim"))
        assertEquals("Ông thông gia", term("toi", "boConDau"))
        assertEquals("Mẹ kế", term("toi", "voTruocCuaBo"))
    }

    @Test
    fun missingDataAsksForConfirmationInsteadOfGuessing() {
        val res = r.resolve("toi", "chuKhongRo")
        assertTrue(res is Kinship.NeedsConfirmation)
        res as Kinship.NeedsConfirmation
        assertEquals(listOf("Bác", "Chú"), res.possibleTerms)

        // Sau khi quản lý xác nhận "chuKhongRo" nhỏ hơn "bo"
        val confirmed = FamilyGraph(persons, links, marriages, confirmedSeniority = listOf("bo" to "chuKhongRo"))
        assertEquals("Chú", (KinshipResolver(confirmed).resolve("toi", "chuKhongRo") as Kinship.Known).term)
    }

    @Test
    fun customOverrideWins() {
        val custom = KinshipResolver(graph, overrides = mapOf(("toi" to "chu") to "Chú Út"))
        assertEquals("Chú Út", (custom.resolve("toi", "chu") as Kinship.Known).term)
    }

    @Test
    fun cycleAndGeneration() {
        assertTrue(graph.wouldCreateCycle("ongNoi", "chauNoi"))
        assertFalse(graph.wouldCreateCycle("chauNoi", "boVo"))
        assertEquals(1, graph.generationOf("ongNoi"))
        assertEquals(3, graph.generationOf("toi"))
        assertEquals(5, graph.generationOf("chauNoi"))
    }

    @Test
    fun strangersAreNotRelated() {
        assertEquals(Kinship.NotRelated, r.resolve("emTrai", "boConDau"))
    }
}
