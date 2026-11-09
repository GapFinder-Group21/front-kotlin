package co.gapfinder.mobile.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.MobilityRepository
import co.gapfinder.mobile.domain.MobilityPreference
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Rango del control de distancia, en metros
private const val MIN_METERS = 100f
private const val MAX_METERS = 1500f
private const val STEP_METERS = 100f
private const val DEFAULT_METERS = 500f

// Ritmo de caminata promedio para mostrar los minutos
private const val METERS_PER_MINUTE = 80

/**
 * Preferencia de movilidad (BQ 14): distancia máxima a pie y solo lugares en primer piso.
 * Es privada y opcional. Se abre como ajuste desde Recomendaciones y desde el mapa de Open Tables.
 */
@Composable
fun MobilityPreferencePage() {
    val scope = rememberCoroutineScope()
    var memberId by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var limitDistance by remember { mutableStateOf(false) }
    var meters by remember { mutableFloatStateOf(DEFAULT_METERS) }
    var groundFloorOnly by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun show(preference: MobilityPreference) {
        limitDistance = preference.maxWalkMeters != null
        preference.maxWalkMeters?.let { meters = it.toFloat().coerceIn(MIN_METERS, MAX_METERS) }
        groundFloorOnly = preference.groundFloorOnly
    }

    LaunchedEffect(Unit) {
        val me = SessionVault.memberId() ?: return@LaunchedEffect
        memberId = me
        // Primero lo del teléfono; después lo del backend, por si la eligió en otro dispositivo
        show(MobilityRepository.current(me))
        loaded = true
        show(MobilityRepository.refresh(me))
    }

    fun save(preference: MobilityPreference) {
        val me = memberId ?: return
        saving = true
        scope.launch {
            val synced = MobilityRepository.save(me, preference)
            Toaster.show(
                if (synced) "Mobility preference saved" else "Saved on your phone. It syncs when the server is reachable.",
                background = Palette.Teal,
            )
            saving = false
            StackNavigator.back()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
    ) {
        InkHeader(title = "Mobility", subtitle = "Optional · only you can see it", showBack = true)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Text(
                text = "Tell us how far you'd like to walk and if you need places on the first floor. " +
                    "We only use it to filter the Open Tables we suggest to you.",
                style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.6f)),
            )
            Spacer(Modifier.height(24.dp))

            PreferenceCard(
                glyph = Glyphs.directionsWalkRounded,
                title = "Limit walking distance",
                checked = limitDistance,
                onCheck = { limitDistance = it },
            ) {
                if (limitDistance) {
                    Spacer(Modifier.height(12.dp))
                    val shown = meters.roundToInt()
                    Text(
                        text = "Up to $shown m · about ${(shown / METERS_PER_MINUTE).coerceAtLeast(1)} min walking",
                        style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
                    )
                    Slider(
                        value = meters,
                        onValueChange = { meters = it },
                        valueRange = MIN_METERS..MAX_METERS,
                        // steps = posiciones intermedias entre los extremos
                        steps = ((MAX_METERS - MIN_METERS) / STEP_METERS).toInt() - 1,
                        colors = SliderDefaults.colors(thumbColor = Palette.Teal, activeTrackColor = Palette.Teal),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            PreferenceCard(
                glyph = Glyphs.accessibleRounded,
                title = "Only first-floor places",
                checked = groundFloorOnly,
                onCheck = { groundFloorOnly = it },
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Hides tables on upper floors.",
                    style = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.Ink.fade(0.5f)),
                )
            }

            Spacer(Modifier.height(32.dp))

            if (saving || !loaded) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(36.dp))
            } else {
                PillButton(
                    label = "Save",
                    color = Palette.Teal,
                    onClick = {
                        save(
                            MobilityPreference(
                                maxWalkMeters = if (limitDistance) meters.roundToInt() else null,
                                groundFloorOnly = groundFloorOnly,
                            )
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                PillButton(
                    label = "I don't need this",
                    outline = true,
                    color = Palette.Ink,
                    textColor = Palette.Ink,
                    onClick = { save(MobilityPreference.None) },
                )
            }
        }
    }
}

/** Tarjeta blanca con ícono, título y switch; el contenido extra va debajo. */
@Composable
private fun PreferenceCard(
    glyph: ImageVector,
    title: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.White, shape)
            .border(1.dp, Palette.Ink.fade(0.08f), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(glyph, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheck,
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Teal),
            )
        }
        content()
    }
}
