package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.gapfinder.mobile.foundation.ServerEnv
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.fade
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/** URL absoluta de la foto: el backend puede mandarla completa o como ruta ("/uploads/…"). */
fun resolvePhotoUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    return ServerEnv.BASE_URL.trimEnd('/') + "/" + value.trimStart('/')
}

/**
 * Foto de una open table. Pasa por el ImageLoader de la app (ImageCaching), así que la primera
 * vez se baja y después sale del caché en memoria o en disco, también sin red.
 * Si la mesa no tiene foto no ocupa espacio.
 */
@Composable
fun HangoutPhoto(url: String?, modifier: Modifier = Modifier, height: Dp = 140.dp, corner: Dp = 12.dp) {
    val resolved = remember(url) { resolvePhotoUrl(url) } ?: return
    val context = LocalContext.current
    val request = remember(resolved) {
        ImageRequest.Builder(context)
            .data(resolved)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = "Open table photo",
        placeholder = ColorPainter(Palette.Ink.fade(0.06f)),
        error = ColorPainter(Palette.Ink.fade(0.06f)),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner)),
    )
}
