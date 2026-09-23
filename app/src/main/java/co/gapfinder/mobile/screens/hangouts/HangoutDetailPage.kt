package co.gapfinder.mobile.screens.hangouts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.AttendeeRepository
import co.gapfinder.mobile.data.HangoutRepository
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.HangoutAttendee
import co.gapfinder.mobile.domain.Rsvp
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.Portrait
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/** Estado de la carga (equivalente al FutureBuilder). */
private sealed interface DetailPhase {
    data object Waiting : DetailPhase
    data class Loaded(val hangout: Hangout) : DetailPhase
    data class Broken(val reason: Throwable) : DetailPhase
}

/** Detalle de una mesa abierta con botón para unirse (antes OpenTableDetailScreen). */
@Composable
fun HangoutDetailPage(hangoutId: Int) {
    var phase by remember { mutableStateOf<DetailPhase>(DetailPhase.Waiting) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var viewerId by remember { mutableStateOf<Int?>(null) }
    var enrolled by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Cada cambio de reloadTick equivale a asignar un Future nuevo
    LaunchedEffect(reloadTick) {
        phase = DetailPhase.Waiting
        phase = try {
            val me = SessionVault.memberId()
            viewerId = me
            val found = HangoutRepository.byId(hangoutId)
            if (me != null) {
                // Se verifica con el servicio de participantes si ya está dentro;
                // quien salió (OUT) sigue registrado pero puede volver a unirse
                enrolled = AttendeeRepository.lookup(hangoutId, me)?.rsvp == Rsvp.Joined
            }
            DetailPhase.Loaded(found)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DetailPhase.Broken(e)
        }
    }

    fun enroll() {
        val me = viewerId ?: return
        scope.launch {
            joining = true
            try {
                HangoutRepository.join(hangoutId, me)
                Toaster.show("Successfully joined the table!", background = Palette.Teal)
                reloadTick++ // recarga para actualizar el botón
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.toString().contains("409")) {
                    Toaster.show("You are already a member of this table!", background = Palette.Sky)
                    reloadTick++ // fuerza recarga para bloquear el botón
                } else {
                    Toaster.show("Error joining: ${e.asDartString()}")
                }
            } finally {
                joining = false
            }
        }
    }

    when (val current = phase) {
        DetailPhase.Waiting -> {
            Box(Modifier.fillMaxSize().background(BareScaffold), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(36.dp))
            }
        }

        is DetailPhase.Broken -> {
            Column(Modifier.fillMaxSize().background(BareScaffold)) {
                InkHeader(title = "Error", showBack = true)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Error: ${current.reason.asDartString()}",
                        style = TextStyle(fontSize = 14.sp, letterSpacing = 0.25.sp, lineHeight = 1.43.em, color = SurfaceText),
                    )
                }
            }
        }

        is DetailPhase.Loaded -> {
            val table = current.hangout
            val isHost = table.host?.id == viewerId
            val inside = isHost || enrolled
            val minutesLeft = Duration.between(LocalDateTime.now(), table.endsAt).toMinutes()

            Column(Modifier.fillMaxSize().background(Palette.Fog)) {
                InkHeader(title = "Open Table Detail", showBack = true)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    HostCard(table)

                    Spacer(Modifier.height(28.dp))
                    SectionCaption("ACTIVITY INFO")
                    Spacer(Modifier.height(12.dp))
                    PastimeCard(table)

                    Spacer(Modifier.height(28.dp))
                    SectionCaption("LOGISTICS")
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        FactTile(Glyphs.locationOnRounded, "Location", table.spot?.name ?: "Unknown")
                        Spacer(Modifier.width(12.dp))
                        FactTile(Glyphs.accessTimeFilledRounded, "Ends in", "$minutesLeft min")
                    }

                    Spacer(Modifier.height(28.dp))
                    SectionCaption("PROPOSAL")
                    Spacer(Modifier.height(10.dp))
                    val proposalShape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White, proposalShape)
                            .border(1.5.dp, Palette.Ink.fade(0.08f), proposalShape)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = table.description,
                            style = Typo.paragraph().copy(
                                fontSize = 14.sp,
                                color = Palette.Ink.fade(0.7f),
                                lineHeight = 1.5.em,
                            ),
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                    SectionCaption("PARTICIPANTS (${table.attendees.size})")
                    Spacer(Modifier.height(12.dp))
                    AttendeeStrip(table.attendees)
                }

                // Zona del botón de acción
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (joining) {
                        CircularProgressIndicator(Modifier.size(36.dp))
                    } else {
                        PillButton(
                            label = if (inside) "Already a member" else "Join Table",
                            onClick = if (inside) null else ({ enroll() }),
                            color = if (inside) LightGrey else Palette.Sky,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = Typo.label(FontWeight.ExtraBold).copy(
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            color = Palette.Ink.fade(0.4f),
        ),
    )
}

@Composable
private fun HostCard(table: Hangout) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.03f), spotColor = Palette.Ink.fade(0.03f))
            .background(Color.White, shape)
            .border(1.dp, Palette.Ink.fade(0.08f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Portrait(url = table.host?.avatarUrl, size = 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Created by",
                style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.4f)),
            )
            Text(
                text = table.host?.name ?: "Unknown User",
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 18.sp, color = Palette.Ink),
            )
        }
    }
}

@Composable
private fun PastimeCard(table: Hangout) {
    val pastime = table.pastime ?: return
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, shape)
            .border(1.5.dp, Palette.Teal.fade(0.15f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(Palette.Teal.fade(0.1f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Icon(Glyphs.rocketLaunchRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = pastime.title,
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 16.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${pastime.durationMinutes} min · ${pastime.energy.wire.lowercase()} effort",
                style = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.Ink.fade(0.5f)),
            )
            pastime.hobby?.let { hobby ->
                Spacer(Modifier.height(8.dp))
                val chip = RoundedCornerShape(20.dp)
                Text(
                    text = hobby.name,
                    style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 11.sp, color = Palette.Teal),
                    modifier = Modifier
                        .background(Palette.Teal.fade(0.1f), chip)
                        .border(1.dp, Palette.Teal.fade(0.2f), chip)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.FactTile(icon: ImageVector, label: String, value: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .weight(1f)
            .background(Color.White, shape)
            .border(1.dp, Palette.Ink.fade(0.06f), shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Palette.Coral, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                style = Typo.label(FontWeight.Bold).copy(fontSize = 9.sp, color = Palette.Ink.fade(0.35f)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 13.sp, color = Palette.Ink),
        )
    }
}

/** Avatares superpuestos (Wrap con spacing -12) con tooltip del nombre. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AttendeeStrip(people: List<HangoutAttendee>) {
    if (people.isEmpty()) {
        Text(
            text = "Be the first to join!",
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
        )
        return
    }

    FlowRow {
        people.forEach { person ->
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip(
                        shape = RoundedCornerShape(4.dp),
                        containerColor = Color(0xE6616161),
                        contentColor = Color.White,
                    ) {
                        Text(person.member?.name ?: "Participant", fontSize = 14.sp)
                    }
                },
                state = rememberTooltipState(),
                // Cada avatar ocupa 12dp menos de ancho para superponerse
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val overlap = 12.dp.roundToPx()
                    layout((placeable.width - overlap).coerceAtLeast(0), placeable.height) {
                        placeable.place(0, 0)
                    }
                },
            ) {
                Portrait(
                    url = person.member?.avatarUrl,
                    size = 34.dp,
                    border = BorderStroke(2.dp, Palette.Sky),
                )
            }
        }
    }
}
