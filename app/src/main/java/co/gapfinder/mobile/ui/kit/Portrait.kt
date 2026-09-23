package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.fade

/** Foto de perfil circular con ícono de respaldo (antes AvatarWidget). */
@Composable
fun Portrait(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    border: BorderStroke? = null,
) {
    val stroke = border ?: BorderStroke(2.dp, Palette.Ink.fade(0.12f))
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Palette.Fog)
            .border(stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrEmpty()) {
            Icon(
                imageVector = Glyphs.personRounded,
                contentDescription = null,
                tint = Palette.Ink.fade(0.3f),
                modifier = Modifier.size(size * 0.6f),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
            Box(Modifier.size(size).border(stroke, CircleShape))
        }
    }
}
