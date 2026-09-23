package co.gapfinder.mobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.R

private val Montserrat = FontFamily(
    Font(R.font.montserrat_w400, FontWeight.Normal),
    Font(R.font.montserrat_w500, FontWeight.Medium),
    Font(R.font.montserrat_w600, FontWeight.SemiBold),
    Font(R.font.montserrat_w700, FontWeight.Bold),
    Font(R.font.montserrat_w800, FontWeight.ExtraBold),
    Font(R.font.montserrat_w900, FontWeight.Black),
)

private val Magra = FontFamily(
    Font(R.font.magra_regular, FontWeight.Normal),
    Font(R.font.magra_bold, FontWeight.Bold),
)

private val Cambay = FontFamily(
    Font(R.font.cambay_regular, FontWeight.Normal),
    Font(R.font.cambay_bold, FontWeight.Bold),
)

private val flatPlatform = PlatformTextStyle(includeFontPadding = false)

/**
 * En Flutter todos los estilos heredan de bodyMedium (M3): altura 1.43 y
 * letterSpacing 0.25. Se aplican como base; cada pantalla puede sobreescribirlos.
 */
private fun base(family: FontFamily, weight: FontWeight) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = 14.sp,
    lineHeight = 1.43.em,
    letterSpacing = 0.25.sp,
    platformStyle = flatPlatform,
)

/**
 * Tipografías de la app:
 *  - [heading]   Montserrat  → títulos / displays
 *  - [label]     Magra       → subtítulos / botones
 *  - [paragraph] Cambay      → cuerpo de texto
 */
object Typo {
    fun heading(weight: FontWeight = FontWeight.Bold) =
        base(Montserrat, weight)

    fun label(weight: FontWeight = FontWeight.Normal) =
        base(Magra, weight)

    fun paragraph(weight: FontWeight = FontWeight.Normal) =
        base(Cambay, weight)
}
