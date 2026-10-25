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

// How long the "back online" notice stays before hiding
private const val BACK_ONLINE_MS = 2_000L

/** What the connection banner shows. */
enum class BannerMode { Hidden, Offline, BackOnline }

/**
 * Banner state: Offline while there is no network and BackOnline for a couple of seconds
 * when it comes back (only if it was previously lost).
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
 * Global "no connection" banner. Sits above all screens (placed by AppStage)
 * and occupies the status bar strip. Per-screen notices
 * (what is blocked and what is saved for later) remain [OfflineNote].
 */
@Composable
fun OfflineBanner(mode: BannerMode, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = mode != BannerMode.Hidden,
        modifier = modifier,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        // Only hidden after BackOnline, so while it is closing it stays green
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
