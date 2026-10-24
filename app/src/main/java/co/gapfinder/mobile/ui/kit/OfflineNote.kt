package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import co.gapfinder.mobile.ui.theme.fade
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Aviso dentro de una pantalla que explica qué pasa con esa pantalla sin red
 * (qué se bloquea, qué se guarda para después). El banner global de "sin conexión" es de Daniela.
 */
@Composable
fun OfflineNote(text: String, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val tone = if (onDark) Color.White else Palette.Ink
    Row(
        modifier
            .fillMaxWidth()
            .background(tone.fade(if (onDark) 0.12f else 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.cloudOff, contentDescription = null, tint = tone.fade(0.6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Typo.paragraph(FontWeight.SemiBold).copy(fontSize = 13.sp, color = tone.fade(0.75f)))
    }
}

/** "Last updated 14:05" (hoy) o "Last updated Oct 18, 14:05" (otro día). */
fun lastUpdatedText(moment: LocalDateTime): String {
    val pattern = if (moment.toLocalDate() == LocalDate.now()) "HH:mm" else "MMM d, HH:mm"
    return "Last updated ${moment.format(DateTimeFormatter.ofPattern(pattern))}"
}
