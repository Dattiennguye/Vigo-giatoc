package vn.vigo.nepnha.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.vigo.nepnha.ui.theme.NepColors

/**
 * Bộ tăng/giảm số lớn – dễ bấm cho người lớn tuổi hơn bánh xe cuộn.
 * Giữ giá trị trong [range]; nút có nhãn đọc màn hình rõ ràng.
 */
@Composable
fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onChange(if (value <= range.first) range.last else value - 1) },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = NepColors.BronzeLight),
            ) { Text("−", fontSize = 26.sp, color = NepColors.Wood, modifier = Modifier.semantics { contentDescription = "Giảm $label" }) }
            Text(
                format(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = 4.dp)
                    .semantics { stateDescription = "$label ${format(value)}" },
            )
            FilledTonalIconButton(
                onClick = { onChange(if (value >= range.last) range.first else value + 1) },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = NepColors.BronzeLight),
            ) { Text("+", fontSize = 26.sp, color = NepColors.Wood, modifier = Modifier.semantics { contentDescription = "Tăng $label" }) }
        }
    }
}

@Composable
fun NepTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error, color = NepColors.Error) }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NepColors.DeepRed,
            unfocusedBorderColor = NepColors.Bronze,
            focusedLabelColor = NepColors.DeepRed,
            focusedContainerColor = NepColors.WarmWhite,
            unfocusedContainerColor = NepColors.WarmWhite,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Dòng bật/tắt: cả dòng là vùng chạm (≥ 56dp), không chỉ riêng công tắc. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = NepColors.InkSoft)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = NepColors.DeepRed),
        )
    }
}

@Composable
fun InfoBox(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = NepColors.Wood,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, NepColors.BronzeLight, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}
