package co.gapfinder.mobile.screens.pairing

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.Pairing
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "PairingInvite"

/** Invitación de match entrante (antes MatchInvitationScreen). */
@Composable
fun PairingInvitePage(pairingId: Int) {
    var invite by remember { mutableStateOf<Pairing?>(null) }
    var fetching by remember { mutableStateOf(true) }
    var answering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            invite = PairingRepository.byId(pairingId)
            fetching = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Error loading match invitation: $e")
            StackNavigator.back()
        }
    }

    val loaded = invite
    if (fetching || loaded == null) {
        BareLoadingPage()
        return
    }

    // Quien invita es el dueño del GAP que propuso el match
    val sender = loaded.proposer
    if (sender == null) {
        Box(Modifier.fillMaxSize().background(BareScaffoldTone), contentAlignment = Alignment.Center) {
            Text(text = "Match user not found", style = Typo.paragraph().asBody(14f, ThemeOnSurface))
        }
        return
    }

    val takeIt: () -> Unit = {
        answering = true
        scope.launch {
            try {
                val me = SessionVault.memberId() ?: run {
                    answering = false
                    return@launch
                }
                val updated = PairingRepository.approve(loaded.id, me)
                // Para quien recibe, el candidato es quien envió la solicitud
                StackNavigator.swap(
                    Destination.PairingConfirmed(pairingId = updated.id, candidate = updated.proposer ?: sender)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                answering = false
                Toaster.show("Error accepting match: ${e.dartText()}")
            }
        }
    }

    val passOnIt: () -> Unit = {
        answering = true
        scope.launch {
            try {
                val me = SessionVault.memberId() ?: run {
                    answering = false
                    return@launch
                }
                PairingRepository.decline(loaded.id, me)
                StackNavigator.back()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                answering = false
                Toaster.show("Error declining match: ${e.dartText()}")
            }
        }
    }

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
                .background(Palette.Sky) // azul para invitaciones
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                text = "Match Invitation!",
                style = Typo.heading(FontWeight.ExtraBold).asBody(18f, Palette.White),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            InviteCaption("SOMEONE WANTS TO CONNECT")
            Spacer(Modifier.height(16.dp))
            SenderCard(sender)
            Spacer(Modifier.height(32.dp))

            InviteCaption("PROPOSAL")
            Spacer(Modifier.height(12.dp))
            val shape = RoundedCornerShape(16.dp)
            // Sin ancho fijo: se ajusta al texto, como el Container de Flutter
            Box(
                Modifier
                    .background(Palette.White, shape)
                    .border(1.dp, Palette.Sky.fade(0.1f), shape)
                    .padding(16.dp)
            ) {
                Text(
                    text = "${sender.name} found an overlap in your schedules and wants to meet up!",
                    style = Typo.paragraph().asBody(14f, Palette.Ink.fade(0.7f)),
                )
            }

            Spacer(Modifier.height(48.dp))

            if (answering) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SpinnerRing(box = 36.dp, stroke = 4.dp)
                }
            } else {
                Column {
                    PillButton(
                        label = "Accept Invitation",
                        color = Palette.Teal,
                        onClick = takeIt,
                    )
                    Spacer(Modifier.height(12.dp))
                    QuietAction(
                        label = "Decline",
                        style = Typo.label(FontWeight.Bold).asButtonText(15f, Palette.Ink.fade(0.4f)),
                        onTap = passOnIt,
                        stretch = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteCaption(text: String) {
    Text(
        text = text,
        style = Typo.label(FontWeight.ExtraBold).asBody(11f, Palette.Ink.fade(0.4f), spacing = 1.2f),
    )
}

@Composable
private fun SenderCard(person: Member) {
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
            diameter = 70.dp,
            ring = BorderStroke(3.dp, Palette.Sky.fade(0.2f)),
            fill = Palette.Fog,
            fallback = Glyphs.personRounded,
            fallbackSize = 36.dp,
            fallbackTint = Palette.Ink.fade(0.2f),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = person.name,
                style = Typo.heading(FontWeight.Black).asBody(20f, Palette.Ink),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = person.career,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Typo.label(FontWeight.SemiBold).asBody(13f, Palette.Ink.fade(0.5f)),
            )
        }
    }
}
