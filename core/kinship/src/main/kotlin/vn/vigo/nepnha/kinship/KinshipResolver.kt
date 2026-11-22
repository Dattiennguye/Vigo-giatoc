package vn.vigo.nepnha.kinship

/** Cách gọi theo vùng miền. Người dùng/gia đình có thể ghi đè từng cách gọi bằng [KinshipResolver.overrides]. */
enum class Dialect { NORTH, SOUTH }

enum class Side { PATERNAL, MATERNAL, NONE }

sealed interface Kinship {
    /** @param term cách [viewer] gọi [target]. @param generationGap >0: target thuộc vai trên. */
    data class Known(
        val term: String,
        val generationGap: Int,
        val side: Side = Side.NONE,
        val isBlood: Boolean = true,
        val viaAdoption: Boolean = false,
    ) : Kinship

    /** Không đủ dữ liệu để phân định (thường là thiếu thứ tự lớn – nhỏ). Cần người quản lý gia phả xác nhận. */
    data class NeedsConfirmation(
        val reason: String,
        /** Cặp người cần xác nhận ai lớn hơn. */
        val compare: Pair<String, String>?,
        val possibleTerms: List<String>,
    ) : Kinship

    data object NotRelated : Kinship
}

/**
 * Suy ra vai vế giữa hai người dựa trên đồ thị huyết thống thực sự.
 * Không bao giờ đoán khi thiếu dữ liệu: trả về [Kinship.NeedsConfirmation].
 */
