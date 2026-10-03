package co.gapfinder.mobile.screens.pairing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.gapfinder.mobile.data.PairingRepository
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.Pairing
import co.gapfinder.mobile.domain.PairingState
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val POLL_EVERY_MS = 3_000L

/** Pantalla "esperando respuesta" del match enviado (antes WaitingScreen). */
@Composable
fun PairingPendingPage(pairingId: Int, requesterId: Int, candidate: Member) {
    var turnedDown by remember { mutableStateOf(false) }

    // Consulta el estado del match cada 3 s hasta que se acepte o se rechace
    LaunchedEffect(Unit) {
        while (true) {
            delay(POLL_EVERY_MS)
            val fetched: Pairing? = try {
                PairingRepository.byId(pairingId)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                null // error de red puntual
            }
            val snapshot = fetched ?: continue

            when (snapshot.state) {
                PairingState.Live -> {
                    StackNavigator.swap(Destination.PairingConfirmed(pairingId = snapshot.id, candidate = candidate))
                    break
                }
                PairingState.Turned -> {
                    turnedDown = true
                    break
                }
                else -> Unit
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp)
    ) {
        // Columna ajustada al ancho de su contenido, igual que en Flutter
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RingedPhoto(
                url = candidate.avatarUrl,
                diameter = 96.dp,
                ring = BorderStroke(3.dp, Palette.Coral),
                fill = Palette.White,
                fallback = Glyphs.person,
                fallbackSize = 44.dp,
                fallbackTint = Palette.Ink.fade(0.3f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Waiting for ${candidate.name}...",
                style = Typo.heading(FontWeight.ExtraBold).asBody(20f, Palette.Ink),
            )
            Spacer(Modifier.height(24.dp))
            SpinnerRing(box = 56.dp, stroke = 4.dp, tint = Palette.Coral, track = Palette.Coral.fade(0.15f))
        }
    }

    if (turnedDown) {
        AlertDialog(
            onDismissRequest = { turnedDown = false },
            confirmButton = {
                TextButton(onClick = {
                    turnedDown = false
                    // Vuelve a la pantalla principal (popUntil '/home')
                    while (StackNavigator.top != Destination.Home && StackNavigator.back()) { }
                }) { Text("OK") }
            },
            title = { Text("No luck this time") },
            text = { Text("${candidate.name} is not available right now.") },
        )
    }
}
