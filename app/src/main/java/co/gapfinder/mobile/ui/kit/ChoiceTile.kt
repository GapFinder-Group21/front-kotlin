package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Tarjeta blanca con ícono, título y subtítulo (antes ScheduleOptionCard). */
@Composable
fun ChoiceTile(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.White, shape)
            .inkTap(shape, onTap = onTap)
            .border(1.5.dp, Palette.Ink.fade(0.15f), shape)
            .padding(horizontal = 21.5.dp, vertical = 23.5.dp), // incluye el grosor del borde, como Container
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typo.heading(FontWeight.Bold).copy(fontSize = 15.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.5f)),
            )
        }
    }
}
