package co.gapfinder.mobile.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.domain.PairingFocus
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Selector "PRIORITIZE BY" del modo de match (antes MatchModeSelector). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusSelector(selected: PairingFocus, onChange: (PairingFocus) -> Unit) {
    Column {
        Text(
            text = "PRIORITIZE BY",
            style = Typo.label(FontWeight.ExtraBold).copy(
                fontSize = 13.sp,
                letterSpacing = 1.6.sp,
                color = Palette.Ink.fade(0.4f),
            ),
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PairingFocus.entries.forEach { focus ->
                val isOn = focus == selected
                val shape = RoundedCornerShape(12.dp)
                val fill by animateColorAsState(if (isOn) Palette.Coral else Palette.White, tween(200), label = "fill")
                val edge by animateColorAsState(if (isOn) Palette.Coral else Palette.Ink.fade(0.1f), tween(200), label = "edge")
                Text(
                    text = focus.caption,
                    style = Typo.paragraph(if (isOn) FontWeight.Black else FontWeight.SemiBold).copy(
                        fontSize = 14.sp,
                        color = if (isOn) Palette.White else Palette.Ink,
                    ),
                    modifier = Modifier
                        .background(fill, shape)
                        .border(1.5.dp, edge, shape)
                        .inkTap(shape) { onChange(focus) }
                        .padding(horizontal = 19.5.dp, vertical = 13.5.dp), // + grosor del borde
                )
            }
        }
    }
}
