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
import co.gapfinder.mobile.foundation.Connectivity
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "HomeShell"
private const val POLL_EVERY_MS = 10_000L

/** Always composes the tab (like IndexedStack) but only places it when it is selected. */
private fun Modifier.onlyWhen(visible: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible) placeable.place(0, 0) }
}

/** The invitation that opened the poll (not UI state: changing it must not trigger recomposition). */
private class PollerState {
    var invite: Job? = null
}

/** Main container with the 5 tabs and the bottom bar (formerly MainScreen). */
@Composable
fun HomeShell() {
    var selected by rememberSaveable { mutableStateOf(DockTab.Schedule) }
    val handledInvites = remember { mutableSetOf<Int>() } // to avoid showing the same pop-up twice
    val poller = remember { PollerState() }
    val scope = rememberCoroutineScope()

    // On entering home, schedule, gaps, notifications and recommendations are launched in parallel.
    // It goes inside remember (before composing the tabs) so their first load already finds the results in flight.
    remember { SessionVault.memberId()?.let { HomeWarmup.start(scope, it) } }
    DisposableEffect(Unit) { onDispose { HomeWarmup.clear() } }

    // Checks notifications and opens the invitation if a new match request arrived
    suspend fun pollAlerts() {
        val me = SessionVault.memberId() ?: return
        // One invitation at a time: while one is open, no new one is looked up
        if (poller.invite?.isActive == true) return
        try {
            // The first check uses the notifications already fetched by HomeWarmup
            val inbox = HomeWarmup.takeAlerts(me)?.getOrNull() ?: AlertRepository.inboxOf(me)
            val alert = inbox.firstOrNull { alert ->
                val pairingId = alert.relatedId
                pairingId != null && !alert.seen && alert.kind == AlertKind.PairingAsked && pairingId !in handledInvites
            } ?: return
            val pairingId = alert.relatedId ?: return

            Log.d(TAG, "MATCH REQUEST DETECTED! MatchID: $pairingId")
            handledInvites += pairingId
            // The invitation runs in the home scope, not the poll scope: if the app goes to the
            // background the poll stops, but an already-open invitation is not dismissed
            poller.invite = scope.launch {
                StackNavigator.goAndWait(Destination.PairingInvite(pairingId))
                // Marked as read when the user closes the invitation
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
            Log.d(TAG, "POLLER ERROR: $e")
        }
    }

    // Polls every 10 s in its own coroutine. LaunchedEffect cancels it when leaving home
    // (sign-out, resetTo) and repeatOnLifecycle pauses it while the app is in the background
    // and restarts it on return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        try {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // No network means no polling: collectLatest cancels the loop on network loss
                // and restarts it (with an immediate check) when connectivity returns
                Connectivity.online.collectLatest { online ->
                    if (!online) {
                        Log.d(TAG, "Polling paused (offline)")
                        return@collectLatest
                    }
                    Log.d(TAG, "Polling started")
                    // delay throws CancellationException when the loop is cancelled, ending it there
                    while (true) {
                        pollAlerts()
                        delay(POLL_EVERY_MS)
                    }
                }
            }
        } finally {
            Log.d(TAG, "Polling stopped")
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
