package vn.vigo.nepnha.lunar

import java.time.LocalDate

/** Tên Can Chi cho năm, tháng, ngày âm lịch. */
object CanChi {
    val CAN = listOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    val CHI = listOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    /** Con giáp tương ứng 12 Chi (người Việt dùng Mèo cho Mão, Trâu cho Sửu). */
    val ZODIAC = listOf("Chuột", "Trâu", "Hổ", "Mèo", "Rồng", "Rắn", "Ngựa", "Dê", "Khỉ", "Gà", "Chó", "Lợn")

    private fun mod(a: Int, n: Int): Int = ((a % n) + n) % n

    fun year(lunarYear: Int): String =
        "${CAN[mod(lunarYear + 6, 10)]} ${CHI[mod(lunarYear + 8, 12)]}"

    fun month(lunarMonth: Int, lunarYear: Int): String =
        "${CAN[mod(lunarYear * 12 + lunarMonth + 3, 10)]} ${CHI[mod(lunarMonth + 1, 12)]}"

    fun day(date: LocalDate): String {
        val jd = VietLunarCalendar.jdFromDate(date.dayOfMonth, date.monthValue, date.year)
        return "${CAN[mod(jd + 9, 10)]} ${CHI[mod(jd + 1, 12)]}"
    }

    fun zodiacOfYear(lunarYear: Int): String = ZODIAC[mod(lunarYear + 8, 12)]

    private val SOLAR_TERMS = listOf(
        "Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng",
        "Hạ chí", "Tiểu thử", "Đại thử", "Lập thu", "Xử thử", "Bạch lộ",
        "Thu phân", "Hàn lộ", "Sương giáng", "Lập đông", "Tiểu tuyết", "Đại tuyết",
        "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập",
    )

    /** Tên tiết khí đang hiệu lực của ngày. */
    fun solarTerm(date: LocalDate): String = SOLAR_TERMS[VietLunarCalendar.solarTermIndex(date)]
}
