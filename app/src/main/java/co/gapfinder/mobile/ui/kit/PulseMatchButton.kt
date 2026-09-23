package co.gapfinder.mobile.ui.kit

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.R
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo

/** Botón circular grande de MATCH con borde tricolor girando y latido (antes AnimatedMatchButton). */
@Composable
fun PulseMatchButton(onTap: (() -> Unit)?, enabled: Boolean = true) {
    val loop = rememberInfiniteTransition(label = "matchLoop")
    val spin by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "spin",
    )
    val beat by loop.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beat",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                val s = if (enabled) beat else 1f
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.4f
            }
            .plainTap(enabled = enabled, onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        // Borde de 3 colores rotando (3 arcos de 120°)
        Canvas(
            Modifier
                .size(160.dp)
                .graphicsLayer { rotationZ = if (enabled) spin else 0f }
        ) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Palette.Sky, 0f, 120f, false, style = stroke)
            drawArc(Palette.Teal, 120f, 120f, false, style = stroke)
            drawArc(Palette.Coral, 240f, 120f, false, style = stroke)
        }
        // Círculo interior oscuro con logo y texto
        Box(
            Modifier.size(148.dp).background(Palette.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.brand_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(80.dp),
                )
                Text(
                    text = "MATCH",
                    style = Typo.heading(FontWeight.Black).copy(
                        fontSize = 13.sp,
                        color = Palette.White,
                        letterSpacing = 2.sp,
                    ),
                )
            }
        }
    }
}
