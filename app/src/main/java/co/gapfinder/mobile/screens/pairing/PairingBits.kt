package co.gapfinder.mobile.screens.pairing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.kit.inkTap
import coil.compose.AsyncImage

// Piezas compartidas por las pantallas de match.

/** Fondo por defecto de un Scaffold de Flutter sin color. */
internal val BareScaffoldTone = Color(0xFFFEF7FF)

/** Color de texto por defecto del tema (onSurface). */
internal val ThemeOnSurface = Color(0xFF1D1B20)

private val EvenLeading = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Hereda lo que Flutter toma de DefaultTextStyle (bodyMedium M3):
 * letterSpacing 0.25 y height 1.43, salvo que Dart los fije.
 */
internal fun TextStyle.asBody(size: Float = 14f, tint: Color, spacing: Float = 0.25f): TextStyle =
    copy(
        fontSize = size.sp,
        color = tint,
        letterSpacing = spacing.sp,
        lineHeight = 1.43.em,
        lineHeightStyle = EvenLeading,
    )

/** Texto dentro de un botón (hereda labelLarge: letterSpacing 0.1). */
internal fun TextStyle.asButtonText(size: Float = 14f, tint: Color): TextStyle =
    asBody(size, tint, 0.1f)

/** Equivalente al `$e` de Dart para una Exception. */
internal fun Throwable.dartText(): String = "Exception: $message"

/** Sombra estilo BoxShadow (mismo sigma que Flutter). */
internal fun Modifier.dropGlow(tint: Color, blur: Dp, dy: Dp, corner: Dp, base: Color): Modifier =
    drawBehind {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = base.toArgb()
                setShadowLayer(blur.toPx(), 0f, dy.toPx(), tint.toArgb())
            }
            val r = corner.toPx()
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }

/**
 * Spinner indeterminado con la geometría de Flutter (el trazo se centra en el borde de la caja,
 * así que se agranda en `stroke` para que el círculo tenga el mismo radio).
 */
@Composable
internal fun SpinnerRing(box: Dp, stroke: Dp, tint: Color? = null, track: Color = Color.Transparent) {
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        if (tint != null) {
            CircularProgressIndicator(
                modifier = Modifier.requiredSize(box + stroke),
                color = tint,
                strokeWidth = stroke,
                trackColor = track,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.requiredSize(box + stroke),
                strokeWidth = stroke,
                trackColor = track,
            )
        }
    }
}

/** Pantalla de carga: Scaffold sin color + CircularProgressIndicator centrado. */
@Composable
internal fun BareLoadingPage() {
    Box(
        Modifier
            .fillMaxSize()
            .background(BareScaffoldTone),
        contentAlignment = Alignment.Center,
    ) {
        SpinnerRing(box = 36.dp, stroke = 4.dp)
    }
}

/** Foto circular con aro (BoxDecoration circle + DecorationImage) e ícono si no hay url. */
@Composable
internal fun RingedPhoto(
    url: String?,
    diameter: Dp,
    ring: BorderStroke,
    fill: Color,
    fallback: ImageVector,
    fallbackSize: Dp,
    fallbackTint: Color,
) {
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(fallback, contentDescription = null, tint = fallbackTint, modifier = Modifier.size(fallbackSize))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // El aro va por encima de la imagen
        Box(
            Modifier
                .size(diameter)
                .border(ring, CircleShape)
        )
    }
}

/**
 * TextButton de Material 3: mínimo 64x40, zona táctil de 48 de alto, forma de píldora.
 * `stretch` = SizedBox(width: double.infinity).
 */
@Composable
internal fun QuietAction(
    label: String,
    style: TextStyle,
    onTap: (() -> Unit)?,
    stretch: Boolean = false,
    inner: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    val pill = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .then(if (stretch) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (stretch) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp)
                .inkTap(pill, onTap = onTap)
                .padding(inner),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = style)
        }
    }
}
