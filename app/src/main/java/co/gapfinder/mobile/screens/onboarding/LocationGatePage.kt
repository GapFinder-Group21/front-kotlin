package co.gapfinder.mobile.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.GeoAccess
import co.gapfinder.mobile.data.GeoProbe
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.launch

/** Pide permiso de ubicación. */
@Composable
fun LocationGatePage(fromOnboarding: Boolean) {
    val scope = rememberCoroutineScope()

    // Siguiente paso según de dónde venga
    fun proceed() {
        if (fromOnboarding) {
            StackNavigator.swap(Destination.HobbyPicker)
        } else if (StackNavigator.canGoBack) {
            StackNavigator.back()
        } else {
            StackNavigator.swap(Destination.Home)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ícono circular
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Palette.Teal, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Glyphs.locationOnRounded,
                contentDescription = null,
                tint = Palette.White,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(20.dp))

        Text(
            text = "Enable Location",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 22.sp, color = Palette.Ink),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        Text(
            text = "GAP FINDER uses your location to identify your campus area and show Open Tables.",
            style = Typo.paragraph().copy(
                fontSize = 15.sp,
                color = Palette.Ink.fade(0.6f),
                lineHeight = 1.55.em,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.height(8.dp))

        PillButton(
            label = "Allow Location",
            color = Palette.Teal,
            onClick = {
                scope.launch {
                    when (GeoProbe.askPermission()) {
                        GeoAccess.Always, GeoAccess.WhileInUse -> proceed()
                        GeoAccess.DeniedForever -> {
                            Toaster.show("Permission permanently denied. Please enable it in settings.")
                            GeoProbe.openAppSettings()
                        }
                        GeoAccess.Denied -> Unit
                    }
                }
            },
        )

        Spacer(Modifier.height(12.dp))

        // TextButton: área táctil de 48 con botón visible de 40
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .inkTap(RoundedCornerShape(50)) {
                        Toaster.show(
                            "You can use the app, but some features won't be available without location.",
                            background = Palette.Ink.fade(0.8f),
                            style = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.White),
                            durationMs = 4_000,
                        )
                        proceed()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Skip for now",
                    style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
                )
            }
        }
    }
}
