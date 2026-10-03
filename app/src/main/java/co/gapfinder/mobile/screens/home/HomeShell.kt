package co.gapfinder.mobile.screens.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import co.gapfinder.mobile.data.AlertRepository
import co.gapfinder.mobile.domain.AlertKind
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.screens.hangouts.HangoutMapTab
import co.gapfinder.mobile.ui.kit.DockBar
import co.gapfinder.mobile.ui.kit.DockTab
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "HomeShell"
private const val POLL_EVERY_MS = 10_000L

/** Compone la pestaña siempre (como IndexedStack) pero solo la coloca si está seleccionada. */
private fun Modifier.onlyWhen(visible: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible) placeable.place(0, 0) }
}

/** Contenedor principal con las 5 pestañas y la barra inferior (antes MainScreen). */
@Composable
fun HomeShell() {
    var selected by rememberSaveable { mutableStateOf(DockTab.Schedule) }
    val handledInvites = remember { mutableSetOf<Int>() } // para no repetir pop-ups
    val scope = rememberCoroutineScope()

    // Revisa las notificaciones y abre la invitación si llegó una solicitud de match nueva
    suspend fun pollAlerts() {
        val me = SessionVault.memberId() ?: return
        try {
            val inbox = AlertRepository.inboxOf(me)
            for (alert in inbox) {
                val pairingId = alert.relatedId ?: continue
                if (alert.seen || alert.kind != AlertKind.PairingAsked || pairingId in handledInvites) continue

                Log.d(TAG, "🎯 MATCH REQUEST DETECTED! MatchID: $pairingId")
                handledInvites += pairingId
                scope.launch {
                    // Se marca como leída cuando el usuario cierra la invitación
                    StackNavigator.goAndWait(Destination.PairingInvite(pairingId))
                    runCatching { AlertRepository.markSeen(alert.id, me) }
                        .onFailure { Log.d(TAG, "Error marking read: $it") }
                }
                break // una invitación a la vez
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "⚠️ POLLER ERROR: $e")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(POLL_EVERY_MS)
            pollAlerts()
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Fog)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Schedule)) {
                TimetableTab(isActive = selected == DockTab.Schedule)
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Friends)) {
                FriendsTab(isActive = selected == DockTab.Friends)
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Match)) {
                PairingTab(isActive = selected == DockTab.Match)
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Map)) {
                HangoutMapTab()
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Suggest)) {
                RecommendationsTab(isActive = selected == DockTab.Suggest)
            }
        }
        DockBar(active = selected, onSelect = { selected = it })
    }
}
