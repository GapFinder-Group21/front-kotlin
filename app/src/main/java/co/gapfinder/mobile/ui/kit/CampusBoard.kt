package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Placeholder del mapa del campus (antes CampusMap). */
@Composable
fun CampusBoard(onSpotTap: ((String) -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.05f), spotColor = Palette.Ink.fade(0.05f))
            .background(Palette.White, shape)
            .border(1.5.dp, Palette.Ink.fade(0.1f), shape)
            .clip(shape)
    ) {
        // Patrón de fondo: grilla de 10 columnas de edificios
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().alpha(0.05f)) {
            val cell = maxWidth / 10
            val rows = (maxHeight / cell).toInt() + 1
            Column {
                repeat(rows) {
                    Row {
                        repeat(10) {
                            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                Icon(Glyphs.locationCityRounded, contentDescription = null, tint = Palette.Ink)
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Glyphs.mapRounded, contentDescription = null, tint = Palette.Sky, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Campus Map Placeholder",
                style = Typo.label(FontWeight.Bold).copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
            )
            Text(
                "Tap to select a building",
                style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.3f)),
            )
        }

        Box(Modifier.fillMaxSize().inkTap(shape) { onSpotTap?.invoke("ML") })
    }
}
