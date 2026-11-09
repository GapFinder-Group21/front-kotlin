package co.gapfinder.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import co.gapfinder.mobile.foundation.AmbientLightMonitor
import co.gapfinder.mobile.foundation.ThemeMode

/**
 * Decide si la app va en oscuro. Se suscribe al modo del sensor (Observer): cuando el sensor
 * cambia de modo, esto se recompone y el tema cambia. Ninguna pantalla sabe que hay un sensor.
 * Sin sensor (o sin lectura útil) se sigue el tema del sistema.
 */
@Composable
fun rememberDarkTheme(): Boolean {
    val sensed by AmbientLightMonitor.mode.collectAsState()
    val system = isSystemInDarkTheme()
    return when (sensed) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> system
    }
}
