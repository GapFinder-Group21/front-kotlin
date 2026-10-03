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
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.PairingFocus
import co.gapfinder.mobile.ui.kit.InkHeader
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

    LaunchedEffect(windowId) {
        outcome = try {
            Fetch.Done(WindowRepository.byId(windowId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Fetch.Broken(e)
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

                is Fetch.Broken -> Text(
                    text = "Error: ${state.cause.dartStyleText()}",
                    style = Typo.paragraph().copy(fontSize = 14.sp, color = InheritedInk),
                    modifier = Modifier.align(Alignment.Center),
                )

                is Fetch.Done -> WindowSummary(state.value)
            }
        }
    }
}

@Composable
private fun WindowSummary(window: FreeWindow) {
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

        PillButton(
            label = "Find Match for this GAP",
            color = Palette.Coral,
            onClick = {
                // La búsqueda parte del dueño del GAP con el modo por defecto
                window.ownerId?.let { owner ->
                    StackNavigator.go(Destination.PairingSearch(owner, PairingFocus.Career))
                }
            },
        )
    }
}
