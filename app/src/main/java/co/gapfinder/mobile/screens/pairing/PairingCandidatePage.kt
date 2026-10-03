package co.gapfinder.mobile.screens.pairing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.gapfinder.mobile.data.PairingRepository
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.PairingProspect
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Hora en formato 12h sin AM/PM ("3:05"). */
private fun twelveHourClock(moment: LocalDateTime): String {
    val h = moment.hour % 12
    val shownHour = if (h == 0) 12 else h
    return "$shownHour:${moment.minute.toString().padStart(2, '0')}"
}

/**
 * Pantalla "¡Match encontrado!" (antes MatchFoundScreen).
 * [currentWindowId] es el GAP de quien busca; el del candidato viene en [prospect].
 */
@Composable
fun PairingCandidatePage(memberId: Int, currentWindowId: Int, prospect: PairingProspect) {
    var requesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val theirWindow = prospect.acceptorWindow
    val candidate = theirWindow.owner

    val sendInvite: () -> Unit = {
        scope.launch {
            requesting = true
            try {
                // Sin el usuario del candidato no hay a quién esperar en la pantalla siguiente
                val person = candidate ?: throw IllegalStateException("Candidate user not found")
                val created = PairingRepository.propose(currentWindowId, theirWindow.id, prospect.score)
                StackNavigator.swap(Destination.PairingPending(created.id, memberId, person))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                requesting = false
                Toaster.show("Error sending request: ${e.dartText()}")
            }
        }
    }

    val stepBack: () -> Unit = { StackNavigator.back() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Palette.Ink)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Match Found!",
                style = Typo.heading(FontWeight.ExtraBold).asBody(16f, Palette.White),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (candidate != null) ProspectCard(candidate)

            Spacer(Modifier.height(24.dp))

            WindowSummary(theirWindow)

            Spacer(Modifier.height(40.dp))

            PillButton(
                label = "Connect Now",
                color = Palette.Coral,
                onClick = if (requesting) null else sendInvite,
            )
            Spacer(Modifier.height(12.dp))
            QuietAction(
                label = "Find someone else",
                style = Typo.label(FontWeight.Bold).asButtonText(15f, Palette.Ink.fade(0.4f)),
                onTap = if (requesting) null else stepBack,
                stretch = true,
                inner = PaddingValues(vertical = 14.dp),
            )
        }
    }
}

/** Tiempo libre del candidato. */
@Composable
private fun WindowSummary(window: FreeWindow) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.White, shape)
            .border(1.dp, Palette.Ink.fade(0.08f), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Glyphs.accessTimeFilledRounded,
                contentDescription = null,
                tint = Palette.Teal,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Available for the next ${window.durationMinutes} min",
                style = Typo.paragraph(FontWeight.Bold).asBody(13f, Palette.Ink),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Until ${twelveHourClock(window.endsAt)}",
            style = Typo.paragraph().asBody(12f, Palette.Ink.fade(0.5f)),
        )
    }
}

@Composable
private fun ProspectCard(person: Member) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropGlow(Palette.Ink.fade(0.04f), blur = 20.dp, dy = 8.dp, corner = 20.dp, base = Palette.White)
            .background(Palette.White, shape)
            .border(1.dp, Palette.Ink.fade(0.08f), shape)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingedPhoto(
            url = person.avatarUrl,
            diameter = 80.dp,
            ring = BorderStroke(3.dp, Palette.Teal.fade(0.2f)),
            fill = Palette.Fog,
            fallback = Glyphs.personRounded,
            fallbackSize = 40.dp,
            fallbackTint = Palette.Ink.fade(0.2f),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = person.name,
                style = Typo.heading(FontWeight.Black).asBody(22f, Palette.Ink),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = person.career,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Typo.label(FontWeight.SemiBold).asBody(13f, Palette.Ink.fade(0.5f)),
            )
            Text(
                text = "Semester ${person.semester}",
                style = Typo.paragraph().asBody(13f, Palette.Ink.fade(0.4f)),
            )
        }
    }
}
