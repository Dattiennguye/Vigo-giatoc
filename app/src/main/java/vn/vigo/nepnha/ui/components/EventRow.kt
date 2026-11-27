package vn.vigo.nepnha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.vigo.nepnha.domain.EventType
import vn.vigo.nepnha.domain.Occurrence
import vn.vigo.nepnha.ui.theme.NepColors
import java.time.LocalDate

/** Nhãn biểu tượng loại sự kiện: chữ ngắn trên nền màu (rõ nghĩa hơn icon trừu tượng với người lớn tuổi). */
@Composable
fun EventTypeBadge(type: EventType, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(min = 52.dp)
            .background(Color(type.colorHex).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            type.symbol,
            color = Color(type.colorHex),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun OccurrenceRow(occ: Occurrence, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val e = occ.event
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(onClickLabel = "Xem và sửa sự kiện", role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EventTypeBadge(e.type)
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.titleSmall,
                color = NepColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${VnFormat.solar(occ.date)} · ${VnFormat.lunarOf(occ.date)} âm" +
                    (e.personName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = NepColors.InkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            VnFormat.relative(today, occ.date),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (occ.date == today) NepColors.DeepRed else NepColors.Wood,
            textAlign = TextAlign.End,
        )
    }
}
