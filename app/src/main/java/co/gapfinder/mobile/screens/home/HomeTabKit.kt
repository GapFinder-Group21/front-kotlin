package co.gapfinder.mobile.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.io.IOException

// Shared utilities for the home tabs.

/** Default Flutter M3 text color (onSurface) used when the style does not set a color. */
internal val InheritedInk = Color(0xFF1D1B20)

/** State of an asynchronous load (equivalent to what a FutureBuilder renders). */
internal sealed interface Fetch<out T> {
    data object Waiting : Fetch<Nothing>
    data class Done<T>(val value: T) : Fetch<T>
    data class Broken(val cause: Throwable) : Fetch<Nothing>
}

/**
 * Calls [onWake] only when the tab transitions from inactive to active
 * (same criterion as `widget.isActive && !oldWidget.isActive`).
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

/** Time in 12-hour format without AM/PM: "8:00", "2:30". */
internal fun twelveHourClock(hour: Int, minute: Int): String {
    val h = hour % 12
    return "${if (h == 0) 12 else h}:${minute.toString().padStart(2, '0')}"
}

/** Error text as Dart displayed it with `${error}` on an Exception. */
internal fun Throwable.dartStyleText(): String = "Exception: ${message ?: toString()}"

/** No network and nothing cached to show. The message explains what is happening with the screen. */
internal class OfflineFault(message: String) : IOException(message)
