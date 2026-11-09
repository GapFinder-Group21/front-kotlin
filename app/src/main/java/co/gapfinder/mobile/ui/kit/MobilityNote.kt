package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.domain.MobilityPreference
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** "Up to 500 m · first floor only" */
fun MobilityPreference.summary(): String = listOfNotNull(
    maxWalkMeters?.let { "up to $it m" },
    "first floor only".takeIf { groundFloorOnly },
).joinToString(" · ").replaceFirstChar { it.uppercase() }

/**
 * Aviso de que la lista está filtrada por la preferencia de movilidad, con cuántas mesas se
 * escondieron y un acceso para cambiarla. No se muestra si no hay preferencia.
 */
@Composable
fun MobilityNote(
    preference: MobilityPreference,
    hidden: Int,
    distanceUnknown: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!preference.isActive) return
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Teal.fade(0.08f), shape)
            .inkTap(shape, onTap = onEdit)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.accessibleRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = preference.summary() + if (hidden > 0) " · $hidden hidden" else "",
                style = Typo.paragraph(FontWeight.SemiBold).copy(fontSize = 13.sp, color = Palette.Ink.fade(0.75f)),
            )
            if (distanceUnknown) {
                Text(
                    text = "Turn on location to filter by distance.",
                    style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.5f)),
                )
            }
        }
        Text(
            text = "Edit",
            style = Typo.label(FontWeight.Bold).copy(fontSize = 13.sp, color = Palette.Teal),
        )
    }
}
