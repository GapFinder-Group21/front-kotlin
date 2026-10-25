package co.gapfinder.mobile.screens.pairing

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.gapfinder.mobile.R
import co.gapfinder.mobile.data.PairingRepository
import co.gapfinder.mobile.data.WindowRepository
import co.gapfinder.mobile.domain.PairingFocus
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException

/** Notice shown when the search does not lead to any screen. */
private sealed interface LookupOutcome {
    data object NobodyFree : LookupOutcome
    data class Broke(val detail: String) : LookupOutcome
}

/** Pantalla "buscando match" (antes SearchingMatchScreen). */
@Composable
fun PairingSearchPage(memberId: Int, focus: PairingFocus) {
    var abandoned by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<LookupOutcome?>(null) }

    LaunchedEffect(Unit) {
        // Candidates are calculated on the server: no searching without network
        if (!Connectivity.isOnline()) {
            outcome = LookupOutcome.Broke("No connection. Finding a match needs the server. Try again when you're back online.")
            return@LaunchedEffect
        }
        try {
            // The match is built on the GAP the user has running right now
            val now = Chrono.now()
            val current = WindowRepository.weekOf(memberId).firstOrNull { it.isLiveAt(now) }
            if (abandoned) return@LaunchedEffect

            if (current == null) {
                outcome = LookupOutcome.NobodyFree
                return@LaunchedEffect
            }

            val prospects = PairingRepository.prospects(current.id, focus)
            if (abandoned) return@LaunchedEffect

            if (prospects.isEmpty()) {
                // Before saying there is nothing, check if there is already an accepted match
                val ongoing = PairingRepository.ongoingFor(memberId)
                val peer = ongoing?.peerOf(memberId)
                if (ongoing != null && peer != null) {
                    if (abandoned) return@LaunchedEffect
                    StackNavigator.swap(Destination.PairingConfirmed(pairingId = ongoing.id, candidate = peer))
                    return@LaunchedEffect
                }
                outcome = LookupOutcome.NobodyFree
                return@LaunchedEffect
            }

            // Candidates arrive sorted by score: the best one is proposed
            StackNavigator.swap(
                Destination.PairingCandidate(
                    memberId = memberId,
                    currentWindowId = current.id,
                    prospect = prospects.first(),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (abandoned) return@LaunchedEffect
            outcome = LookupOutcome.Broke(e.dartText())
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
        // The column adjusts to the width of its widest child, like in Flutter
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                SpinnerRing(box = 100.dp, stroke = 5.dp, tint = Palette.Coral, track = Color(0x1FFF3366))
                Box(
                    Modifier
                        .size(76.dp)
                        .background(Palette.Coral, CircleShape)
                        .padding(18.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.brand_mark),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Finding your match...",
                style = Typo.heading(FontWeight.ExtraBold).asBody(20f, Palette.Ink),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Comparing schedules and shared interests",
                textAlign = TextAlign.Center,
                style = Typo.paragraph().asBody(14f, Palette.Ink.fade(0.5f)),
            )
            Spacer(Modifier.height(40.dp))
            QuietAction(
                label = "Cancel",
                style = Typo.paragraph(FontWeight.Bold).asButtonText(14f, Palette.Ink.fade(0.4f)),
                onTap = {
                    abandoned = true
                    StackNavigator.back()
                },
            )
        }
    }

    outcome?.let { shown ->
        AlertDialog(
            onDismissRequest = { outcome = null },
            confirmButton = {
                TextButton(onClick = {
                    // Closes the dialog and then this screen
                    outcome = null
                    StackNavigator.back()
                }) { Text("OK") }
            },
            title = {
                Text(
                    when (shown) {
                        LookupOutcome.NobodyFree -> "No matches found"
                        is LookupOutcome.Broke -> "Something went wrong"
                    }
                )
            },
            text = {
                Text(
                    when (shown) {
                        LookupOutcome.NobodyFree -> "Nobody available right now with this mode. Try another one."
                        is LookupOutcome.Broke -> shown.detail
                    }
                )
            },
        )
    }
}
