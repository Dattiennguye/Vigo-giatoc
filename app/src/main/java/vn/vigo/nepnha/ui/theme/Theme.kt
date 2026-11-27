package vn.vigo.nepnha.ui.theme

import vn.vigo.nepnha.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Bảng màu NẾP NHÀ (theo tài liệu thiết kế). */
object NepColors {
    val Ivory = Color(0xFFFAF5EE)       // Kem ngà – nền
    val WarmWhite = Color(0xFFFFFDF9)   // Trắng ấm – thẻ
    val Wood = Color(0xFF624735)        // Nâu gỗ
    val DeepRed = Color(0xFF8C3438)     // Đỏ trầm – nhấn chính
    val Bronze = Color(0xFFB58A50)      // Vàng đồng – viền, họa tiết
    val Ink = Color(0xFF292522)         // Chữ chính
    val InkSoft = Color(0xFF5C534C)     // Chữ phụ (vẫn đạt tương phản AA trên nền kem)
    val BronzeLight = Color(0xFFEFE3CF)
    val RedLight = Color(0xFFF5E3E1)
    val Success = Color(0xFF2E6B3F)
    val Error = Color(0xFFA4262C)
    val Hairline = Color(0x1F624735)    // viền mảnh: nâu gỗ 12%
    val Shell = Color(0xFFF3EADC)       // lớp vỏ ngoài của thẻ nổi bật
}

private val colorScheme = lightColorScheme(
    primary = NepColors.DeepRed,
    onPrimary = NepColors.WarmWhite,
    primaryContainer = NepColors.RedLight,
    onPrimaryContainer = NepColors.Ink,
    secondary = NepColors.Wood,
    onSecondary = NepColors.WarmWhite,
    secondaryContainer = NepColors.BronzeLight,
    onSecondaryContainer = NepColors.Ink,
    tertiary = NepColors.Bronze,
    background = NepColors.Ivory,
    onBackground = NepColors.Ink,
    surface = NepColors.WarmWhite,
    onSurface = NepColors.Ink,
    surfaceVariant = NepColors.BronzeLight,
    onSurfaceVariant = NepColors.InkSoft,
    surfaceContainer = NepColors.WarmWhite,
    surfaceContainerHigh = NepColors.WarmWhite,
    surfaceContainerHighest = NepColors.BronzeLight,
    outline = NepColors.Bronze,
    outlineVariant = NepColors.BronzeLight,
    error = NepColors.Error,
)

// Chữ: Spectral (serif, tiêu đề) + Be Vietnam Pro (nội dung) – cả hai vẽ dấu tiếng Việt chuẩn.
// Đã cắt bớt chỉ còn ký tự Latin + tiếng Việt để APK nhẹ. Không dùng font thư pháp cho nội dung.
val Serif = FontFamily(Font(R.font.spectral_semibold, FontWeight.SemiBold))
val Sans = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.Bold),
)

// Tiêu đề: khoảng cách chữ hơi khít, dòng 1.2; nội dung: dòng 1.5 để người lớn tuổi đọc thoải mái.
private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 19.sp, lineHeight = 29.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 18.sp, lineHeight = 27.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

/** Nhãn nhỏ viết hoa, giãn chữ – đặt trên tiêu đề để tạo thứ bậc (vẫn ≥ 14sp cho người lớn tuổi). */
val EyebrowStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 1.4.sp)

/** Cấu hình hiệu năng: trên máy yếu tắt hiệu ứng chuyển cảnh và họa tiết phụ. */
@Immutable
data class PerformanceConfig(val reduceMotion: Boolean, val lightDecor: Boolean)

val LocalPerformance = staticCompositionLocalOf { PerformanceConfig(reduceMotion = false, lightDecor = false) }

@Composable
fun NepNhaTheme(
    userFontScale: Float = 1f,
    performance: PerformanceConfig,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    // Cỡ chữ người dùng chọn (100–150%) nhân với cỡ chữ hệ thống, giới hạn để không vỡ bố cục.
    val scaled = Density(base.density, (base.fontScale * userFontScale).coerceIn(0.85f, 2.0f))
    CompositionLocalProvider(LocalDensity provides scaled, LocalPerformance provides performance) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
