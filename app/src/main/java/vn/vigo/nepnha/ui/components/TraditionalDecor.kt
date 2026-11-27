package vn.vigo.nepnha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.vigo.nepnha.ui.theme.LocalPerformance
import vn.vigo.nepnha.ui.theme.NepColors

/**
 * Dải hoa văn "chữ Công" (hồi văn) truyền thống, vẽ bằng Path được cache –
 * không dùng ảnh bitmap, nhẹ trên máy yếu và sắc nét mọi màn hình.
 */
@Composable
fun KeyPatternBand(modifier: Modifier = Modifier, height: Dp = 14.dp, color: Color = NepColors.Bronze) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val h = size.height
                val unit = h // mỗi ô vuông cạnh = chiều cao dải
                val stroke = (h / 7f).coerceAtLeast(1f)
                val path = Path()
                var x = 0f
                while (x < size.width) {
                    // Một móc hồi văn trong ô [x, x+unit]
                    val p = stroke
                    path.moveTo(x + p, h - p)
                    path.lineTo(x + p, p)
                    path.lineTo(x + unit - p, p)
                    path.lineTo(x + unit - p, h - p * 3)
                    path.lineTo(x + p * 3, h - p * 3)
                    path.lineTo(x + p * 3, p * 3)
                    x += unit
                }
                val style = Stroke(width = stroke)
                onDrawBehind {
                    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), stroke)
                    drawLine(color, Offset(0f, h), Offset(size.width, h), stroke)
                    drawPath(path, color, style = style)
                }
            },
    )
}

/** Viền góc kiểu khung tranh (như các ô thẻ trong giao diện gia phả truyền thống). */
fun Modifier.cornerFrame(color: Color = NepColors.Bronze, corner: Dp = 14.dp, inset: Dp = 5.dp): Modifier =
    this.drawWithCache {
        val c = corner.toPx()
        val i = inset.toPx()
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.5.dp.toPx())
        val path = Path().apply {
            moveTo(i, i + c); lineTo(i, i); lineTo(i + c, i)
            moveTo(w - i - c, i); lineTo(w - i, i); lineTo(w - i, i + c)
            moveTo(w - i, h - i - c); lineTo(w - i, h - i); lineTo(w - i - c, h - i)
            moveTo(i + c, h - i); lineTo(i, h - i); lineTo(i, h - i - c)
        }
        onDrawWithContent {
            drawContent()
            drawPath(path, color, style = stroke)
        }
    }

/** Thẻ nội dung chuẩn: nền trắng ấm, viền đồng mảnh, góc khung truyền thống. */
@Composable
fun NepCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    framed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val light = LocalPerformance.current.lightDecor
    val inner = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    var m = modifier.fillMaxWidth()
    if (framed) {
        // Thẻ nổi bật: lớp vỏ màu kem đậm + lõi trắng ấm, bo góc đồng tâm (vỏ 20dp = lõi 14dp + khe 6dp)
        m = m.background(NepColors.Shell, RoundedCornerShape(20.dp))
            .border(1.dp, NepColors.Hairline, RoundedCornerShape(20.dp))
            .padding(6.dp)
    }
    m = m.pressScale(interaction, enabled = onClick != null)
        .background(NepColors.WarmWhite, inner)
        .border(1.dp, NepColors.Hairline, inner)
    if (framed && !light) m = m.cornerFrame(NepColors.Bronze.copy(alpha = 0.7f))
    if (onClick != null) {
        m = m.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Column(m.padding(horizontal = 20.dp, vertical = 18.dp), content = content)
}

/** Phản hồi chạm: thu nhỏ rất nhẹ (0.98) khi nhấn, như ấn vào vật thật. Chỉ dùng transform nên không tốn bố cục. */
fun Modifier.pressScale(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, animationSpec = tween(110), label = "press")
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Dấu chấm hoa văn nhỏ dưới tiêu đề (như họa tiết hoa mai cách điệu). */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .drawWithCache {
                val cy = size.height / 2
                val cx = size.width / 2
                val r = 3.5.dp.toPx()
                val line = 56.dp.toPx()
                onDrawBehind {
                    drawLine(NepColors.Bronze, Offset(cx - line, cy), Offset(cx - r * 2.5f, cy), 1.dp.toPx())
                    drawLine(NepColors.Bronze, Offset(cx + r * 2.5f, cy), Offset(cx + line, cy), 1.dp.toPx())
                    // Hoa mai 5 cánh tối giản
                    for (k in 0 until 5) {
                        val a = Math.toRadians(-90.0 + k * 72.0)
                        drawCircle(
                            NepColors.DeepRed,
                            radius = r * 0.7f,
                            center = Offset(cx + (r * Math.cos(a)).toFloat(), cy + (r * Math.sin(a)).toFloat()),
                        )
                    }
                    drawCircle(NepColors.Bronze, radius = r * 0.45f, center = Offset(cx, cy))
                }
            },
    )
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(color, RoundedCornerShape(50)))
}
