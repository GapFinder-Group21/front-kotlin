package co.gapfinder.mobile.ui.kit

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.GeoProbe
import co.gapfinder.mobile.data.NearbyRepository
import co.gapfinder.mobile.data.SpotRepository
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.rememberOnline
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SpotStatusLine"
private const val REFRESH_EVERY_MS = 2 * 60 * 1000L

/** Aviso pendiente de mostrar: edificio al que se acaba de entrar y los amigos que están ahí. */
private data class NearbyNotice(val spotName: String, val friends: List<Member>)

/**
 * Línea "Current building: X" que se actualiza cada 2 min y cuando cambia el GPS
 * (antes CurrentLocationBanner). Al entrar a un edificio donde hay amigos, vibra y los muestra.
 */
@Composable
fun SpotStatusLine(memberId: Int) {
    var spotName by remember { mutableStateOf<String?>(null) }
    // Último edificio resuelto; se conserva aunque el GPS se apague, para no repetir el aviso
    var lastSpotName by remember { mutableStateOf<String?>(null) }
    var firstLoad by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var gpsOk by remember { mutableStateOf(true) }
    var resolved by remember { mutableStateOf(false) }
    val online by rememberOnline()
    // true si algún refresco se saltó por falta de red: al volver la red se hace uno enseguida
    var paused by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<NearbyNotice?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    suspend fun refresh() {
        // Sin red se pausa la detección: no se manda la ubicación ni se buscan amigos
        if (!Connectivity.isOnline()) {
            paused = true
            refreshing = false
            return
        }
        paused = false
        refreshing = true
        if (!GeoProbe.isReady()) {
            gpsOk = false; resolved = false; spotName = null; firstLoad = false; refreshing = false
            return
        }
        try {
            val fix = GeoProbe.currentFix()
            Log.d(TAG, "📍 LOCATION DETECTED: Lat: ${fix.latitude}, Long: ${fix.longitude}")

            // Guarda la ubicación en el backend y trae los amigos que están en el mismo edificio
            val friends = NearbyRepository.moveTo(memberId, fix.latitude, fix.longitude)
            val nowIn = SpotRepository.locate(fix.latitude, fix.longitude)?.name
            Log.d(TAG, "Located building: ${nowIn ?: "Outside campus"} (${friends.size} friends nearby)")

            // Solo avisa al cambiar de edificio, no en cada refresco, y no si la red se cayó
            // mientras llegaba la respuesta (sería un aviso viejo)
            if (nowIn != lastSpotName && nowIn != null && friends.isNotEmpty() && Connectivity.isOnline()) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                notice = NearbyNotice(nowIn, friends)
            }
            lastSpotName = nowIn
            gpsOk = true; resolved = true; spotName = nowIn
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Error updating location in banner: $e")
            gpsOk = false; resolved = false
        }
        firstLoad = false
        refreshing = false
    }

    LaunchedEffect(memberId) {
        while (true) {
            refresh()
            delay(REFRESH_EVERY_MS)
        }
    }
    LaunchedEffect(Unit) {
        GeoProbe.gpsChanges().collect { scope.launch { refresh() } }
    }
    // Al perder la red se descarta el aviso pendiente; al volver se reanuda la detección enseguida
    // (solo si se saltó algún refresco, para no mandar dos ubicaciones seguidas al abrir la pantalla)
    LaunchedEffect(online) {
        if (!online) notice = null else if (paused) refresh()
    }

    var text: String
    var icon = Glyphs.locationOnRounded
    var tint: Color = Palette.White.fade(0.5f)
    when {
        // Sin red no se muestra el último edificio: podría ya no ser cierto
        !online -> {
            text = "Offline · building detection paused"
            icon = Glyphs.cloudOff
        }
        firstLoad -> text = "Locating..."
        !gpsOk -> {
            text = "Location disabled"
            icon = Glyphs.locationOffRounded
            tint = Palette.Coral.fade(0.7f)
        }
        resolved -> if (spotName != null) {
            text = "Current building: $spotName"
            tint = Palette.Teal.fade(0.8f)
        } else {
            text = "Outside campus"
        }
        else -> text = "Location unavailable"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Typo.label().copy(fontSize = 13.sp, color = Palette.White.fade(0.5f)))
        if (refreshing && !firstLoad) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 2.dp,
                color = Color(0x3DFFFFFF),
            )
        }
    }

    notice?.let { shown ->
        NearbyFriendsDialog(spotName = shown.spotName, friends = shown.friends, onDismiss = { notice = null })
    }
}
