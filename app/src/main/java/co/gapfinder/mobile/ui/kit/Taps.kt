package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/** Equivalente a InkWell: recorta a la forma y dibuja el ripple. */
@Composable
fun Modifier.inkTap(shape: Shape = RectangleShape, enabled: Boolean = true, onTap: (() -> Unit)?): Modifier =
    this
        .clip(shape)
        .clickable(enabled = enabled && onTap != null, onClick = { onTap?.invoke() })

/** Equivalente a GestureDetector: toque sin efecto visual. */
@Composable
fun Modifier.plainTap(enabled: Boolean = true, onTap: (() -> Unit)?): Modifier {
    val source = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled && onTap != null,
        onClick = { onTap?.invoke() },
    )
}