class KinshipResolver(
    private val graph: FamilyGraph,
    private val dialect: Dialect = Dialect.NORTH,
    /** Cách xưng hô tùy chỉnh theo phong tục gia đình: (viewerId, targetId) -> cách gọi. */
    private val overrides: Map<Pair<String, String>, String> = emptyMap(),
) {
    private class Node(val depth: Int, val prev: String?, val adoptive: Boolean)

    /** Cách [viewer] gọi [target]. */
    fun resolve(viewer: String, target: String): Kinship {
        overrides[viewer to target]?.let { return Kinship.Known(it, 0, isBlood = false) }
        if (viewer == target) return Kinship.Known("Bản thân", 0)
        if (graph.person(viewer) == null || graph.person(target) == null) return Kinship.NotRelated

        if (target in graph.spouses(viewer)) {
            return Kinship.Known(byGender(target, "Chồng", "Vợ", "Vợ/Chồng"), 0, isBlood = false)
        }
        blood(viewer, target)?.let { return it }
        inLaw(viewer, target)?.let { return it }
        return Kinship.NotRelated
    }

    // ---------------------------------------------------------------- huyết thống

    private fun ancestors(start: String): Map<String, Node> {
        val out = HashMap<String, Node>()
        out[start] = Node(0, null, false)
        var frontier = listOf(start)
        var depth = 0
        while (frontier.isNotEmpty() && depth < 12) {
            depth++
            val next = ArrayList<String>()
            for (c in frontier) {
                val cAdopt = out[c]!!.adoptive
                for (link in graph.parents(c)) {
                    if (link.parentId !in out) {
                        out[link.parentId] = Node(depth, c, cAdopt || link.type == ParentType.ADOPTIVE)
                        next += link.parentId
                    }
                }
            }
            frontier = next
        }
        return out
    }

    /** Đường đi từ tổ tiên chung xuống [start]: [lca, ..., start]. */
    private fun pathDown(map: Map<String, Node>, lca: String): List<String> {
        val path = ArrayList<String>()
        var cur: String? = lca
        while (cur != null) {
            path += cur
            cur = map[cur]?.prev
        }
        return path
    }

    private fun blood(viewer: String, target: String): Kinship? {
        val av = ancestors(viewer)
        val at = ancestors(target)
        val common = av.keys.intersect(at.keys)
        if (common.isEmpty()) return null
        val lca = common.minBy { av[it]!!.depth + at[it]!!.depth }
        val a = av[lca]!!.depth // số đời từ viewer lên tổ chung
        val b = at[lca]!!.depth // số đời từ target lên tổ chung
        val adoptive = av[lca]!!.adoptive || at[lca]!!.adoptive
        val pathV = pathDown(av, lca) // [lca, ..., viewer]
        val pathT = pathDown(at, lca)
        val gap = a - b
        // Bên nội/ngoại: xác định bởi cha hay mẹ của viewer trên đường đi.
        val side = if (a >= 1) {
            val parentOnPath = pathV[pathV.size - 2]
            when (graph.person(parentOnPath)?.gender) {
                Gender.MALE -> Side.PATERNAL
                Gender.FEMALE -> Side.MATERNAL
                else -> Side.NONE
            }
        } else Side.NONE

        fun known(term: String) = Kinship.Known(term, gap, side, isBlood = !adoptive, viaAdoption = adoptive)

        return when {
            gap == 0 -> siblingLike(viewer, target, a, pathV, pathT, adoptive)
            gap == 1 && b == 0 -> known(byGender(target, "Bố", "Mẹ", "Cha/Mẹ") + if (adoptive) " nuôi" else "")
            gap == 1 -> uncleAunt(target, a, pathV, pathT, side, adoptive)
            gap == 2 -> known(byGender(target, "Ông", "Bà", "Ông/Bà") + sideSuffix(side))
            gap == 3 -> known(byGender(target, "Cụ ông", "Cụ bà", "Cụ") + sideSuffix(side))
            gap == 4 -> known(byGender(target, "Kỵ ông", "Kỵ bà", "Kỵ"))
            gap >= 5 -> known("Tổ tiên (trên $gap đời)")
            gap == -1 && a == 0 -> known("Con" + if (adoptive) " nuôi" else "")
            gap == -2 && a == 0 -> {
                // cháu nội nếu qua con trai, cháu ngoại nếu qua con gái
                val childOnPath = pathT[1]
                known(
                    when (graph.person(childOnPath)?.gender) {
                        Gender.MALE -> "Cháu nội"
                        Gender.FEMALE -> "Cháu ngoại"
                        else -> "Cháu"
                    },
                )
            }
            gap == -1 || gap == -2 -> known("Cháu")
            gap == -3 -> known("Chắt")
            gap == -4 -> known("Chút")
            else -> known("Hậu duệ (dưới ${-gap} đời)")
        }
    }

    private fun sideSuffix(side: Side) = when (side) {
        Side.PATERNAL -> " nội"
        Side.MATERNAL -> " ngoại"
        Side.NONE -> ""
    }

    private fun siblingLike(
        viewer: String,
        target: String,
        a: Int,
        pathV: List<String>,
        pathT: List<String>,
        adoptive: Boolean,
    ): Kinship {
        // Anh chị em ruột: so trực tiếp. Anh chị em họ: thứ bậc theo nhánh (con chú con bác), không theo tuổi.
        val (x, y) = if (a == 1) viewer to target else pathV[1] to pathT[1]
        val older = graph.isOlder(y, x)
        val suffix = when {
            a == 1 -> halfSiblingSuffix(viewer, target)
            else -> " họ"
        }
        val termOlder = byGender(target, "Anh", "Chị", "Anh/Chị") + suffix
        val termYounger = byGender(target, "Em trai", "Em gái", "Em") + suffix
        return when (older) {
            true -> Kinship.Known(termOlder, 0, isBlood = !adoptive, viaAdoption = adoptive)
            false -> Kinship.Known(termYounger, 0, isBlood = !adoptive, viaAdoption = adoptive)
            null -> Kinship.NeedsConfirmation(
                reason = if (a == 1) "Chưa rõ ai lớn hơn giữa hai anh chị em" else "Chưa rõ thứ bậc giữa hai nhánh",
                compare = y to x,
                possibleTerms = listOf(termOlder, termYounger),
            )
        }
    }

    private fun halfSiblingSuffix(v: String, t: String): String {
        val fv = graph.father(v)
        val ft = graph.father(t)
        val mv = graph.mother(v)
        val mt = graph.mother(t)
        val sameFather = fv != null && fv == ft
        val sameMother = mv != null && mv == mt
        return when {
            sameFather && !sameMother && mv != null && mt != null -> " (cùng cha khác mẹ)"
            sameMother && !sameFather && fv != null && ft != null -> " (cùng mẹ khác cha)"
            else -> ""
        }
    }

    private fun uncleAunt(
        target: String,
        a: Int,
        pathV: List<String>,
        pathT: List<String>,
        side: Side,
        adoptive: Boolean,
    ): Kinship {
        // Vai cô dì chú bác: so target với cha/mẹ của viewer (a==2) hoặc so hai nhánh (họ xa hơn).
        val parentOnPath = pathV[pathV.size - 2]
        val (x, y) = if (a == 2) parentOnPath to target else pathV[1] to pathT[1]
        val older = graph.isOlder(y, x)
        val gender = graph.person(target)?.gender ?: Gender.UNKNOWN
        val suffix = if (a == 2) "" else " họ"

        fun term(isOlder: Boolean): String? = when (side) {
            Side.PATERNAL -> when {
                dialect == Dialect.NORTH && isOlder -> if (gender == Gender.FEMALE) "Bác gái" else "Bác"
                gender == Gender.MALE -> if (isOlder) "Bác" else "Chú"
                gender == Gender.FEMALE -> "Cô"
                else -> null
            }
            Side.MATERNAL -> when {
                dialect == Dialect.NORTH && isOlder -> if (gender == Gender.FEMALE) "Bác gái" else "Bác"
                gender == Gender.MALE -> "Cậu"
                gender == Gender.FEMALE -> "Dì"
                else -> null
            }
            Side.NONE -> null
        }

        val tOlder = term(true)
        val tYounger = term(false)
        if (tOlder == null || tYounger == null) {
            return Kinship.NeedsConfirmation(
                reason = "Thiếu giới tính của người được gọi hoặc của cha/mẹ trên đường quan hệ",
                compare = null,
                possibleTerms = listOf("Bác", "Chú", "Cô", "Cậu", "Dì"),
            )
        }
        if (tOlder == tYounger) {
            return Kinship.Known(tOlder + suffix, 1, side, !adoptive, adoptive)
        }
        return when (older) {
            true -> Kinship.Known(tOlder + suffix, 1, side, !adoptive, adoptive)
            false -> Kinship.Known(tYounger + suffix, 1, side, !adoptive, adoptive)
            null -> Kinship.NeedsConfirmation(
                reason = "Chưa rõ người này lớn hay nhỏ hơn bố/mẹ của bạn (hoặc thứ bậc giữa hai nhánh)",
                compare = y to x,
                possibleTerms = listOf(tOlder + suffix, tYounger + suffix),
            )
        }
    }

    // ---------------------------------------------------------------- quan hệ qua hôn nhân

    private fun inLaw(viewer: String, target: String): Kinship? {
        // 1) target là người thân của vợ/chồng mình
        for (sp in graph.spouses(viewer, currentOnly = true)) {
            val k = blood(sp, target) as? Kinship.Known ?: continue
            val husbandSide = graph.person(sp)?.gender == Gender.MALE
            val sideName = if (husbandSide) "chồng" else "vợ"
            val term = when {
                k.generationGap == 1 && k.term.startsWith("Bố") -> "Bố $sideName"
                k.generationGap == 1 && k.term.startsWith("Mẹ") -> "Mẹ $sideName"
                k.generationGap == 0 && (k.term.startsWith("Anh") || k.term.startsWith("Chị") || k.term.startsWith("Em")) ->
                    "${k.term.substringBefore(" (")} $sideName"
                k.generationGap < 0 -> k.term // con, cháu của vợ/chồng
                else -> "${k.term} (bên $sideName)"
            }
            return k.copy(term = term, isBlood = false)
        }
        // 2) target là vợ/chồng của người thân của mình
        for (sp in graph.spouses(target, currentOnly = true)) {
            if (sp == viewer) continue
            val k = blood(viewer, sp) as? Kinship.Known ?: continue
            val t = graph.person(target)?.gender
            val base = k.term.substringBefore(" (").removeSuffix(" họ")
            val term = when {
                k.generationGap == -1 && (base == "Con" || base == "Con nuôi") ->
                    if (t == Gender.FEMALE) "Con dâu" else "Con rể"
                k.generationGap == 1 && base.startsWith("Bố") -> if (t == Gender.FEMALE) "Mẹ kế" else "Bố dượng"
                k.generationGap == 1 && base.startsWith("Mẹ") -> if (t == Gender.MALE) "Bố dượng" else "Mẹ kế"
                base == "Anh" -> "Chị dâu"
                base == "Chị" -> "Anh rể"
                base == "Em trai" -> "Em dâu"
                base == "Em gái" -> "Em rể"
                base == "Chú" -> "Thím"
                base == "Cậu" -> "Mợ"
                base == "Bác" -> "Bác gái"
                base == "Bác gái" -> "Bác trai"
                base == "Cô" -> if (dialect == Dialect.NORTH) "Chú (chồng cô)" else "Dượng"
                base == "Dì" -> if (dialect == Dialect.NORTH) "Chú (chồng dì)" else "Dượng"
                k.generationGap <= -1 -> if (t == Gender.FEMALE) "Cháu dâu" else "Cháu rể"
                k.generationGap >= 2 -> k.term.replace("Ông", "Bà").takeIf { t == Gender.FEMALE }
                    ?: k.term.replace("Bà", "Ông")
                else -> "Vợ/chồng của ${k.term.lowercase()}"
            }
            return k.copy(term = term, isBlood = false)
        }
        // 3) Thông gia: cha mẹ của vợ/chồng con mình
        for (child in graph.children(viewer)) {
            for (childSpouse in graph.spouses(child.childId, currentOnly = true)) {
                if (graph.parents(childSpouse).any { it.parentId == target }) {
                    return Kinship.Known(byGender(target, "Ông thông gia", "Bà thông gia", "Thông gia"), 0, isBlood = false)
                }
            }
        }
        return null
    }

    private fun byGender(id: String, male: String, female: String, unknown: String): String =
        when (graph.person(id)?.gender) {
            Gender.MALE -> male
            Gender.FEMALE -> female
            else -> unknown
        }
}
