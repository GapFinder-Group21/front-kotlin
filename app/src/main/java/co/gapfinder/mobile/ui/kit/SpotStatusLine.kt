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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.GeoProbe
import co.gapfinder.mobile.data.MemberRepository
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Línea "Current building: X" que se actualiza cada 5 min y cuando cambia el GPS
 * (antes CurrentLocationBanner).
 */
@Composable
fun SpotStatusLine(memberId: Int) {
    var spotName by remember { mutableStateOf<String?>(null) }
    var firstLoad by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var gpsOk by remember { mutableStateOf(true) }
    var resolved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        refreshing = true
        if (!GeoProbe.isReady()) {
            gpsOk = false; resolved = false; spotName = null; firstLoad = false; refreshing = false
            return
        }
        try {
            val fix = GeoProbe.currentFix()
            Log.d("SpotStatusLine", "📍 LOCATION DETECTED: Lat: ${fix.latitude}, Long: ${fix.longitude}")
            val me = MemberRepository.relocate(memberId, fix.latitude, fix.longitude)
            gpsOk = true; resolved = true; spotName = me.currentSpot?.name
        } catch (e: Exception) {
            Log.d("SpotStatusLine", "Error updating location in banner: $e")
            gpsOk = false; resolved = false
        }
        firstLoad = false
        refreshing = false
    }

    LaunchedEffect(memberId) {
        while (true) {
            refresh()
            delay(5 * 60 * 1000L)
        }
    }
    LaunchedEffect(Unit) {
        GeoProbe.gpsChanges().collect { scope.launch { refresh() } }
    }

    var text: String
    var icon = Glyphs.locationOnRounded
    var tint: Color = Palette.White.fade(0.5f)
    when {
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
}
