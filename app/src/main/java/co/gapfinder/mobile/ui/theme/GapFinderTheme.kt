package co.gapfinder.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tema base. Replica los valores por defecto de Material 3 que usaba Flutter
 * (semilla deepPurple) para los widgets que no fijan color explícito.
 */
@Composable
fun GapFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF65558F),
            onPrimary = Color.White,
            surface = Color(0xFFFEF7FF),
            onSurface = Color(0xFF1D1B20),
            background = Color(0xFFFEF7FF),
        ),
        content = content,
    )
}
