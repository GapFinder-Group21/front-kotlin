package co.gapfinder.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/** Colores de marca, idénticos a los de la app iOS/Flutter. */
object Palette {
    val Fog = Color(0xFFF6F7F8)    // fondo claro
    val Ink = Color(0xFF011627)    // contraste / azul noche
    val Coral = Color(0xFFFF3366)  // acento principal
    val Teal = Color(0xFF2EC4B6)   // acento secundario
    val Sky = Color(0xFF20A4F3)    // acento terciario
    val White = Color(0xFFFFFFFF)
}

/** Atajo para `color.withValues(alpha: x)` de Flutter. */
fun Color.fade(alpha: Float): Color = copy(alpha = alpha)
