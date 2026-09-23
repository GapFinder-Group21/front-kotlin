package co.gapfinder.mobile.screens.hangouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

// Piezas compartidas por las pantallas de mesas abiertas.

/** Color de texto por defecto de Material 3 (onSurface). */
internal val SurfaceText = Color(0xFF1D1B20)

/** Primario del tema (semilla deepPurple). */
internal val ThemePrimary = Color(0xFF65558F)

/** Colors.grey / grey[400] de Flutter. */
internal val MidGrey = Color(0xFF9E9E9E)
internal val LightGrey = Color(0xFFBDBDBD)

/** Fondo de Scaffold sin color explícito. */
internal val BareScaffold = Color(0xFFFEF7FF)

/** Equivale a `e.toString()` de una Exception de Dart. */
internal fun Throwable.asDartString(): String = "Exception: ${message ?: ""}"

/** Mensaje sin el prefijo "Exception: " (replaceAll). */
internal fun Throwable.plainText(): String = message ?: toString()

/**
 * Botón tipo ElevatedButton de pastilla (elevación 0, alto mínimo 40, ancho mínimo 64,
 * con 4dp arriba/abajo del área táctil de 48).
 */
@Composable
internal fun PillAction(
    label: String,
    fill: Color,
    textStyle: TextStyle,
    horizontal: Dp,
    vertical: Dp,
    onTap: () -> Unit,
    leading: ImageVector? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp)
                .background(fill, shape)
                .inkTap(shape) { onTap() }
                .padding(horizontal = horizontal, vertical = vertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = Palette.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = textStyle.copy(color = Palette.White))
        }
    }
}

/** Rótulo "DESCRIPTION" de las tarjetas de mesa. */
@Composable
internal fun BlurbCaption() {
    Text(
        text = "DESCRIPTION",
        style = Typo.label(FontWeight.ExtraBold).copy(
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = Palette.Ink.fade(0.35f),
        ),
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
    )
}

/** Caja con la descripción entre comillas en cursiva. */
@Composable
internal fun BlurbBox(text: String, edge: Color) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .background(Palette.Fog, shape)
            .border(1.5.dp, edge, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "\"$text\"",
            style = Typo.paragraph().copy(
                fontSize = 13.sp,
                color = Palette.Ink.fade(0.65f),
                fontStyle = FontStyle.Italic,
            ),
        )
    }
}

/** Etiqueta con el título de la actividad (fondo celeste suave). */
@Composable
internal fun PastimeTag(title: String) {
    Text(
        text = title,
        style = Typo.label(FontWeight.Bold).copy(fontSize = 11.sp, color = Palette.Sky),
        modifier = Modifier
            .background(Palette.Sky.fade(0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}
