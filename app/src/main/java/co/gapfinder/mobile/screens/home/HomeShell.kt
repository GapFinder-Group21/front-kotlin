package co.gapfinder.mobile.screens.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "HomeShell"
private const val POLL_EVERY_MS = 10_000L

/** Compone la pestaña siempre (como IndexedStack) pero solo la coloca si está seleccionada. */
private fun Modifier.onlyWhen(visible: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible) placeable.place(0, 0) }
}

/** Invitación que abrió el sondeo (no es estado de UI: cambiarla no debe recomponer). */
private class PollerState {
    var invite: Job? = null
}

/** Contenedor principal con las 5 pestañas y la barra inferior (antes MainScreen). */
@Composable
fun HomeShell() {
    var selected by rememberSaveable { mutableStateOf(DockTab.Schedule) }
    val handledInvites = remember { mutableSetOf<Int>() } // para no repetir pop-ups
    val poller = remember { PollerState() }
    val scope = rememberCoroutineScope()

    // Al entrar al home se lanzan en paralelo horario, gaps, notificaciones y recomendaciones.
    // Va en remember (antes de componer las pestañas) para que su primera carga ya las encuentre en vuelo.
    remember { SessionVault.memberId()?.let { HomeWarmup.start(scope, it) } }
    DisposableEffect(Unit) { onDispose { HomeWarmup.clear() } }

    // Revisa las notificaciones y abre la invitación si llegó una solicitud de match nueva
    suspend fun pollAlerts() {
        val me = SessionVault.memberId() ?: return
        // Una invitación a la vez: mientras haya una abierta no se busca otra
        if (poller.invite?.isActive == true) return
        try {
            // La primera revisión usa las notificaciones que ya trajo HomeWarmup
            val inbox = HomeWarmup.takeAlerts(me)?.getOrNull() ?: AlertRepository.inboxOf(me)
            val alert = inbox.firstOrNull { alert ->
                val pairingId = alert.relatedId
                pairingId != null && !alert.seen && alert.kind == AlertKind.PairingAsked && pairingId !in handledInvites
            } ?: return
            val pairingId = alert.relatedId ?: return

            Log.d(TAG, "🎯 MATCH REQUEST DETECTED! MatchID: $pairingId")
            handledInvites += pairingId
            // La invitación va en el scope del home, no en el del sondeo: si la app pasa a segundo
            // plano el sondeo se detiene, pero la invitación abierta no se cierra
            poller.invite = scope.launch {
                StackNavigator.goAndWait(Destination.PairingInvite(pairingId))
                // Se marca como leída cuando el usuario cierra la invitación
                try {
                    AlertRepository.markSeen(alert.id, me)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "Error marking read: $e")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "⚠️ POLLER ERROR: $e")
        }
    }

    // Sondeo cada 10 s en su propia coroutine. LaunchedEffect la cancela al salir del home
    // (cerrar sesión, resetTo) y repeatOnLifecycle la pausa mientras la app está en segundo plano
    // y la vuelve a arrancar al regresar.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        try {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d(TAG, "▶️ Polling started")
                while (isActive) {
                    pollAlerts()
                    delay(POLL_EVERY_MS)
                }
            }
        } finally {
            Log.d(TAG, "⏹️ Polling stopped")
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
