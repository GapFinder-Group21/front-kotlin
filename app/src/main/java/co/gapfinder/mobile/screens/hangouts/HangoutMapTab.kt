package co.gapfinder.mobile.screens.hangouts

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.GeoProbe
import co.gapfinder.mobile.data.HangoutRepository
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.HangoutState
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.CampusBoard
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.Portrait
import co.gapfinder.mobile.ui.kit.SpotStatusLine
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val TAG = "HangoutMapTab"
private const val GPS_REQUIRED = "Location is required to discover tables nearby."

/** Pestaña "Open Tables": mapa del campus + mesas abiertas (antes MapScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangoutMapTab() {
    var listings by remember { mutableStateOf<List<Hangout>>(emptyList()) }
    // Mesas en las que el usuario ya participa
    var joinedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var fetching by remember { mutableStateOf(true) }
    var viewerId by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshBoard() {
        fetching = true
        notice = null
        try {
            val me = SessionVault.memberId()
            viewerId = me
            if (me == null) return

            // GPS encendido y con permiso
            if (!GeoProbe.isReady()) {
                notice = GPS_REQUIRED
                return
            }

            // Solo las mesas abiertas que todavía no terminan
            val now = LocalDateTime.now()
            val open = HangoutRepository.all().filter { it.state == HangoutState.Open && it.endsAt.isAfter(now) }
            listings = open

            // Se consulta en paralelo en cuáles ya está el usuario
            val checks = coroutineScope { open.map { table -> async { attendsQuietly(table.id, me) } }.awaitAll() }
            joinedIds = open.filterIndexed { index, _ -> checks[index] }.map { it.id }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Error loading Open Tables: $e")
            notice = e.plainText()
        } finally {
            fetching = false
        }
    }

    LaunchedEffect(Unit) { refreshBoard() }
    // Recarga cada vez que cambia el estado del GPS
    LaunchedEffect(Unit) {
        GeoProbe.gpsChanges().collect { status ->
            Log.d(TAG, "DEBUG: GPS Status changed to: $status")
            scope.launch { refreshBoard() }
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Fog)) {
        BoardHeader(viewerId)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (fetching) {
                CircularProgressIndicator(Modifier.size(36.dp).align(Alignment.Center))
            } else {
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { scope.launch { refreshBoard() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MapBlock()
                        ListingBlock(
                            notice = notice,
                            listings = listings,
                            viewerId = viewerId,
                            joinedIds = joinedIds,
                            onRetry = { scope.launch { refreshBoard() } },
                            onEnableGps = {
                                scope.launch {
                                    StackNavigator.goAndWait(Destination.LocationGate())
                                    refreshBoard()
                                }
                            },
                            onOpen = { table ->
                                // Al volver del detalle se recarga, por si el usuario se unió
                                scope.launch {
                                    StackNavigator.goAndWait(Destination.HangoutDetail(table.id))
                                    refreshBoard()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Como [attends], pero un fallo al consultar cuenta como "no participa". */
private suspend fun attendsQuietly(hangoutId: Int, memberId: Int): Boolean = try {
    attends(hangoutId, memberId)
} catch (e: CancellationException) {
    throw e
} catch (ignored: Exception) {
    false
}

