package co.gapfinder.mobile.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Utilidades compartidas por las pestañas del home.

/** Color de texto por defecto de Flutter M3 (onSurface) cuando el estilo no fija color. */
internal val InheritedInk = Color(0xFF1D1B20)

/** Estado de una carga asíncrona (equivalente a lo que pinta un FutureBuilder). */
internal sealed interface Fetch<out T> {
    data object Waiting : Fetch<Nothing>
    data class Done<T>(val value: T) : Fetch<T>
    data class Broken(val cause: Throwable) : Fetch<Nothing>
}

/**
 * Llama a [onWake] solo cuando la pestaña pasa de inactiva a activa
 * (mismo criterio que `widget.isActive && !oldWidget.isActive`).
 */
@Composable
internal fun OnTabWake(isActive: Boolean, onWake: () -> Unit) {
    val latest by rememberUpdatedState(onWake)
    var previouslyOn by remember { mutableStateOf(isActive) }
    LaunchedEffect(isActive) {
        if (isActive && !previouslyOn) latest()
        previouslyOn = isActive
    }
}

/** Hora en formato 12h sin AM/PM: "8:00", "2:30". */
internal fun twelveHourClock(hour: Int, minute: Int): String {
    val h = hour % 12
    return "${if (h == 0) 12 else h}:${minute.toString().padStart(2, '0')}"
}

/** Texto de error como lo mostraba Dart con `${error}` sobre un Exception. */
internal fun Throwable.dartStyleText(): String = "Exception: ${message ?: toString()}"
