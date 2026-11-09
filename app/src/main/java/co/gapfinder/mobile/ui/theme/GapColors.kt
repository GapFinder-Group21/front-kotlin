package co.gapfinder.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores de marca de un tema. Mismos nombres que [Palette]: fog (fondo), ink (texto y contraste),
 * white (tarjetas y superficies) y los acentos coral, teal y sky.
 */
@Immutable
data class GapColors(
    val fog: Color,
    val ink: Color,
    val coral: Color,
    val teal: Color,
    val sky: Color,
    val white: Color,
    val isDark: Boolean,
)

/** Tema claro: los valores de siempre. */
val LightGapColors = GapColors(
    fog = Color(0xFFF6F7F8),
    ink = Color(0xFF011627),
    coral = Color(0xFFFF3366),
    teal = Color(0xFF2EC4B6),
    sky = Color(0xFF20A4F3),
    white = Color(0xFFFFFFFF),
    isDark = false,
)

/**
 * Tema oscuro: el fondo pasa a azul noche y el texto a claro. Las tarjetas ("white") quedan un
 * poco más claras que el fondo para que se distingan. Los acentos no cambian, porque cada uno
 * tiene un significado en la app (coral = GAP / acción, teal = confirmación, sky = invitaciones).
 */
val DarkGapColors = GapColors(
    fog = Color(0xFF0B1622),
    ink = Color(0xFFE6EDF3),
    coral = Color(0xFFFF3366),
    teal = Color(0xFF2EC4B6),
    sky = Color(0xFF20A4F3),
    white = Color(0xFF15263A),
    isDark = true,
)

/** Colores del tema activo. Los provee GapFinderTheme. */
val LocalGapColors = staticCompositionLocalOf { LightGapColors }