@Composable
private fun BoardHeader(viewerId: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .padding(start = 20.dp, top = 56.dp, end = 20.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                text = "Open Table",
                style = Typo.heading(FontWeight.Black).copy(
                    fontSize = 20.sp,
                    color = Color.White,
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            if (viewerId != null) {
                SpotStatusLine(viewerId)
            } else {
                Text(
                    text = "Locating...",
                    style = Typo.label().copy(fontSize = 13.sp, color = Color.White.fade(0.5f)),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val chipText = Typo.label(FontWeight.Bold).copy(fontSize = 12.sp)
            PillAction(
                label = "Mine",
                fill = Palette.Sky,
                textStyle = chipText,
                horizontal = 14.dp,
                vertical = 8.dp,
                onTap = { StackNavigator.go(Destination.MyHangouts) },
            )
            Spacer(Modifier.width(8.dp))
            PillAction(
                label = "New",
                fill = Palette.Teal,
                textStyle = chipText,
                horizontal = 14.dp,
                vertical = 8.dp,
                onTap = { StackNavigator.go(Destination.HangoutComposer) },
                leading = Glyphs.addRounded,
            )
        }
    }
}

@Composable
private fun MapBlock() {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        CampusBoard(onSpotTap = { /* futuro: cambiar de edificio */ })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            LegendDot(Color(0xFFE91E63), "You are here")
            Spacer(Modifier.width(16.dp))
            LegendDot(Palette.Sky, "Open Tables")
        }
    }
}

@Composable
private fun LegendDot(tone: Color, caption: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(tone, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = caption,
            style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 11.sp, color = Palette.Ink.fade(0.6f)),
        )
    }
}

@Composable
private fun ListingBlock(
    notice: String?,
    listings: List<Hangout>,
    viewerId: Int?,
    joinedIds: Set<Int>,
    onRetry: () -> Unit,
    onEnableGps: () -> Unit,
    onOpen: (Hangout) -> Unit,
) {
    if (notice != null) {
        val gpsIssue = notice.contains("Location is required")
        Column(
            Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (gpsIssue) Glyphs.locationOffRounded else Glyphs.infoOutlineRounded,
                contentDescription = null,
                tint = Palette.Coral,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = notice,
                textAlign = TextAlign.Center,
                style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 14.sp, color = Palette.Coral),
            )
            Spacer(Modifier.height(24.dp))
            PillButton(
                label = if (gpsIssue) "Enable Location" else "Retry",
                onClick = if (gpsIssue) onEnableGps else onRetry,
                compact = true,
            )
        }
        return
    }

    if (listings.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Glyphs.coffeeMakerRounded,
                contentDescription = null,
                tint = Palette.Ink.fade(0.1f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No active tables available to discover right now.",
                textAlign = TextAlign.Center,
                style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        listings.forEach { item ->
            // El creador también cuenta como dentro de la mesa
            val alreadyIn = item.host?.id == viewerId || item.id in joinedIds
            DiscoverCard(item, alreadyIn = alreadyIn, onOpen = { onOpen(item) })
        }
    }
}

@Composable
private fun DiscoverCard(item: Hangout, alreadyIn: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.04f), spotColor = Palette.Ink.fade(0.04f))
            .background(Color.White, shape)
            .border(1.5.dp, Palette.Ink.fade(0.07f), shape)
    ) {
        // Cabecera: anfitrión, lugar, título y tiempo restante
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Portrait(url = item.host?.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.host?.name ?: "User",
                    style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Glyphs.locationOnRounded,
                        contentDescription = null,
                        tint = Palette.Ink.fade(0.35f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.spot?.name ?: "Unknown location",
                        style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    PastimeTag(item.title)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Glyphs.accessTimeRounded,
                        contentDescription = null,
                        tint = Palette.Ink.fade(0.35f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${item.minutesLeft()}m left",
                        style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
                    )
                }
            }
        }

        BlurbCaption()
        BlurbBox(item.description, Palette.Sky)

        // Pie: cupo + botón Join (gris si ya está dentro)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CapacityNote(item.maxParticipants)
            Spacer(Modifier.weight(1f))
            PillAction(
                label = if (alreadyIn) "Joined" else "Join",
                fill = if (alreadyIn) PaleGrey else Palette.Sky,
                textStyle = Typo.paragraph(FontWeight.ExtraBold).copy(fontSize = 12.sp),
                horizontal = 18.dp,
                vertical = 7.dp,
                onTap = onOpen,
                textColor = if (alreadyIn) Palette.Ink.fade(0.5f) else Palette.White,
            )
        }
    }
}
