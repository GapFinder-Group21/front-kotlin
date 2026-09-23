package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/**
 * Barra superior azul noche (antes CustomTopBar).
 * El padding va por fuera del área segura, igual que en Flutter.
 */
@Composable
fun InkHeader(
    title: String,
    subtitle: String? = null,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderContent(title, subtitle, showBack, onBack, iconSize = 20.dp, gap = 1.dp, tight = false)
    }
}

/** Variante con el padding dentro del área segura (antes TopBar). */
@Composable
fun NightHeader(
    title: String,
    subtitle: String? = null,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderContent(title, subtitle, showBack, onBack, iconSize = 22.dp, gap = 2.dp, tight = true)
    }
}

@Composable
private fun HeaderContent(
    title: String,
    subtitle: String?,
    showBack: Boolean,
    onBack: (() -> Unit)?,
    iconSize: Dp,
    gap: Dp,
    tight: Boolean,
) {
    if (showBack) {
        Icon(
            imageVector = Glyphs.backIos,
            contentDescription = "Back",
            tint = Palette.Fog,
            modifier = Modifier
                .inkTap(CircleShape) { if (onBack != null) onBack() else StackNavigator.back() }
                .size(iconSize),
        )
        Spacer(Modifier.width(12.dp))
    }
    Column {
        Text(
            text = title,
            style = Typo.heading(FontWeight.ExtraBold).copy(
                fontSize = 16.sp,
                color = Palette.Fog,
                letterSpacing = 0.5.sp,
                lineHeight = if (tight) 1.1.em else 1.43.em,
            ),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(gap))
            Text(
                text = subtitle,
                style = Typo.label().copy(
                    fontSize = 12.sp,
                    color = Palette.Fog.fade(0.65f),
                    lineHeight = if (tight) 1.1.em else 1.43.em,
                ),
            )
        }
    }
}
