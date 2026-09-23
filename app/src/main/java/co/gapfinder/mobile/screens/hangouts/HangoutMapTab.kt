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
import co.gapfinder.mobile.data.MemberRepository
import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.Hangout
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
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

private const val TAG = "HangoutMapTab"
private const val GPS_REQUIRED = "Location is required to discover tables nearby."

/** Pestaña "Open Tables": mapa del campus + mesas descubribles (antes MapScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangoutMapTab() {
    var nearbySpot by remember { mutableStateOf<CampusSpot?>(null) }
    var listings by remember { mutableStateOf<List<Hangout>>(emptyList()) }
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

            // 0. GPS encendido y con permiso
            val gpsReady = GeoProbe.isReady()
            Log.d(TAG, "DEBUG: MapScreen hasLocation check: $gpsReady")
            if (!gpsReady) {
                notice = GPS_REQUIRED
                fetching = false
                return
            }

            // 1. Perfil (edificio actual)
            try {
                nearbySpot = MemberRepository.byId(me).currentSpot
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Note: Profile load failed: $e")
            }

            // 2. Mesas
            listings = HangoutRepository.discoverFor(me)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Error loading Discoverable Open Tables: $e")
            notice = if (e.toString().contains("409")) {
                "YOU HAVE AN ACTIVE MATCH OR TABLE.\nComplete it to discover new ones!"
            } else {
                e.plainText()
            }
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
                            onRetry = { scope.launch { refreshBoard() } },
                            onEnableGps = {
                                scope.launch {
                                    StackNavigator.goAndWait(Destination.LocationGate())
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
    onRetry: () -> Unit,
    onEnableGps: () -> Unit,
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
        listings.forEach { item -> DiscoverCard(item) }
    }
}

@Composable
private fun DiscoverCard(item: Hangout) {
    val minutesLeft = Duration.between(LocalDateTime.now(), item.endsAt).toMinutes()
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.04f), spotColor = Palette.Ink.fade(0.04f))
            .background(Color.White, shape)
            .border(1.5.dp, Palette.Ink.fade(0.07f), shape)
    ) {
        // Cabecera: anfitrión, lugar, actividad y tiempo restante
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
                    item.pastime?.let { PastimeTag(it.title) }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Glyphs.accessTimeRounded,
                        contentDescription = null,
                        tint = Palette.Ink.fade(0.35f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${minutesLeft}m left",
                        style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
                    )
                }
            }
        }

        BlurbCaption()
        BlurbBox(item.description, Palette.Sky)

        // Pie: interés + botón Join
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.pastime?.hobby?.let { hobby ->
                Row(
                    Modifier
                        .background(Palette.Teal.fade(0.12f), RoundedCornerShape(20.dp))
                        .border(1.dp, Palette.Teal.fade(0.25f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Glyphs.starRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = hobby.name,
                        style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 11.sp, color = Palette.Teal),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PillAction(
                label = "Join",
                fill = Palette.Sky,
                textStyle = Typo.paragraph(FontWeight.ExtraBold).copy(fontSize = 12.sp),
                horizontal = 18.dp,
                vertical = 7.dp,
                onTap = { StackNavigator.go(Destination.HangoutDetail(item.id)) },
            )
        }
    }
}
