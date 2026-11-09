package co.gapfinder.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Valores por defecto de Material 3 que usaba Flutter (semilla deepPurple). */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF65558F),
    onPrimary = Color.White,
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    background = Color(0xFFFEF7FF),
)

/** Versión oscura, con el fondo azul noche de [DarkGapColors]. */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    surface = DarkGapColors.white,
    onSurface = Color(0xFFE6E0E9),
    background = DarkGapColors.fog,
    onBackground = Color(0xFFE6E0E9),
)

/**
 * Tema de la app. [darkTheme] lo decide HostActivity con el sensor de luz o la elección manual.
 * Cambia a la vez el colorScheme de Material y la paleta de marca (Palette.Fog, Ink, White).
 */
@Composable
fun GapFinderTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    // Los colores de marca van por LocalGapColors; Palette los lee de ahí
    CompositionLocalProvider(LocalGapColors provides if (darkTheme) DarkGapColors else LightGapColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content,
        )
    }
}
