package vn.vigo.nepnha.kinship

import java.time.LocalDate

enum class Gender { MALE, FEMALE, UNKNOWN }

enum class ParentType { BIOLOGICAL, ADOPTIVE }

/** Hồ sơ một người trong gia phả (tách biệt với tài khoản đăng nhập). */
data class Person(
    val id: String,
    val name: String,
    val gender: Gender = Gender.UNKNOWN,
    val birthDate: LocalDate? = null,
    /** Thứ tự trong anh chị em cùng cha mẹ: 1 = con cả. */
    val birthOrder: Int? = null,
    val isDeceased: Boolean = false,
)

data class ParentLink(val childId: String, val parentId: String, val type: ParentType = ParentType.BIOLOGICAL)

data class Marriage(val aId: String, val bId: String, val isCurrent: Boolean = true)

/**
 * Đồ thị huyết thống: chỉ lưu quan hệ gốc (cha/mẹ – con, vợ – chồng).
 * Mọi vai vế khác (ông bà, cô dì chú bác, anh chị em họ, thông gia...) được SUY RA, không lưu cứng.
 *
 * @param confirmedSeniority các cặp (người lớn hơn, người nhỏ hơn) do người quản lý gia phả xác nhận,
 *   dùng khi thiếu ngày sinh/thứ tự để phân định anh – em, bác – chú.
 */
class FamilyGraph(
    persons: Collection<Person>,
    parentLinks: Collection<ParentLink>,
    marriages: Collection<Marriage> = emptyList(),
    confirmedSeniority: Collection<Pair<String, String>> = emptyList(),
) {
    val persons: Map<String, Person> = persons.associateBy { it.id }
    private val parentsOf: Map<String, List<ParentLink>> = parentLinks.groupBy { it.childId }
    private val childrenOf: Map<String, List<ParentLink>> = parentLinks.groupBy { it.parentId }
    private val spousesOf: Map<String, List<Marriage>> =
        (marriages.map { it.aId to it } + marriages.map { it.bId to it }).groupBy({ it.first }, { it.second })
    private val seniority: Set<Pair<String, String>> = confirmedSeniority.toSet()

    fun person(id: String): Person? = persons[id]

    fun parents(id: String): List<ParentLink> = parentsOf[id].orEmpty()

    fun children(id: String): List<ParentLink> = childrenOf[id].orEmpty()

    fun spouses(id: String, currentOnly: Boolean = false): List<String> =
        spousesOf[id].orEmpty().filter { !currentOnly || it.isCurrent }
            .map { if (it.aId == id) it.bId else it.aId }

    fun father(id: String): String? =
        parents(id).firstOrNull { it.type == ParentType.BIOLOGICAL && persons[it.parentId]?.gender == Gender.MALE }?.parentId

    fun mother(id: String): String? =
        parents(id).firstOrNull { it.type == ParentType.BIOLOGICAL && persons[it.parentId]?.gender == Gender.FEMALE }?.parentId

    /**
     * true nếu [a] lớn hơn (vai trên) [b]; false nếu nhỏ hơn; null nếu không đủ dữ liệu.
     * Thứ tự ưu tiên: xác nhận của quản lý > thứ tự sinh (khi cùng cha hoặc mẹ) > ngày sinh.
     */
    fun isOlder(a: String, b: String): Boolean? {
        if (a == b) return null
        if (a to b in seniority) return true
        if (b to a in seniority) return false
        val pa = persons[a] ?: return null
        val pb = persons[b] ?: return null
        val shareParent = parents(a).any { la -> parents(b).any { it.parentId == la.parentId } }
        if (shareParent && pa.birthOrder != null && pb.birthOrder != null && pa.birthOrder != pb.birthOrder) {
            return pa.birthOrder < pb.birthOrder
        }
        if (pa.birthDate != null && pb.birthDate != null && pa.birthDate != pb.birthDate) {
            return pa.birthDate.isBefore(pb.birthDate)
        }
        return null
    }

    /** Kiểm tra quan hệ cha mẹ – con không tạo vòng (một người không thể là tổ tiên của chính mình). */
    fun wouldCreateCycle(childId: String, newParentId: String): Boolean {
        if (childId == newParentId) return true
        val stack = ArrayDeque<String>()
        val seen = HashSet<String>()
        stack.add(newParentId)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur == childId) return true
            if (seen.add(cur)) parents(cur).forEach { stack.add(it.parentId) }
        }
        return false
    }

    /** Đời (thế hệ) tính từ thủy tổ: người không có cha mẹ trong gia phả là đời 1. */
    fun generationOf(id: String): Int {
        val memo = HashMap<String, Int>()
        fun gen(p: String, depth: Int): Int {
            memo[p]?.let { return it }
            if (depth > 60) return 1
            val ps = parents(p)
            val g = if (ps.isEmpty()) 1 else ps.maxOf { gen(it.parentId, depth + 1) } + 1
            memo[p] = g
            return g
        }
        return gen(id, 0)
    }
}
