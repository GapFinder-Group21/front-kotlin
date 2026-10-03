package co.gapfinder.mobile.screens.pairing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.gapfinder.mobile.domain.EnergyLevel
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.Pastime
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

// Colores de Flutter usados aquí
private val Grey800 = Color(0xFF424242)
private val White24 = Color(0x3DFFFFFF)
private val White54 = Color(0x8AFFFFFF)
private val White70 = Color(0xB3FFFFFF)

/**
 * Pantalla "IT'S A MATCH!" (antes ItsAMatchScreen). Muestra el teléfono de la otra persona
 * para que se pongan de acuerdo por fuera de la app.
 */
@Composable
fun PairingConfirmedPage(pairingId: Int, candidate: Member, suggested: Pastime?, chosen: Pastime?) {
    val featured = suggested ?: chosen

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "IT'S A MATCH!",
                style = Typo.heading(FontWeight.Black).asBody(32f, Palette.Coral, spacing = 2f),
            )
            Spacer(Modifier.height(40.dp))
            RingedPhoto(
                url = candidate.avatarUrl,
                diameter = 120.dp,
                ring = BorderStroke(4.dp, Palette.White),
                fill = Grey800,
                fallback = Glyphs.person,
                fallbackSize = 60.dp,
                fallbackTint = White24,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "You and ${candidate.name} are free at the same time.",
                textAlign = TextAlign.Center,
                style = Typo.paragraph().asBody(16f, White70),
            )
            Spacer(Modifier.height(20.dp))
            PhoneBadge(candidate.phoneNumber)

            if (featured != null) {
                Spacer(Modifier.height(30.dp))
                Text(
                    text = "SUGGESTED ACTIVITY",
                    style = Typo.label(FontWeight.ExtraBold).asBody(11f, Palette.White.fade(0.4f), spacing = 1.5f),
                )
                Spacer(Modifier.height(12.dp))
                PastimeCard(featured)
            }

            Spacer(Modifier.height(48.dp))
            BackHomeButton {
                // Se vuelve a un Home limpio: no quedan pantallas del flujo de match debajo
                StackNavigator.resetTo(Destination.Home)
            }
        }
    }
}

/** Teléfono de la otra persona, o un aviso si no lo registró. */
@Composable
private fun PhoneBadge(phoneNumber: String?) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .background(Palette.White.fade(0.1f), shape)
            .border(1.dp, Palette.White.fade(0.2f), shape)
            .padding(horizontal = 17.dp, vertical = 13.dp), // padding de Flutter + ancho del borde
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.phone, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = phoneNumber?.takeIf { it.isNotEmpty() } ?: "No phone number available",
            style = Typo.heading(FontWeight.Bold).asBody(16f, Palette.White),
        )
    }
}

@Composable
private fun PastimeCard(pastime: Pastime) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.White.fade(0.05f), shape)
            .border(1.dp, Palette.White.fade(0.1f), shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EnergyBadge(pastime.energy)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = pastime.name,
                    style = Typo.heading(FontWeight.ExtraBold).asBody(18f, Palette.White),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // Dart: effortType.name.toLowerCase() → "low" / "medium" / "high"
                    text = "${pastime.durationMinutes} minutes · ${pastime.energy.wire.lowercase()}",
                    style = Typo.paragraph().asBody(13f, White54),
                )
            }
        }
        val hobby = pastime.hobby
        if (hobby != null) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                val pill = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .background(Palette.Sky.fade(0.2f), pill)
                        .border(1.dp, Palette.Sky.fade(0.3f), pill)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = hobby.name,
                        style = Typo.paragraph(FontWeight.Bold).asBody(11f, Palette.Sky),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnergyBadge(level: EnergyLevel) {
    val (glyph: ImageVector, hue: Color) = when (level) {
        EnergyLevel.Calm -> Glyphs.selfImprovementRounded to Palette.Sky
        EnergyLevel.Regular -> Glyphs.directionsWalkRounded to Palette.Teal
        EnergyLevel.Lively -> Glyphs.boltRounded to Palette.Coral
    }
    Box(
        Modifier
            .size(44.dp)
            .background(hue.fade(0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, contentDescription = null, tint = hue, modifier = Modifier.size(24.dp))
    }
}

/** ElevatedButton coral de ancho completo (elevación 1 por defecto en M3). */
@Composable
private fun BackHomeButton(onTap: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .background(Palette.Coral, shape)
            .inkTap(shape, onTap = onTap)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Back to Schedule",
            style = Typo.paragraph(FontWeight.Bold).asButtonText(16f, Palette.White),
        )
    }
}
