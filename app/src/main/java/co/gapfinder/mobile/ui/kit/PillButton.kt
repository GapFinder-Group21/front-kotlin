package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo

/** Color que Flutter M3 aplica al fondo de un ElevatedButton deshabilitado. */
val DisabledFill = Color(0x1F1D1B20)

/** Botón principal de ancho completo (antes CustomButton). */
@Composable
fun PillButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = Palette.Coral,
    textColor: Color = Palette.White,
    outline: Boolean = false,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val fill = when {
        outline -> Color.Transparent
        onClick == null -> DisabledFill
        else -> color
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(fill, shape)
            .then(if (outline) Modifier.border(BorderStroke(2.dp, color), shape) else Modifier)
            .inkTap(shape, onTap = onClick)
            .padding(vertical = if (compact) 10.dp else 14.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Typo.label(FontWeight.Bold).copy(
                fontSize = if (compact) 14.sp else 16.sp,
                letterSpacing = 0.3.sp,
                color = if (outline) color else textColor,
            ),
        )
    }
}
