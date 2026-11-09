package co.gapfinder.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colores de marca, idénticos a los de la app iOS/Flutter.
 *
 * Fog, Ink y White se leen del tema activo ([LocalGapColors]): así `Palette.Fog` sigue
 * funcionando igual en todas las pantallas y cambia solo con el modo claro/oscuro. Por eso
 * solo se pueden usar dentro de una función @Composable.
 *
 * Coral, Teal y Sky son iguales en los dos modos, así que siguen siendo constantes y se pueden
 * usar en cualquier parte (por ejemplo, en Toaster.show desde una coroutine).
 */
object Palette {
    val Fog: Color @Composable get() = LocalGapColors.current.fog      // fondo
    val Ink: Color @Composable get() = LocalGapColors.current.ink      // texto / contraste
    val White: Color @Composable get() = LocalGapColors.current.white  // tarjetas / superficies
    val Coral = Color(0xFFFF3366)  // acento principal
    val Teal = Color(0xFF2EC4B6)   // acento secundario
    val Sky = Color(0xFF20A4F3)    // acento terciario
}

/** Atajo para `color.withValues(alpha: x)` de Flutter. */
fun Color.fade(alpha: Float): Color = copy(alpha = alpha)
