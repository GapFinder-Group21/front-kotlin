package co.gapfinder.mobile.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.foundation.rememberOnline
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import kotlinx.coroutines.delay

// Cuánto se queda el aviso de "volvió la conexión" antes de esconderse
private const val BACK_ONLINE_MS = 2_000L

/** Qué muestra el banner de conexión. */
enum class BannerMode { Hidden, Offline, BackOnline }

/**
 * Estado del banner: Offline mientras no hay red y BackOnline un par de segundos
 * cuando vuelve (solo si antes se había perdido).
 */
@Composable
fun rememberBannerMode(): BannerMode {
    val online by rememberOnline()
    var mode by remember { mutableStateOf(if (online) BannerMode.Hidden else BannerMode.Offline) }

    LaunchedEffect(online) {
        if (!online) {
            mode = BannerMode.Offline
        } else if (mode == BannerMode.Offline) {
            mode = BannerMode.BackOnline
            delay(BACK_ONLINE_MS)
            mode = BannerMode.Hidden
        }
    }
    return mode
}

/**
 * Banner global de "sin conexión". Va arriba de todas las pantallas (lo pone AppStage)
 * y ocupa la franja de la barra de estado. Los avisos propios de cada pantalla
 * (qué se bloquea y qué se guarda para después) siguen siendo [OfflineNote].
 */
@Composable
fun OfflineBanner(mode: BannerMode, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = mode != BannerMode.Hidden,
        modifier = modifier,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        // Solo se esconde después de BackOnline, así que mientras se cierra sigue en verde
        val backOnline = mode != BannerMode.Offline
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (backOnline) Palette.Teal else Palette.Ink)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (backOnline) Glyphs.checkRounded else Glyphs.cloudOff,
                contentDescription = null,
                tint = Palette.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (backOnline) "Back online" else "No connection · showing saved data",
                style = Typo.paragraph(FontWeight.SemiBold).copy(fontSize = 13.sp, color = Palette.White),
            )
        }
    }
}
