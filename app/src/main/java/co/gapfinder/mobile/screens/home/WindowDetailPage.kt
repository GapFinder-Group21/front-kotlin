package co.gapfinder.mobile.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.WindowRepository
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.PairingFocus
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.rememberOnline
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.OfflineNote
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException

/** Detalle de un GAP con acceso directo a buscar match (antes GapDetailScreen). */
@Composable
fun WindowDetailPage(windowId: Int) {
    var outcome by remember { mutableStateOf<Fetch<FreeWindow>>(Fetch.Waiting) }
    val online by rememberOnline()

    // También corre cuando cambia la red: sin red sale de Room y al volver se actualiza del backend
    LaunchedEffect(windowId, online) {
        // El GAP ya está en Room con el horario de la semana
        val saved = savedWindow(windowId)
        if (!Connectivity.isOnline()) {
            outcome = if (saved != null) Fetch.Done(saved)
            else Fetch.Broken(OfflineFault("No connection. This GAP isn't saved on your phone yet."))
            return@LaunchedEffect
        }
        if (saved != null) outcome = Fetch.Done(saved)
        try {
            outcome = Fetch.Done(WindowRepository.byId(windowId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (saved == null) outcome = Fetch.Broken(e)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
    ) {
        InkHeader(title = "GAP Details", showBack = true)

        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
            when (val state = outcome) {
                Fetch.Waiting -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))

                is Fetch.Broken -> if (state.cause is OfflineFault) {
                    OfflineNote(
                        text = state.cause.message ?: "",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    Text(
                        text = "Error: ${state.cause.dartStyleText()}",
                        style = Typo.paragraph().copy(fontSize = 14.sp, color = InheritedInk),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is Fetch.Done -> WindowSummary(state.value, online)
            }
        }
    }
}

@Composable
private fun WindowSummary(window: FreeWindow, online: Boolean) {
    val from = twelveHourClock(window.startsAt.hour, window.startsAt.minute)
    val to = twelveHourClock(window.endsAt.hour, window.endsAt.minute)
    val shape = RoundedCornerShape(16.dp)

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.White, shape)
                .border(1.5.dp, Palette.Coral.fade(0.3f), shape)
                .padding(21.5.dp) // padding de Flutter + ancho del borde
        ) {
            Text(
                text = "FREE TIME GAP",
                style = Typo.label(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Coral),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$from – $to",
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 22.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${window.durationMinutes} minutes available",
                style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
            )
        }

        Spacer(Modifier.weight(1f))

        // Buscar match necesita al servidor: sin red el botón se apaga y se explica por qué
        if (!online) {
            OfflineNote("No connection · showing your saved GAP. Finding a match needs the server, so it turns on when you're back online.")
            Spacer(Modifier.height(12.dp))
        }
        PillButton(
            label = "Find Match for this GAP",
            color = Palette.Coral,
            onClick = if (online) ({
                // La búsqueda parte del dueño del GAP con el modo por defecto
                window.ownerId?.let { owner ->
                    StackNavigator.go(Destination.PairingSearch(owner, PairingFocus.Career))
                }
            }) else null,
        )
    }
}

/** El GAP guardado en Room con el horario de la semana, si está. */
private suspend fun savedWindow(windowId: Int): FreeWindow? {
    val me = SessionVault.memberId() ?: return null
    return LocalStore.timetableOf(me)?.windows?.firstOrNull { it.id == windowId }
}
