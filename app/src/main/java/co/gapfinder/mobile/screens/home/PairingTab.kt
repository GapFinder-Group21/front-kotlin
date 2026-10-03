package co.gapfinder.mobile.screens.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.PairingRepository
import co.gapfinder.mobile.data.WindowRepository
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Pairing
import co.gapfinder.mobile.domain.PairingFocus
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.FocusSelector
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.PulseMatchButton
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime

private const val LOG_TAG = "PairingTab"

private val SHORT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Pestaña principal de Match: GAP actual, match activo, botón y modo (antes MatchMainScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingTab(isActive: Boolean) {
    var focus by remember { mutableStateOf(PairingFocus.Career) }
    var cycle by remember { mutableIntStateOf(0) }
    var fetching by remember { mutableStateOf(true) }
    var openWindow by remember { mutableStateOf<FreeWindow?>(null) }
    var livePairing by remember { mutableStateOf<Pairing?>(null) }
    var myId by remember { mutableStateOf<Int?>(null) }
    var checkedAt by remember { mutableStateOf<LocalDateTime?>(null) }

    // GAP que está corriendo ahora mismo, entre los de la semana del usuario
    suspend fun probeWindow(): FreeWindow? {
        val me = SessionVault.memberId() ?: return null
        myId = me
        val now = Chrono.now()
        checkedAt = now
        return try {
            WindowRepository.weekOf(me).firstOrNull { it.isLiveAt(now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(LOG_TAG, "Advertencia: No se pudieron cargar los GAPs en Match: $e")
            null
        }
    }

    // Match aceptado en curso: mientras exista se bloquea el botón
    suspend fun probePairing(): Pairing? {
        val me = SessionVault.memberId() ?: return null
        return try {
            PairingRepository.ongoingFor(me).also { found ->
                Log.d(
                    LOG_TAG,
                    if (found != null) "🔒 Active match found! ID=${found.id} blocking match button."
                    else "🔓 No active ACCEPTED match found for userId=$me.",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(LOG_TAG, "❌ Error loading active match: $e")
            null
        }
    }

    LaunchedEffect(cycle) {
        fetching = true
        coroutineScope {
            val windowJob = async { probeWindow() }
            val pairingJob = async { probePairing() }
            openWindow = windowJob.await()
            livePairing = pairingJob.await()
        }
        fetching = false
    }

    val reload: () -> Unit = {
        fetching = true
        cycle++
    }

    OnTabWake(isActive) { reload() }

    val window = if (fetching) null else openWindow
    val pairing = if (fetching) null else livePairing
    val ready = window != null && pairing == null

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
    ) {
        Text(
            text = "MATCH",
            style = Typo.heading(FontWeight.Black).copy(
                fontSize = 20.sp,
                color = Palette.White,
                letterSpacing = 1.5.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Ink)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )

        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = reload,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    fetching -> CircularProgressIndicator(
                        Modifier
                            .padding(vertical = 20.dp)
                            .size(36.dp)
                    )
                    window != null -> OpenWindowCard(window)
                    else -> NoWindowCard(checkedAt)
                }

                if (pairing != null) LivePairingBanner()

                PulseMatchButton(
                    onTap = {
                        val me = myId
                        if (me != null) StackNavigator.go(Destination.PairingSearch(me, focus))
                    },
                    enabled = ready,
                )

                Spacer(Modifier.height(28.dp))

                FocusSelector(selected = focus, onChange = { focus = it })
            }
        }
    }
}

private fun clockOf(moment: LocalDateTime): String = twelveHourClock(moment.hour, moment.minute)

/** 90 → "1h 30m free", 60 → "1h free", 45 → "45m free". */
private fun freeTimeText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m free"
        h > 0 -> "${h}h free"
        else -> "${m}m free"
    }
}

/** "Mon, Jan 5 - 9:07" */
private fun stampText(moment: LocalDateTime): String {
    val day = SHORT_DAYS[moment.dayOfWeek.value - 1]
    val month = SHORT_MONTHS[moment.monthValue - 1]
    val time = "${moment.hour}:${moment.minute.toString().padStart(2, '0')}"
    return "$day, $month ${moment.dayOfMonth} - $time"
}

@Composable
private fun OpenWindowCard(window: FreeWindow) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .padding(bottom = 32.dp)
            .fillMaxWidth()
            .background(Palette.White, shape)
            .border(1.5.dp, Palette.Teal.fade(0.3f), shape)
            // padding de Flutter + ancho del borde
            .padding(horizontal = 19.5.dp, vertical = 17.5.dp)
    ) {
        // Barra lateral que ocupa todo el alto del contenido
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Palette.Teal)
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = "CURRENT GAP",
                style = Typo.label(FontWeight.Bold).copy(
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = Palette.Teal,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${clockOf(window.startsAt)} – ${clockOf(window.endsAt)}",
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 18.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.accessTime, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = freeTimeText(window.durationMinutes),
                    style = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.Ink.fade(0.5f)),
                )
            }
        }
    }
}

@Composable
private fun NoWindowCard(checkedAt: LocalDateTime?) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .padding(bottom = 32.dp)
            .fillMaxWidth()
            .background(Palette.White, shape)
            .border(1.dp, Palette.Ink.fade(0.1f), shape)
            .padding(horizontal = 17.dp, vertical = 21.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "You have no active GAP right now.",
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.7f)),
        )
        if (checkedAt != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Last check: ${stampText(checkedAt)}",
                textAlign = TextAlign.Center,
                style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.4f)),
            )
        }
    }
}

@Composable
private fun LivePairingBanner() {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .padding(bottom = 24.dp)
            .fillMaxWidth()
            .background(Palette.Teal.fade(0.1f), shape)
            .border(1.dp, Palette.Teal.fade(0.2f), shape)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.boltRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "YOU HAVE AN ACTIVE MATCH HAPPENING NOW!",
            style = Typo.paragraph(FontWeight.ExtraBold).copy(
                fontSize = 12.sp,
                color = Palette.Teal,
                letterSpacing = 1.sp,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}
